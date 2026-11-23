# Proposal

## Why

Reminders are one-way today: the bot nags, but the user can neither tell it "done" nor "give me ten more minutes", and a HIGH task that is ignored goes silent right after its deadline, which is exactly when it matters most. Reminders need action buttons, and HIGH urgency needs to keep nagging until the user deals with the task.

## What Changes

- Every reminder message is sent with two inline buttons, **Done** and **Snooze 10 min**. The callback data carries the action and the task id; the handler verifies the task exists and belongs to the chat that pressed the button.
- **Done** sets the task to `DONE`, edits the reminder message into a confirmation (removing its buttons) and thereby stops all reminders.
- **Snooze** sets `deadline_at = max(deadline_at, now) + 10 min` and resets `last_reminder_stage` to 0, then edits the message to say it was snoozed. Past the deadline this makes the next scheduler tick send a "10 minutes left" reminder immediately; that is intended and is written into the spec.
- **HIGH repeat nagging**: after stage 3 (T-0), the scheduler sends an "overdue" reminder every `app.reminder.repeat-interval` (default 2 min) while the task is `PENDING`, using `last_reminder_stage` (4 and up) as the repeat counter and `last_reminded_at` for spacing, so there is no new migration. After `app.reminder.max-repeats` (default 10) overdue reminders, one final "I'll stop reminding you" message is sent and the task is never messaged again. LOW and MEDIUM never repeat.
- New overdue message texts for HIGH in English and pt-BR, growing more aggressive as the repeat count rises, plus the final message.
- Every callback query is answered so the button spinner stops. Pressing Done or Snooze twice, or on a missing, finished or foreign task, changes nothing and gets a polite reply.
- Because snooze and the scheduler can now race, recording a sent reminder additionally requires the task's deadline to be unchanged since the decision was made.

## Capabilities

### New Capabilities
- `reminder-actions`: the Done and Snooze buttons on reminder messages: callback data format, ownership check, state changes, message edits, idempotency and polite replies for stale presses.

### Modified Capabilities
- `reminder-policy`: HIGH gains overdue repeat stages (4 up to 3 + max repeats) spaced by the repeat interval, and a final stop stage; the "all stages sent" rule now means the final stop stage, and repeats are due only at or after the deadline. LOW and MEDIUM are unchanged.
- `reminder-dispatch`: reminders are sent with the Done/Snooze buttons; candidates include overdue HIGH tasks until the final stage; the recorded stage is applied only if the deadline is unchanged; a snooze followed by the next run restarts the cycle.
- `reminder-messages`: overdue and final HIGH texts in both languages with intensity banding, and a defined meaning of the minutes argument for them (whole minutes overdue); button labels and the action confirmations.
- `app-configuration`: new settings `app.reminder.repeat-interval` and `app.reminder.max-repeats`, with defaults and validation.

## Impact

- Domain: `ReminderPolicy` (now constructed with the repeat interval and cap), `ReminderDecision` (kind and repeat number), possibly `Task` helpers for done/snooze transitions.
- Application: `SendDueRemindersService` (sends with buttons), `ReminderMessageFactory` (overdue and final texts), a new reminder-actions use case and service, routing in `IncomingCommandService`.
- Ports: `TaskRepository` gains `markDone` and `snooze` and `markReminded` gains the expected deadline; the `in` port gets the new action use case. `MessageSenderPort` is unchanged.
- Infrastructure: `JpaTaskRepository` (new updates, policy-driven candidate query), a `ReminderPolicy` bean wired from configuration.
- Config and docs: `AppProperties.Reminder`, `application.yaml`, `.env.example`, `README.md`, `messages.properties` and `messages_pt_BR.properties`.
- Tests: `ReminderPolicyTest`, `SendDueRemindersServiceTest`/integration test, `ReminderMessageFactoryTest`, `AppPropertiesTest`, `InMemoryTaskRepository`, `TestProperties`, plus new callback-handling and snooze-then-scheduler tests.
- No database migration and no new dependency.
