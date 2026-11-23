# Proposal

## Why

The bot picks its reply language from `app.language` (fixed at `en`), not from the language the user wrote in. Reminders, the Done and Snooze buttons, the snooze confirmation and the `/tasks` list therefore come out in English for a Portuguese user, and the snoozed deadline prints as ISO `2026-11-23 09:10`, while the saved-task summary already shows `23/11/2026 09:00`. A pt-BR user sees Portuguese and English mixed in one conversation.

## What Changes

- Each task remembers the language its draft was written in (`en` or `pt-BR`), stored in a new `language` column added by Flyway migration V2. Existing rows default to `en`.
- Every message that concerns a task is rendered in that task's language: reminders (all urgencies, stages, overdue and final texts), the Done and Snooze 10 min buttons, the Done and Snooze confirmations, the polite replies, and the `/tasks` list with its Done and Cancel buttons.
- Every date and time shown to the user uses the task language's `date.pattern`: `dd/MM/yyyy HH:mm` for pt-BR, `yyyy-MM-dd HH:mm` for en. This covers the saved-task summary, the `/tasks` list and the snoozed new deadline. The times stay in `app.timezone`.
- `app.language` stays as the fallback for anything not tied to a task (for example the `/start` usage text and the not-supported reply).
- **BREAKING** for behaviour, not for the API: reminders no longer follow `app.language`; they follow the language of the task. Existing tasks keep `en` and therefore keep their current wording.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `task-storage-schema`: the `tasks` table gains a `language` column (`VARCHAR(5)`, required, default `en`, restricted to `en` and `pt-BR`).
- `reminder-messages`: the language of a reminder comes from the task, not from `app.language`; the date pattern follows that language.
- `reminder-actions`: the Done and Snooze buttons, the Done and Snooze confirmations and the polite replies follow the task's language; the snoozed new deadline is formatted with the task language's date pattern.
- `task-listing`: the `/tasks` list, its labels, its buttons and its empty and "more" messages follow the task's language, and deadlines use the task language's date pattern.

## Impact

- Database: new migration `V2__add_task_language.sql`; `TaskEntity` gets a `language` field.
- Application: `TaskResponse`, `TaskMapper`, `TaskFactory` (carries the draft language), `ReminderMessageFactory`, `ReminderActionService`, `ListTasksService`, `TaskSummaryFormatter` (builds its date formatter per language instead of once with the default `Messages`).
- Tests: every `new TaskResponse(...)` call site and the in-memory task service gain the language argument; reminder and listing tests assert pt-BR output for pt-BR tasks.
- No change to the LLM prompt, the speech-to-text configuration or the public Telegram behaviour beyond the wording and date format.
