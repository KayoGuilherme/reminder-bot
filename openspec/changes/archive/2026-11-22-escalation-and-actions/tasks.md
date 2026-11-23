# Tasks

## 1. Configuration

- [x] 1.1 Add `AppProperties.Reminder(repeatInterval default 2m, maxRepeats default 10 with min 1)` and map `APP_REMINDER_REPEAT_INTERVAL` / `APP_REMINDER_MAX_REPEATS` in `application.yaml`; update `TestProperties`; extend `AppPropertiesTest` with defaults, custom values, `0s` interval and `0` cap failures, and verify `./mvnw -q test -Dtest=AppPropertiesTest` passes
- [x] 1.2 Document both variables in `.env.example` and the README variables table and verify the names and defaults match `application.yaml`

## 2. Policy

- [x] 2.1 Give `ReminderPolicy` a `(Duration repeatInterval, int maxRepeats)` constructor plus a defaults factory, make `finalStage(HIGH)` equal `4 + maxRepeats`, and add `Kind` and `repeat` to `ReminderDecision` (SCHEDULED, OVERDUE, FINAL)
- [x] 2.2 Implement the repeat logic in `decide` (next stage when `now >= deadline` and `now >= lastRemindedAt + repeatInterval`, final stage after the cap, nothing after it, LOW and MEDIUM unchanged) and verify existing `ReminderPolicyTest` rows still pass
- [x] 2.3 Extend the table-driven `ReminderPolicyTest` with: repeat spacing at 1m59s and 2m, spacing from a late send, no repeat before the deadline with a stale high stage, custom interval and cap, stage 13 to 14, nothing after 14, catch-up not jumping to stage 4, LOW/MEDIUM never repeating, DONE stopping repeats, and stage 1 due right after a snooze reset; verify `./mvnw -q test -Dtest=ReminderPolicyTest` passes
- [x] 2.4 Provide a single `ReminderPolicy` bean built from `AppProperties.Reminder` and inject it into `SendDueRemindersService` and `JpaTaskRepository` in place of `new ReminderPolicy()`; verify the application context starts in `FoundationSmokeTest`

## 3. Texts

- [x] 3.1 Add `reminder.done-button`, `reminder.snooze-button`, `reminder.HIGH.overdue.1..3`, `reminder.HIGH.final`, the done and snoozed confirmations and the polite replies (task not available, already done, no longer active, already snoozed) to `messages.properties` and `messages_pt_BR.properties`, with the overdue texts growing more aggressive per band
- [x] 3.2 Teach `ReminderMessageFactory` to render OVERDUE (band from the repeat number, clamped to 3, `{1}` = whole minutes overdue) and FINAL decisions; extend `ReminderMessageFactoryTest` for repeats 1 to 12 in both languages, minutes overdue rounding, the beyond-band repeat 25 case, the final wording ("stop reminding") and literal titles; extend the existing key-parity test and verify `./mvnw -q test -Dtest='ReminderMessageFactoryTest,MessagesTest'` passes

## 4. Persistence

- [x] 4.1 Add `markDone(id, at)` and `snooze(id, expectedDeadline, newDeadline, at)` to `TaskRepository`, add the expected deadline to `markReminded`, implement them as conditional updates in `JpaTaskRepository` (snooze requires PENDING, the expected deadline and stage above 0), and update `InMemoryTaskRepository` the same way
- [x] 4.2 Verify with `JpaTaskRepositoryTest` cases: done applies once and not on DONE or CANCELED, snooze applies once with a stale deadline rejected and stage 0 rejected, `markReminded` rejected after a snooze changed the deadline, and HIGH candidates up to the new final stage; `./mvnw -q test -Dtest=JpaTaskRepositoryTest` passes

## 5. Dispatch with buttons

- [x] 5.1 Create `HandleReminderActionUseCase` with the `task:done:` and `task:snooze:` prefixes and make `SendDueRemindersService` send with `sendWithButtons` (Done and Snooze 10 min, callback data under 64 bytes), passing the deadline of the decision to `markReminded`
- [x] 5.2 Update `SendDueRemindersServiceTest` for the button payload on scheduled, overdue and final messages, one message per run for an overdue task, failed overdue send retried with unchanged state, and the record skipped when the deadline changed; verify `./mvnw -q test -Dtest=SendDueRemindersServiceTest` passes

## 6. Reminder actions

- [x] 6.1 Implement `ReminderActionService` for Done and Snooze (ownership check identical to not-found, `max(deadline, now) + 10 min`, edit with fallback to a new message, polite replies on stale presses, edit never causing a second application) and route the new prefixes in `IncomingCommandService.handleCallback`, ignoring malformed data after answering the callback
- [x] 6.2 Add `ReminderActionServiceTest` and extend `IncomingCommandServiceTest` for: Done, Snooze before and after the deadline, double Done, double Snooze, DONE and CANCELED tasks, unknown task, another chat's task, malformed data, callback always answered, and edit failure fallback; verify `./mvnw -q test -Dtest='ReminderActionServiceTest,IncomingCommandServiceTest'` passes
- [x] 6.3 Extend `TelegramFlowTest` with a reminder button press through the fake Telegram API (callback answered, message edited without buttons) and verify the test passes

## 7. Integration and docs

- [x] 7.1 Extend `SendDueRemindersIntegrationTest` with: an ignored HIGH task nagging every 2 minutes up to the cap then one final message then silence, Done mid-sequence stopping it, and Snooze of an overdue task followed by the scheduler (immediate stage 1, then stages 2 and 3, then fresh overdue counter); verify `./mvnw -q test -Dtest=SendDueRemindersIntegrationTest` passes
- [x] 7.2 Update the README reminders and task-actions description (buttons, snooze rule including the immediate "10 minutes left" after the deadline, overdue nagging, cap and final message) and verify the described behavior matches the specs
- [x] 7.3 Run the full suite with `./mvnw -q verify` and `openspec validate escalation-and-actions --strict` and verify both pass
