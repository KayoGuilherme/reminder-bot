# Design

## Context

See proposal.md for motivation. Current state, from reading the code:

- Hexagonal layout under `com.kayo.reminder`: `domain` (pure records/enums: `Task`, `Urgency`, `TaskStatus`), `application.port.in/out`, `application.service`, `infrastructure.*`, `config`.
- `Task` already has `lastReminderStage` (short), `lastRemindedAt`, `deadlineAt`, `urgency`, `status`. The `tasks` table has `last_reminder_stage`, `last_reminded_at` and `idx_tasks_status_deadline (status, deadline_at)`. No migration is needed.
- `TaskRepository` only has `save` and `findById`; `JpaTaskRepository` uses an `EntityManager` with `@Transactional` methods.
- `MessageSenderPort.sendText(chatId, text)` never throws; failure is a `SendResult` with `success() == false`. The only implementation (`TelegramMessageSender`) already retries transient errors and honors 429 inside the call.
- `Messages` wraps `MessageSource` for the single language in `app.language`. Texts are `MessageFormat` strings with positional `{0}` arguments, and apostrophes are doubled (see `messages.properties`).
- `Clock` is an injectable `Clock.systemUTC()` bean. Services that need Telegram are `@ConditionalOnProperty(app.telegram.enabled)`, and integration tests build services by hand with a `MutableClock` and fake senders, using the real repository from the Spring context (see `VoiceToTaskIntegrationTest`).
- `AppProperties` is a validated `@ConfigurationProperties(prefix = "app")` record.

## Goals / Non-Goals

**Goals:**
- Exactly-once-in-practice delivery: no missed reminder, no duplicate across ticks or restarts.
- Time rules in one pure, table-testable class; I/O and persistence kept out of it.
- A bounded candidate query that stays cheap as tasks accumulate.

**Non-Goals:**
- Distributed locking or running several app instances; the deployment is a single instance.
- Exactly-once across a crash between "send succeeded" and "stage recorded" (impossible with an external send; see Risks).
- Per-task or per-user reminder customization, snooze, or acting on a reminder.

## Decisions

**1. `ReminderPolicy` is a plain class with `Optional<ReminderDecision> decide(Task task, Instant now)`.**
It holds the offsets per urgency as data (`LOW: [10m]`, `MEDIUM: [10m, 0]`, `HIGH: [10m, 5m, 0]`), stage = index + 1. `decide` returns empty unless the task is `PENDING`; otherwise it finds the highest stage whose `deadline - offset <= now` and returns it only if it is greater than `lastReminderStage`. Highest-due-wins gives the no-burst catch-up for free, and skipped stages are never revisited because `lastReminderStage` jumps past them. It also exposes `finalStage(Urgency)`. `ReminderDecision` is a small record `(int stage)`. `now` is passed in (the use case reads it from the `Clock`) so the policy has no hidden dependency and tests use a fixed instant.
*Alternative:* have the policy take a `Clock`. Rejected: a pure `(task, instant)` function is simpler to table-test and matches the stated contract.

