# Design

## Context

Reminders are produced by `SendDueRemindersService`, which asks `ReminderPolicy.decide(task, now)` for a stage, renders it with `ReminderMessageFactory`, sends it with `sendText`, and then records it with `TaskRepository.markReminded` (see proposal.md for motivation). Today the policy is a stateless `new ReminderPolicy()` (used by the service and by `JpaTaskRepository.findReminderCandidates` for per-urgency final stages), HIGH ends at stage 3, and `last_reminder_stage` / `last_reminded_at` are the only reminder state on the `tasks` row (SMALLINT, no range check, so values up to 14 fit without a migration).

Button presses already reach `IncomingCommandService.handleCallback`, which answers the callback query first and then dispatches on the callback data prefix (`task:ok:` / `task:no:` for the draft flow). `MessageSenderPort` already offers `sendWithButtons`, `editText` and `answerCallback`, and `TelegramMessageSender.editText` omits the keyboard, so editing a message also removes its buttons. `UpdateAllowlist` already restricts updates to the owner. There is a single owner and a single instance, but the scheduler thread and the Telegram update thread run concurrently.

## Goals / Non-Goals

**Goals:**
- Done and Snooze through inline buttons on every reminder, idempotent and safe on stale buttons.
- HIGH overdue nagging with a cap and a final message, expressed entirely through the existing `last_reminder_stage` and `last_reminded_at` columns.
- Keep the policy a pure function of the task, the instant and its two settings.

**Non-Goals:**
- No new migration, no new table, no new task status, no per-task snooze duration or configurable snooze length (10 minutes is a constant).
- No nagging for LOW or MEDIUM, no "cancel" button, no persistence of which Telegram messages belong to a task.
- No change to how tasks are created.

## Decisions

**1. Stage numbering for HIGH extends the existing counter.** Stages 1 to 3 stay scheduled; overdue repeat n is stage `3 + n` for n in `1..maxRepeats`; the final stop message is stage `4 + maxRepeats` (14 with defaults). `ReminderPolicy` gets a constructor `(Duration repeatInterval, int maxRepeats)`; `finalStage(HIGH)` becomes `4 + maxRepeats`, LOW and MEDIUM keep 1 and 2. A no-argument factory with the defaults keeps existing call sites and tests simple. The policy stays in the domain package with no Spring types; a configuration class builds the single `ReminderPolicy` bean from `AppProperties.Reminder`, and both `SendDueRemindersService` and `JpaTaskRepository` receive it, so the stage numbers still live in one place.
*Alternative: a new `overdue_count` column.* Rejected by the requirement of no migration, and the stage already is a monotonic counter.

**2. Decision algorithm.** For `lastStage < 3` the current catch-up loop is unchanged (highest due of stages 1..3 by offset). For HIGH with `3 <= lastStage < final`, the next stage `lastStage + 1` is due iff `now >= deadline` and `now >= lastRemindedAt + repeatInterval` (a null `lastRemindedAt`, which should not happen for stage 3 or above, counts as elapsed). `lastStage >= final` and non-HIGH beyond their last stage give nothing. Only one stage is returned per evaluation, so a long outage yields one overdue message, not a burst, and spacing restarts from the real send time. The `now >= deadline` guard makes a stale stage above 3 harmless if its deadline was moved forward (see Decision 6).
`ReminderDecision` becomes `(int stage, Kind kind, int repeat)` with `Kind` in `SCHEDULED`, `OVERDUE`, `FINAL`, computed by the policy, so the message factory does not re-derive the numbering.

**3. Overdue texts are banded, not per-stage.** Because the cap is configurable, a key per repeat number cannot work. Keys: `reminder.HIGH.overdue.1` (repeats 1-3), `.2` (4-6), `.3` (7 and above, also used for anything higher), and `reminder.HIGH.final`, in both property files. The factory picks the band from `decision.repeat()`. For these texts argument `{1}` is the whole minutes overdue (floor of now minus deadline, never negative) instead of minutes left; scheduled stages keep the ceiling-of-minutes-left rule. The existing key-parity test is extended to the new keys.

