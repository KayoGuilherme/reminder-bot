# Proposal

## Why

The bot can create tasks and nag about them, but the owner cannot see what is pending or drop a task they no longer need, and the only way out of a reminder cycle is to mark the task done. It is also meant to run unattended on one machine (home server, Raspberry Pi): today old tasks pile up forever, a revoked or mistyped bot token only shows up as noisy failures, logs cannot be tied to one incoming message, stopping the container can cut a reminder run in half, and the README still describes an earlier stage of the bot.

## What Changes

- **`/tasks` command**: lists the owner's `PENDING` tasks ordered by deadline (earliest first), one message per task with the title, deadline in `app.timezone` and an urgency marker (plus an overdue flag), each with inline **Done** and **Cancel** buttons. An empty list gets a friendly reply. At most 20 tasks are shown per call, followed by a "and N more" line.
- **Cancel**: a new `task:cancel:<id>` button action sets the task to `CANCELED` through a conditional update (like Done), edits the message into a confirmation without buttons, and the task never gets another reminder. Done from the list reuses the existing Done action.
- **Resilience**
  - Every external HTTP client has explicit connect and read timeouts: the STT, LLM and Telegram file-download clients already do; the Telegram send client gets them too (`app.telegram.connect-timeout` / `read-timeout`).
  - The one-retry rule for transient STT/LLM errors already exists; its pause becomes configurable (`app.resilience.retry-backoff`, default 500 ms) and a test proves a failed call does not lose the next message.
  - On startup the bot calls Telegram `getMe`: an invalid token is logged as a clear `ERROR` naming `TELEGRAM_BOT_TOKEN`; an unreachable network is only a `WARN`. The app keeps running either way.
  - Every Telegram update and every scheduler run gets a short correlation id in the logging context and in each log line. Audio, transcripts, task text and secrets are never logged.
  - A daily retention job deletes `DONE` and `CANCELED` tasks whose last change is older than `app.retention.days` (default 30). `app.retention.enabled=false` turns it off.
  - Graceful shutdown: the application waits for the reminder run in progress to finish (bounded by a timeout) before stopping, and compose gives the container enough time to do so.
- **README**: usage guide with example voice phrases in Portuguese and English, the command list, a configuration reference for every `app.*` variable, and how to run the container on a home machine or a Raspberry Pi with `docker compose` and `restart: unless-stopped`. Stale statements (such as the scheduler "comes in a later change") are corrected.

## Capabilities

### New Capabilities
- `task-listing`: the `/tasks` command, its ordering, formatting and empty state.
- `task-retention`: the daily cleanup of old finished tasks and its settings.
- `external-call-resilience`: timeouts on every external HTTP client, the single retry with configurable backoff for transient STT/LLM errors, and recovery without losing the next message.
- `log-correlation`: per-update and per-run correlation id in the logs, and the rule that audio, transcripts, task text and secrets are never logged.

### Modified Capabilities
- `reminder-actions`: adds the Cancel action (callback data, conditional update, stale and foreign-task handling).
- `reminder-dispatch`: adds graceful shutdown of the reminder job.
- `app-configuration`: adds the retention, resilience and Telegram timeout settings and the startup bot-token check.
- `container-runtime`: adds the stop grace period in compose and the operator documentation requirement (README for home machine / Raspberry Pi).

## Impact

- **Code**: `IncomingCommandService` (route `/tasks`, `task:cancel:`), `HandleReminderActionUseCase`/`ReminderActionService` (cancel), a new list use case and formatter, `TaskRepository`/`JpaTaskRepository` (`findPendingByChat`, `markCanceled`, `deleteFinishedBefore`), `TaskSummaryFormatter`, `TelegramConfiguration` (timeouts, startup check), `TelegramUpdateConsumer` and `ReminderScheduler` (correlation id), a new retention scheduler, `AppProperties`, `application.yaml`, `messages*.properties` (en and pt-BR).
- **Database**: no migration. Existing `tasks` columns and the `CANCELED` status are enough; cleanup uses `updated_at`, which Done and Cancel set.
- **Config / deployment**: new optional variables `APP_RETENTION_DAYS`, `APP_RETENTION_ENABLED`, `APP_RETRY_BACKOFF`, `APP_SHUTDOWN_TIMEOUT`, `APP_TELEGRAM_CONNECT_TIMEOUT`, `APP_TELEGRAM_READ_TIMEOUT`; `.env.example`, `docker-compose.yml` (`stop_grace_period`), README.
- **Dependencies**: none new.
- **Related changes**: `voice-to-task` and `telegram-bot-base` are not archived yet, so their specs (`telegram-inbound`, `voice-transcription`, `task-parsing`) are not in `openspec/specs/`. This change therefore does not modify them; the resilience and routing behavior it adds is specified in its own new capabilities.