**2. Candidate query uses the index and excludes exhausted tasks.**
`TaskRepository.findReminderCandidates(Instant deadlineBefore)` returns `PENDING` tasks with `deadline_at <= :cutoff` (cutoff = now + 10 min, the largest stage-1 offset, taken from the policy) **and** `last_reminder_stage < finalStage(urgency)`, ordered by `deadline_at`. Without the second predicate, every past-deadline task that is never marked DONE would be re-read every tick forever. The per-urgency final stage is passed from the domain (`ReminderPolicy.finalStage`) as query parameters (three OR'd `(urgency = ? AND last_reminder_stage < ?)` terms in JPQL) so the numbers live in one place and not hard-coded in SQL. The `(status, deadline_at)` index serves the range scan; the stage predicate is a cheap filter on the result.
*Alternative:* filter only in Java. Rejected: unbounded growth of the scanned set over time.

**3. Send first, record after, in a guarded update; the transaction covers only the update.**
Per candidate the service does: re-read the task by id (it may have changed since the list query) → `policy.decide` → render text → `sender.sendText` → if `success()`, call `TaskRepository.markReminded(id, stage, now)`. `markReminded` is one `@Transactional` JPQL/SQL `UPDATE tasks SET last_reminder_stage = :stage, last_reminded_at = :now, updated_at = :now WHERE id = :id AND status = 'PENDING' AND last_reminder_stage < :stage` returning the affected row count. This is the "one transaction per task" of the brief, and the guard makes it safe against a concurrent status change and against a stale read. The HTTP call is deliberately **not** inside a database transaction: `TelegramMessageSender` may sleep through retries/429 waits, which would hold a connection open.
*Alternative:* one transaction around the whole run, or `SELECT … FOR UPDATE` held across the send. Rejected: connection held during network I/O, and one task's failure would roll back others.

**4. Failure isolation.** The run loop wraps each task in `try/catch (RuntimeException)`, logs the task id and failure kind (never the title or bot token) and continues. A failed `SendResult` is treated as "nothing happened": no update, next tick retries the same stage. `markReminded` returning 0 rows is logged at debug and ignored. The scheduled adapter additionally catches anything escaping the use case so a bad run cannot cancel the schedule (Spring's `@Scheduled` would otherwise keep running anyway, but the catch gives a controlled log line).

**5. Text rendering via `Messages` with keys `reminder.<URGENCY>.<stage>`**, e.g. `reminder.HIGH.3`. Arguments are positional: `{0}` = title, `{1}` = minutes. The brief names the placeholders `{title}` and `{minutes}`; `MessageSource`/`MessageFormat` has no named arguments, and the project's existing texts are positional, so `{0}`/`{1}` are used and documented in a comment at the top of the reminder block in both properties files. Titles are passed as arguments, never concatenated into the pattern, so braces or quotes in a title are shown literally. A small `ReminderMessageFactory` (application service) maps `(Urgency, stage)` to a key and computes minutes. Minutes = `ceil((deadline - now) / 1 minute)` clamped to ≥ 0, so a stage-1 sent 20 seconds after T-10 still says 10, a late-created task says the real remaining time, and an overdue one says 0.
Tone, English (pt-BR mirrors the intent):
- LOW 1: `Hey, {1} minutes left for {0}, whenever you're ready.`
- MEDIUM 1: `{0} is due in {1} minutes. Time to start.`; MEDIUM 2: `{0} is due NOW. Stop what you're doing and finish it.` (firmer, still measured)
- HIGH 1: `{0} is due in {1} MINUTES. Drop everything and do it now.`; HIGH 2: `{0} is due in {1} MINUTES. Why aren't you doing this yet?! MOVE.`; HIGH 3: `{0} is DUE. RIGHT NOW. No more excuses — DO IT!`
Final wording is the implementer's call within these tones; tests assert the stage-1 reference strings and that every key exists in both locales.

**6. Wiring.** `SendDueRemindersUseCase` (port in) / `SendDueRemindersService` (application service, `@ConditionalOnProperty(app.telegram.enabled)` like the other Telegram-dependent services, since it needs `MessageSenderPort`). A `ReminderScheduler` in `infrastructure.scheduling` calls the use case from a `@Scheduled(fixedDelayString = "${app.scheduler.interval}")` method. It is registered as a `@Bean` by a `SchedulingConfiguration` that carries `@EnableScheduling` and is `@ConditionalOnProperty(app.scheduler.enabled, matchIfMissing = true)`; the bean method is also `@ConditionalOnBean(SendDueRemindersUseCase)`, so with Telegram (and hence the use case) off there is nothing to schedule. `fixedDelay` (not `fixedRate`) guarantees runs never overlap and a slow run cannot queue a pile-up. `AppProperties` gains `@DefaultValue Scheduler(@DefaultValue("true") boolean enabled, @DefaultValue("30s") @DurationMin(nanos = 1) Duration interval)`, and `application.yaml` gets `app.scheduler.*` with the same defaults. Spring Boot 4 parses `30s`-style durations in `fixedDelayString`; `ReminderSchedulerTest` confirms it.

**7. Tests.** `ReminderPolicyTest` is table-driven (`@ParameterizedTest` + `@MethodSource`) over urgency × stage × offset around each boundary (T-10:01, T-10:00, T-5:00, T, after T) using a fixed `Clock`, plus: non-PENDING, already-sent, catch-up after downtime (highest stage only), late creation, idempotency (re-deciding after the stage is recorded yields empty). `SendDueRemindersIntegrationTest` extends `AbstractIntegrationTest`, builds the service by hand with the real `TaskRepository`, a `MutableClock` and a Mockito `MessageSenderPort` (the repo's existing pattern), and drives the HIGH-due-in-12-minutes scenario in 30 s steps, asserting exactly three `sendText` calls and stored stages 1→2→3; a failed send leaves stage 0 and the next tick resends; a rebuilt service instance (restart) sends nothing new; DONE/CANCELED tasks get nothing; one failing task does not block another. `AbstractIntegrationTest` sets `app.scheduler.enabled=false` so no background tick runs during any test.

## Risks / Trade-offs

- **Crash between a successful send and the `markReminded` update → duplicate on restart.** Unavoidable for an external send without an outbox; the window is milliseconds and the failure mode is one repeated reminder, never a missed one (at-least-once). Accepted; stated rather than hidden.
- **Stale reminder after long downtime.** A task whose deadline passed during downtime gets one catch-up reminder on restart (stage = highest due, minutes shown as 0), as the brief requires ("never miss one"). If this proves noisy, a staleness cutoff could be added later without changing the policy shape.
- **Reminder timing resolution is the interval.** With a 30 s delay a reminder can be up to ~30 s late (plus run time). Mitigation: minutes are rounded up so the text still reads correctly; the interval is configurable.
- **Single instance assumed.** Two running instances could both read the same stage and both send. The guarded update prevents double-recording, not double-sending. Out of scope; noted for a future lock if the deployment scales out.
- **Task status changed between the list query and the send.** Mitigated by re-reading the task right before sending and by the `status = 'PENDING'` guard on the update; a vanishing window remains and is acceptable.
- **Telegram outage.** Sends fail, stages stay put, every tick retries; once Telegram returns the highest due stage is sent once. Each failed tick also costs the sender's internal retry/backoff time per task, which `fixedDelay` absorbs without overlapping.

## Migration Plan

No database migration. Deploy is a normal image release; on first start the job begins ticking. Rollback is reverting the image (or setting `app.scheduler.enabled=false`); stored stages are only ever raised by successful sends, so a rollback and redeploy never re-sends reminders already delivered.

## Open Questions

- Exact pt-BR wording and the HIGH stage-2/3 and MEDIUM stage-2 phrasing are drafted above; they can be tuned during implementation without affecting specs or tasks.
