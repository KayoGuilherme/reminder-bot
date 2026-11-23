# Tasks

## 1. Settings

- [x] 1.1 Add `Retention(enabled, days @Min(1))`, `Resilience(retryBackoff)`, `Shutdown(timeout)` and Telegram `connectTimeout`/`readTimeout` to `AppProperties` with the defaults in the app-configuration spec, map them in `application.yaml` (`APP_RETENTION_DAYS`, `APP_RETENTION_ENABLED`, `APP_RETRY_BACKOFF`, `APP_SHUTDOWN_TIMEOUT`, `APP_TELEGRAM_CONNECT_TIMEOUT`, `APP_TELEGRAM_READ_TIMEOUT`) and update `TestProperties`; extend `AppPropertiesTest` for defaults, an override and an invalid value (`retention.days=0`, `retry-backoff=0s`) naming the setting, then `./mvnw -Dtest=AppPropertiesTest test` passes
- [x] 1.2 List the new variables as commented-out optional entries in `.env.example` and verify each variable name matches `application.yaml`

## 2. Repository operations

- [x] 2.1 Add `findPendingByChat(chatId, limit)` (deadline then id order), `countPendingByChat(chatId)`, `markCanceled(id, at)` and `deleteFinishedBefore(cutoff)` to `TaskRepository`, `JpaTaskRepository` and the in-memory `InMemoryTaskRepository` test double
- [x] 2.2 Extend `JpaTaskRepositoryTest` (Testcontainers): ordering with overdue first and equal deadlines, other chats and non-pending excluded, limit honored, `markCanceled` applies once and not on DONE, `deleteFinishedBefore` removes DONE/CANCELED older than the cutoff only, keeps the boundary and never touches PENDING; run `./mvnw -Dtest=JpaTaskRepositoryTest test`

## 3. Cancel action and /tasks

- [x] 3.1 Add `cancel` and the `task:cancel:` prefix to `HandleReminderActionUseCase` and implement it in `ReminderActionService` (ownership, status, conditional update, confirmation via `show()` with fallback, id-only logging); route the prefix in `IncomingCommandService.handleCallback`; add `task.cancel.*` texts in both message files
- [x] 3.2 Extend `ReminderActionServiceTest` for cancel: pending, twice, on a done task, unknown/foreign task (same reply), edit fallback, and `ButtonTest`-style check that the callback data is ≤ 64 bytes; verify with `./mvnw -Dtest=ReminderActionServiceTest test`
- [x] 3.3 Add `ListTasksUseCase` and `ListTasksService`, `TaskSummaryFormatter.listItem` (urgency emoji + localized word, deadline in `app.timezone`, overdue flag), the 20-task cap with the "N more" message, the empty-list message, and the `/tasks` case (including `/tasks@bot`) in `IncomingCommandService`; add `tasks.*` texts in `messages.properties` and `messages_pt_BR.properties`
- [x] 3.4 Write `ListTasksServiceTest` and extend `IncomingCommandServiceTest`: ordering, timezone rendering (21:00Z → 18:00 in `America/Fortaleza`), urgency markers, overdue flag, titles with `<*_` unchanged, empty list, exactly 20 and 25 tasks, other chats excluded, Done and Cancel buttons on every message; `MessagesTest` still passes (en and pt-BR have the same keys); run those tests
- [x] 3.5 Add `TaskCommandsIntegrationTest` (real PostgreSQL, recording sender, real command/list/action/reminder services; used instead of `TelegramFlowTest` because that one mocks the actions): `/tasks` lists seeded tasks, pressing Cancel then Done works from the list, a canceled or done task gets no reminder at the next run; `./mvnw -Dtest=TaskCommandsIntegrationTest test`

## 4. Resilience

