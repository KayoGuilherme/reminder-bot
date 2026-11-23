# Proposal

## Why

The bot can now create and store tasks, but it never speaks up again afterwards: a task with a deadline just sits in the database. The point of the product is to nudge the user before a deadline, with pressure that matches how urgent the task is, and to do so reliably — a missed reminder defeats the purpose and a duplicate one is noise. The `tasks` table already carries `last_reminder_stage` / `last_reminded_at` and an index on `(status, deadline_at)` for exactly this, so the groundwork is in place.

## What Changes

- Add a pure-domain `ReminderPolicy` that, given a task and the current instant, decides which reminder stage (if any) is due:
  - LOW: stage 1 at T-10min. MEDIUM: stage 1 at T-10min, stage 2 at T-0. HIGH: stage 1 at T-10min, stage 2 at T-5min, stage 3 at T-0 (T = `deadline_at`).
  - Only `PENDING` tasks; a stage is sent only if it is higher than `last_reminder_stage`; when several stages are overdue only the highest is sent, once (no burst after downtime or late creation).
- Add a `SendDueReminders` use case: load `PENDING` tasks whose deadline is within the next 10 minutes (or already past), ask the policy per task, send through `MessageSenderPort`, and record `last_reminder_stage` / `last_reminded_at` only after a successful send, one transaction per task. A failed send changes nothing, so the next tick retries.
- Add a scheduled job that runs the use case with `fixedDelay` taken from `app.scheduler.interval` (default 30s), switchable off with `app.scheduler.enabled=false`; one failing task never stops the others.
- Add externalized reminder texts per urgency and stage, in English and Brazilian Portuguese, with a friendly (LOW), firm (MEDIUM) and aggressive (HIGH) tone that grows more intense with each later stage.
- Extend `TaskRepository` with the two operations the use case needs (query due candidates, guarded stage update).
- A task created already inside the 10-minute window needs no special code: it is a candidate on the very next tick.

## Capabilities

### New Capabilities
- `reminder-policy`: pure domain rules that map a task and the current time to the reminder stage to send, or nothing.
- `reminder-dispatch`: the `SendDueReminders` use case and its scheduled trigger — candidate selection, send-then-record ordering, retry on failure, failure isolation between tasks, and the scheduler settings.
- `reminder-messages`: the per-urgency, per-stage, bilingual reminder texts and how they are rendered.

### Modified Capabilities
- `app-configuration`: adds the `app.scheduler.enabled` and `app.scheduler.interval` settings (with defaults) to the configuration contract.

## Impact

- **Code**: new `domain/ReminderPolicy` (+ decision/stage types); new `application` use case and inbound port; new scheduled adapter; `TaskRepository` and its JPA implementation gain two methods; `AppProperties` gains a `Scheduler` record; `@EnableScheduling` is turned on.
- **Resources**: new keys in `messages.properties` and `messages_pt_BR.properties`; `application.yaml` gains `app.scheduler.*`.
- **Database**: no migration. Uses the existing `last_reminder_stage`, `last_reminded_at` columns and the `idx_tasks_status_deadline` index.
- **Tests**: existing integration tests must run with the scheduler disabled so a background tick cannot interfere; new policy tests and one PostgreSQL-backed job test.
- **Dependencies**: none new.
- **Out of scope**: reminder at `start_at`, snoozing / marking done from the reminder, per-user reminder settings, multi-instance coordination.
