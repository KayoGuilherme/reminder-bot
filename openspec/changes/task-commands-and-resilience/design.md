# Design

## Context

The bot is a hexagonal Spring Boot 4 / Java 21 app (`application` services and ports, `infrastructure` adapters, `domain`). Relevant state today, observed in the code:

- Commands are routed in `IncomingCommandService`; `/tasks` currently falls into the "not supported yet" reply. Button callbacks use `task:ok:`/`task:no:` (draft Confirm/Cancel) and `task:done:`/`task:snooze:` (`ReminderActionService`, conditional updates in `JpaTaskRepository`).
- `TaskStatus` already has `CANCELED` and the `tasks` table already allows it; `updated_at` is set by every status change (`markDone`, `markReminded`, `snooze`). No migration is needed.
- `RestClients.create` already sets connect/read timeouts for the STT, LLM and Telegram-download clients, and `SingleRetry` already retries a transient failure once (fixed 500 ms pause, hard-coded). The Telegram send client (`OkHttpTelegramClient(token)`) and the long-polling client created by the starter use library defaults.
- Logs never contain content today (services log ids and failure kinds only), but nothing enforces that, there is no correlation id, and `TelegramUpdateConsumer` is the single entry point of updates while `ReminderScheduler.tick` is the entry point of scheduled work.
- `ReminderScheduler` uses `@Scheduled(fixedDelay)`; the default Spring scheduler does not wait for a running task on shutdown. Compose has `restart: unless-stopped` already but no `stop_grace_period` (Docker default 10 s).
- `voice-to-task` and `telegram-bot-base` are not archived, so their specs are not in `openspec/specs/`; deltas here only target the 4 main specs that exist (see proposal).

## Goals / Non-Goals

**Goals:**
- `/tasks`, Cancel, retention, startup check, correlation ids, graceful shutdown and the README, as specified, with small, local changes that follow the existing ports/adapters layout.
- Keep Done and Cancel race-safe the same way Done and Snooze are: one conditional `UPDATE ... WHERE status = 'PENDING'`.

**Non-Goals:**
- No Flyway migration, no new table, no soft-delete column.
- No pagination buttons for `/tasks` (cap + "N more" only), no editing of tasks, no `/cancel` typed command (buttons only).
- No retry policy beyond "once" for STT/LLM, no circuit breaker, no health indicator for external services.
- No JSON/structured logging, no tracing library; MDC is enough for a single instance.
- No horizontal scaling concerns: the app stays single-instance.

## Decisions

### 1. `/tasks`: one message per task, built by a dedicated use case
`ListTasksUseCase` (inbound port) is implemented by `ListTasksService` in `application/service`. It asks the repository for `findPendingByChat(chatId, limit + 1)` ordered by `deadline_at, id`, sends a task message per row through `MessageSenderPort.sendWithButtons`, and if the `limit + 1`-th row exists, sends "and more pending" using a `countPendingByChat` (cheap, indexed on `(status, deadline_at)`; filter by `chat_id` is fine at this size). `IncomingCommandService.handleText` adds `case "/tasks"`.

*Why one message per task:* the Done and Cancel callbacks edit the message they are on (existing `show()` behavior), so each task needs its own message; a single list message with 2N buttons could not be edited into a per-task confirmation. *Alternative considered:* one message with numbered rows and `✅1 ❌1` buttons — fewer messages but ambiguous after the list shifts, and hits the 100-button / 4096-char limits sooner. *Limit 20* keeps a `/tasks` call within Telegram's ~1 message/s-per-chat guidance when combined with the sender's existing 429 handling; the limit is a constant, not a setting (YAGNI). Sends are sequential and in order so the visual order equals deadline order; a failed send is logged by the sender and does not abort the rest.

### 2. Task line format and urgency marker
Extend `TaskSummaryFormatter` (already owns the `yyyy-MM-dd HH:mm` format in `app.timezone`) with `listItem(Task, Instant now)`:

```
🔴 High · Send the report
Due: 2026-11-22 18:00          (overdue: "⏰ Overdue · Due: …")
```

Markers: 🟢 LOW, 🟡 MEDIUM, 🔴 HIGH, with the localized word next to the emoji so the marker is not colour-only. Texts go in `messages.properties` and `messages_pt_BR.properties` (`tasks.list.*`, `tasks.empty`, `tasks.more`, `task.cancel.*`, button labels). Titles are sent as plain text: `TelegramMessageSender` does not set a parse mode today, so no escaping is needed (verified by a test with `<*_` characters); this must stay true.