- [x] 4.1 Make `SingleRetry`'s pause come from `app.resilience.retry-backoff` (wire it in `SttConfiguration`, `LlmConfiguration`, `TelegramConfiguration`) and extend `SingleRetryTest` to assert exactly two attempts, the configured pause, and no retry on 400/401/403
- [x] 4.2 Build the Telegram send client with the configured connect/read timeouts, and give the long-polling client bounded timeouts (read above the poll timeout) if the starter lets us supply the `OkHttpClient`; if it does not, document the library default in the README instead; verify with a `TelegramMessageSenderTest` case against `FakeHttpServer` that never answers (fails within the timeout)
- [x] 4.3 Add `TelegramStartupCheck` (`ApplicationRunner`, only when Telegram is enabled): 401/404 → ERROR naming `TELEGRAM_BOT_TOKEN` without the token, other failure → WARN, success → INFO with the username, never throws; test the four cases (invalid, valid, unreachable, disabled) with `LogCapture` and `FakeTelegramApi`
- [x] 4.4 Add a recovery test to `VoiceToTaskIntegrationTest` (or `TelegramFlowTest`): the first STT call fails with 503 twice, the owner gets a "try again" reply and no draft/task exists, then the next voice note is transcribed and confirmed; same for an LLM outage; and a batch where the first update throws still handles the second (`TelegramUpdateConsumerTest`)

## 5. Logging

- [x] 5.1 Add `CorrelationId` (MDC key `cid`, 8-char id, closed in try-with-resources), use it in `TelegramUpdateConsumer.consume` (around handling and the error log) and in `ReminderScheduler.tick`, and set `logging.pattern.level` to include `%X{cid:--}`; test in `TelegramUpdateConsumerTest`/`ReminderSchedulerTest` that all lines of one update share an id, two updates differ, the id is cleared afterwards and the error line carries it
- [x] 5.2 Add `LogHygieneTest`: run a successful voice → confirm flow and failing STT/LLM flows with log capture at DEBUG and assert no event message or throwable message contains the transcript, task title, API keys or bot token; fix any leak it finds

## 6. Retention and graceful shutdown

- [x] 6.1 Add `RetentionService` and `RetentionScheduler` (cron `0 0 3 * * *` in `app.timezone`, conditional on `app.retention.enabled`, correlation id, INFO count without content, exceptions caught and logged); tests with `MutableClock`: old DONE and CANCELED deleted, 29-day and boundary tasks kept, PENDING never deleted, configured days honored, disabled → bean absent, failure logged and the next run unaffected; run `./mvnw -Dtest='Retention*Test' test`
- [x] 6.2 Configure graceful shutdown in `application.yaml` (`spring.task.scheduling.shutdown.await-termination`, `await-termination-period` and `spring.lifecycle.timeout-per-shutdown-phase` from `app.shutdown.timeout`, `server.shutdown=graceful`) and add a test where a slow fake sender is mid-run when the context closes: the send completes and its stage is recorded, no new run starts, an idle context closes immediately; reorder bean dependencies if the sender is destroyed first
- [x] 6.3 Add `stop_grace_period: 45s` to the `app` service in `docker-compose.yml` and confirm with `docker compose -f docker-compose.yml config` that it is longer than the default 30 s timeout

## 7. README and wrap-up

- [x] 7.1 Rewrite `README.md` per design decision 10: usage guide with at least 3 pt and 3 en example phrases and the expected title/urgency/deadline (checked against `TaskParserPrompt`), commands and buttons list, full configuration reference with defaults (every `app.*` variable and `.env.example` entry), home machine / Raspberry Pi run section with `restart: unless-stopped`, update and logs commands, token-error troubleshooting, and remove stale text ("scheduler comes in a later change", amd64-only note if the workflow builds arm64); verify the commands in the README run as written (`docker compose -f docker-compose.yml config`) and the variable list matches `application.yaml`
- [x] 7.2 Run `./mvnw verify` (Docker running) and confirm the whole suite passes, then walk the acceptance flow against the fake Telegram API or a real token: `/tasks` shows pending tasks, Done and Cancel work from it, a failed STT/LLM call does not block the next message, and a seeded 31-day-old DONE task is removed by the retention job