**4. Reminder actions live in a new use case.** `HandleReminderActionUseCase` (in-port) exposes `done(chatId, messageId, taskId)` and `snooze(chatId, messageId, taskId)` and defines the prefixes `task:done:` and `task:snooze:` (46 and 48 bytes with a 36-character UUID, under the 64-byte limit enforced by `Button`). `IncomingCommandService.handleCallback` keeps answering the callback first, then parses the prefix and UUID like it does for drafts; malformed data is ignored. `ReminderActionService` loads the task, treats "absent or `chatId` differs" identically (same polite reply, so another chat's task ids are not probed), then applies the action. The edit-or-fallback helper (`show`) is the same pattern as `CreateTaskFromVoiceService`: try `editText`, on failure send a new message.

**5. State changes are conditional updates on the repository port.** New methods: `boolean markDone(UUID id, Instant at)` (`where id = :id and status = PENDING`) and `boolean snooze(UUID id, Instant expectedDeadline, Instant newDeadline, Instant at)` (`where id = :id and status = PENDING and deadline_at = :expectedDeadline and last_reminder_stage > 0`, setting `deadline_at`, `last_reminder_stage = 0`, `updated_at`). The service reads the task, computes `max(deadline, now) + 10 min`, and calls `snooze`; a `false` result is resolved by re-reading the task and replying according to what is found (DONE or CANCELED: not active; PENDING with stage 0: already snoozed). `last_reminded_at` is left as it is: stages below 3 do not use it.
*Alternative: compute `greatest(deadline_at, now) + interval` in SQL.* Rejected: it needs a native query and hides the rule from unit tests; the compare-and-set on the old deadline gives the same atomicity.

**6. Snooze idempotency and the scheduler race.** Requiring `last_reminder_stage > 0` makes a double press (or a press on an older reminder message right after a snooze) a no-op until the restarted cycle has sent its stage 1, which is at most one scheduler interval later. Without it, a second press would silently stack another 10 minutes. The race with the scheduler is closed by extending `markReminded` with the deadline the decision was made on (`markReminded(id, stage, at, expectedDeadline)`, adding `and t.deadlineAt = :deadline`), so an overdue message that was in flight during a snooze cannot write its high stage back over the reset one. The policy's `now >= deadline` guard is a second line of defence. A Done pressed while a send is in flight can still let that one message go out; the existing `status = PENDING` guard keeps the stored state correct, and the next run sends nothing.

**7. Scheduler and sending.** `SendDueRemindersService.remind` switches from `sendText` to `sendWithButtons` with one row of two buttons (labels `reminder.done-button`, `reminder.snooze-button`, callback data from the new use case's prefixes and the task id). The candidate query keeps `status = PENDING and deadline_at <= cutoff and last_reminder_stage < finalStage(urgency)`, now with the policy-derived HIGH final of `4 + maxRepeats`; past-deadline tasks were already included, so overdue HIGH tasks remain candidates without further change. Spacing is judged by the policy per task, so no date arithmetic is needed in SQL. With the default 30 s scheduler interval the actual gap between nags is 2 min plus up to one interval.

**8. Configuration.** `AppProperties.Reminder(@DefaultValue("2m") @DurationMin(nanos = 1) Duration repeatInterval, @DefaultValue("10") @Min(1) int maxRepeats)` with `@DefaultValue` on the record component like `Scheduler`; `application.yaml` maps `APP_REMINDER_REPEAT_INTERVAL` and `APP_REMINDER_MAX_REPEATS`; `.env.example` and the README table document them. `TestProperties` and `AppPropertiesTest` are updated for the new record component.

## Risks / Trade-offs

- [A tab full of old reminder messages keeps stale buttons] → Every action is idempotent and answers politely; Done and Snooze edit the message they were pressed on, other messages keep their buttons by design.
- [Stage numbers above 3 now mean overdue repeats, so a downgraded build or a lowered `max-repeats` meets rows with a stage above its final] → The policy treats `lastStage >= final` as finished, so a lowered cap just ends the nagging sooner; a downgrade to the old policy also treats stage above 3 as exhausted.
- [Snooze on a task that is still before its deadline pushes it out by 10 minutes from the current deadline, not from now] → Follows the requested `max(deadline_at, now) + 10 min` rule and is written into the spec.
- [A restart in the middle of nagging] → State is in the database, so nagging resumes with spacing from the stored `last_reminded_at`; no duplicate stage is sent.
- [The reminder text could exceed Telegram's 4096-character limit with a very long title] → Titles are capped at 500 characters, texts are short, so this cannot happen.
- [Button press by a second chat] → Ignored by `UpdateAllowlist` for other users and also by the chat ownership check.

## Migration Plan

No schema change. Deploy the new image; reminders that were already sent keep their stage. A HIGH task that was exhausted at stage 3 before the deploy and is still PENDING will start receiving overdue reminders at the next runs, up to the cap, which is the intended behavior. Rollback is redeploying the previous image: stages above 3 are then treated as exhausted.