### 3. Cancel as a third reminder action
Add `cancel(chatId, messageId, taskId)` to `HandleReminderActionUseCase` (it is already "the buttons on a task message") and `ReminderActionService`, mirroring `done`: ownership check, `PENDING` check, `TaskRepository.markCanceled(id, at)` (`UPDATE ... SET status='CANCELED', updated_at=:at WHERE id=:id AND status='PENDING'`), then `show()` with the confirmation, falling back to a new message. Callback prefix `task:cancel:` (12 chars + 36-char UUID = 48 bytes ≤ 64), distinct from the draft's `task:no:`. `IncomingCommandService.handleCallback` gets one more branch. Reminder scheduling needs no change: candidates are `PENDING` only and `markReminded` is conditional on `PENDING`, so a cancel between "decide" and "record" is already safe.

*Alternative:* reuse `task:no:` for both — rejected, that prefix is bound to in-memory drafts and would conflate two flows.

### 4. Retention job
New `RetentionService` (application) with `int deleteFinishedBefore(Instant cutoff)` backed by `TaskRepository.deleteFinishedBefore(cutoff)` (`DELETE FROM TaskEntity WHERE status IN (DONE, CANCELED) AND updatedAt < :cutoff`, `@Transactional`). A `RetentionScheduler` in `infrastructure/scheduling` runs `@Scheduled(cron = "0 0 3 * * *", zone = "${app.timezone}")` and logs the count at INFO. `SchedulingConfiguration`-style conditional: `@ConditionalOnProperty(app.retention.enabled)`, default true. Cutoff = `clock.instant().minus(days)`; "older than" is strict, so a task exactly at the boundary is kept.

*Why `updated_at`:* it is the time of the last status change for finished tasks (Done/Cancel set it; reminders stop once finished, so nothing else touches it). No schema change. *Why a cron and not `fixedDelay`:* a daily fixed local time is predictable and does not re-run after a restart the same day (a `fixedDelay` would run at every start). Missing the 03:00 window (machine off) just means the next day's run catches up, since the cutoff is relative. `AppProperties.Retention(enabled, days @Min(1))`.

### 5. Timeouts, retry backoff and the long-polling client
- `AppProperties.Telegram` gains `connectTimeout`/`readTimeout` (defaults 5s/30s, `@DurationMin`). `TelegramConfiguration.telegramClient` builds an `OkHttpClient` with those and passes it to `OkHttpTelegramClient(http, token)` (the same constructor the audio downloader already uses). `audioDownloadPort` keeps using the STT timeouts.
- Long polling legitimately holds a request open (`getUpdates` timeout is ~50 s), so it gets a separate client: connect timeout as configured, read timeout fixed above the poll timeout (75 s), so no request can hang forever. The starter's `TelegramBotsLongPollingApplication` takes an `OkHttpClient` supplier; the design is to provide that bean ourselves if the starter's auto-configuration backs off for a user-defined bean. **To verify in task 4.2** — if it does not, the fallback is to document that the library's own default applies and cover only the clients we construct.
- `SingleRetry` takes its pause from `app.resilience.retry-backoff` (new `AppProperties.Resilience`), wired where `new SingleRetry("...")` is built today (`SttConfiguration`, `LlmConfiguration`, `TelegramConfiguration`). Behavior (exactly one retry, transient classification) stays as is; it already satisfies the spec, so the work is configurability and tests that prove the pause and the two-request count. "Backoff" for a single retry is just the pause; exponential growth is meaningless with one retry and is not added.
- "Does not lose the next message" holds because `TelegramUpdateConsumer` catches per update and `CreateTaskFromVoiceService` converts `ExternalServiceException` into a reply. A test makes the FakeHttpServer fail the first STT/LLM call and asserts the second update works end to end. Note the update that failed is *not* retried later (the owner is told to resend); that is intentional, as storing voice notes for replay is out of scope.

### 6. Startup token check
`TelegramStartupCheck` (infrastructure/telegram), an `ApplicationRunner` conditional on `app.telegram.enabled`, runs `client.execute(new GetMe())` on the (timeout-bounded) send client. `TelegramApiRequestException` with code 401/404 → `log.error("Telegram rejected TELEGRAM_BOT_TOKEN ... the bot cannot receive messages")` (no token; the existing `redact` helper idea is reused); any other failure → `log.warn(...)` that the check could not complete; success → `log.info` with the bot username. It never throws, so startup always proceeds. *Alternative:* fail startup on an invalid token — rejected: under `restart: unless-stopped` that becomes a crash loop that hides the message among restarts, and the request says "logs a clear error". The runner runs after the context is ready, so a slow Telegram does not delay Flyway or the health endpoint.

### 7. Correlation id
`CorrelationId` helper in `infrastructure` (not in the domain): `try (var scope = CorrelationId.open("upd"))` puts an 8-char random id under MDC key `cid` and removes it in `close()`. Used by `TelegramUpdateConsumer.consume` (one scope per update, around `process` and its catch block so the error line carries it), by `ReminderScheduler.tick` and by the retention scheduler. Logging pattern in `application.yaml`: `logging.pattern.level: "%5p [%X{cid:--}]"` so lines outside a scope print `-`. Because handling is synchronous on the consumer thread, MDC propagates to the services and `SingleRetry` without extra plumbing. *Alternative:* use the Telegram `update_id` as the id — rejected: it repeats across restarts in logs and does not exist for scheduler runs; the update id is still logged on the error line already.

### 8. Log hygiene is enforced by a test, not only by convention
No new code is needed for "never log content" since services only log ids/kinds. The risk is future regressions, so a test (`LogHygieneTest`) runs a full voice → confirm flow and a failing STT/LLM flow with `LogCapture` on the root logger at DEBUG and asserts no event message or throwable message contains the transcript, task title, API keys or bot token. The existing `TelegramMessageSender.redact` stays. `SingleRetry`'s `describe()` already logs only the status code or exception class.

### 9. Graceful shutdown
Configuration only, no new thread logic:
- `spring.task.scheduling.shutdown.await-termination=true` and `await-termination-period=${app.shutdown.timeout}` make the Spring scheduler stop accepting new runs and wait for the running one. (`fixedDelay` does not start new runs after shutdown begins.)
- `spring.lifecycle.timeout-per-shutdown-phase=${app.shutdown.timeout}` for the lifecycle phases; `server.shutdown=graceful` for the actuator HTTP server.
- Shutdown order matters: the Telegram sender must still work while the last run finishes. Because the scheduler's await happens during the context's `close` before beans such as the Telegram client are destroyed (beans are destroyed after the scheduler bean's `destroy`, which depends on declaration/dependency order), a test with a slow fake sender verifies a run in progress completes its send and records the stage. If ordering proves wrong, the fix is `@DependsOn`/ordering the scheduler to depend on the sender bean so it is destroyed first.
- `docker-compose.yml`: `stop_grace_period: 45s` on `app` (default 30 s + margin), and `.env.example`/README mention `APP_SHUTDOWN_TIMEOUT`.
- `AppProperties.Shutdown(timeout)`, default 30s.

### 10. README rewrite
Restructure into: what it does → usage guide (phrases pt/en, commands and buttons) → configuration reference (one table per area, every variable with default, including the new ones) → run (local, home machine / Raspberry Pi with compose, `restart: unless-stopped`, updates, logs, backups pointer) → develop/CI → Oracle section kept as is. The arm64 note stays accurate: the CI image is already built for both architectures (README says so in the Oracle section but the generic section says amd64 only; the rewrite reconciles them after checking `.github/workflows/docker.yml`). Example phrases are checked against `TaskParserPrompt` so the documented behavior (e.g. "amanhã às 9" ⇒ tomorrow 09:00, no urgency ⇒ Medium assumed) matches what the prompt does.

## Risks / Trade-offs

- [Per-task messages are noisy for a long list] → capped at 20 with a "N more" line; the cap is one constant to change.
- [`/tasks` is not paginated, so tasks beyond 20 are unreachable until earlier ones are finished] → acceptable for a single-owner reminder bot; stated in the spec and README.
- [Retention deletes data permanently] → only finished tasks older than a configurable window (min 1 day), disable switch, count logged; the daily backup script from the deploy docs covers recovery.
- [Using `updated_at` as "finished at"] → true today because nothing updates finished tasks; a test pins it, and a future feature that touches finished rows would need to revisit this.
- [Overriding the long-polling client may not be supported by the starter] → verify in task 4.2; documented fallback, no behavior change for the user.
- [Shutdown ordering could close the Telegram client before the last run sends] → covered by a test with a slow sender; ordering fix identified above.
- [An invalid token is only logged, the app keeps running and polling fails in a loop] → the library's own backoff limits the noise; the single clear ERROR line plus the README troubleshooting entry tell the owner what to fix.
- [Cron at 03:00 in `app.timezone` skips a day around DST changes in zones with 02:00-03:00 gaps] → harmless: cleanup is relative to "now", so the next run deletes the same tasks.

## Migration Plan

No database migration and no breaking configuration change: all new variables are optional with defaults. Deploy as usual (`docker compose pull app && docker compose up -d`); the first stop of the old container still uses the old 10 s grace, the new `stop_grace_period` takes effect once the new compose file is on the machine (the README says to copy it). Rollback is redeploying the previous image tag; old tasks already deleted by retention are not restored (restore from the backup if needed).

## Open Questions

- None blocking. The 20-task cap, the 03:00 cleanup time and the 75 s long-poll read timeout are fixed constants chosen here; they can become settings later without changing the specs' behavior.
