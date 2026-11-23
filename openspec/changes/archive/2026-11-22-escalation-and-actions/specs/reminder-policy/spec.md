# Spec Delta

## MODIFIED Requirements

### Requirement: Stages per urgency
A task's reminders SHALL be numbered stages, each due at a fixed offset before the task's deadline (T): LOW has stage 1 due at T-10 minutes; MEDIUM has stage 1 at T-10 minutes and stage 2 at T; HIGH has stage 1 at T-10 minutes, stage 2 at T-5 minutes and stage 3 at T. A stage is due from its offset onward, inclusive of the exact instant. HIGH additionally has overdue and final stages after stage 3, defined in "HIGH overdue reminders repeat until a cap".

#### Scenario: LOW task before its window
- **WHEN** a pending LOW task is evaluated 10 minutes and 1 second before its deadline with no reminder sent yet
- **THEN** no reminder is due

#### Scenario: LOW task at T-10
- **WHEN** a pending LOW task is evaluated exactly 10 minutes before its deadline with no reminder sent yet
- **THEN** stage 1 is due

#### Scenario: MEDIUM task stages
- **WHEN** a pending MEDIUM task is evaluated at T-10 minutes with no reminder sent, and again at T after stage 1 was sent
- **THEN** stage 1 is due at the first evaluation and stage 2 at the second

#### Scenario: HIGH task stages
- **WHEN** a pending HIGH task is evaluated at T-10 minutes, T-5 minutes and T, each time after the previous stage was sent
- **THEN** stages 1, 2 and 3 are due respectively

#### Scenario: Between stages
- **WHEN** a pending HIGH task whose stage 1 was sent is evaluated at T-7 minutes
- **THEN** no reminder is due

### Requirement: A stage is never repeated or sent out of order
A stage SHALL be due only if it is greater than the task's last sent reminder stage. Once the last stage of an urgency has been sent, no further reminder SHALL be due: for LOW it is stage 1, for MEDIUM stage 2 and for HIGH the final stop stage.

#### Scenario: Stage already sent
- **WHEN** a pending MEDIUM task with last sent stage 1 is evaluated at T-9 minutes
- **THEN** no reminder is due

#### Scenario: All MEDIUM stages sent
- **WHEN** a pending MEDIUM task with last sent stage 2 is evaluated long after its deadline
- **THEN** no reminder is due

#### Scenario: All stages sent
- **WHEN** a pending HIGH task whose final stop stage was sent (stage 14 with the defaults) is evaluated at any later time
- **THEN** no reminder is due

### Requirement: Catch-up sends only the highest due stage
When more than one unsent scheduled stage (1 to 3 for HIGH) is due at the evaluation time (for example after downtime, or because the task was created late), the policy SHALL return only the highest due stage, so a single reminder is sent and the earlier stages are skipped rather than sent in a burst. A catch-up never jumps directly to an overdue repeat: the stage after the scheduled ones is due only once stage 3 itself has been sent.

#### Scenario: Downtime across several stages
- **WHEN** a pending HIGH task with last sent stage 0 is evaluated 1 minute after its deadline
- **THEN** stage 3 is due and stages 1, 2 and 4 are not

#### Scenario: Skipped stages are not revisited
- **WHEN** a HIGH task is evaluated, less than the repeat interval after stage 3 was sent as a catch-up
- **THEN** no reminder is due and stages 1 and 2 are never sent

#### Scenario: Task created inside the window
- **WHEN** a pending HIGH task with last sent stage 0 is evaluated 7 minutes before its deadline
- **THEN** stage 1 is due

#### Scenario: Deadline already passed for a lower urgency
- **WHEN** a pending LOW task with last sent stage 0 is evaluated after its deadline
- **THEN** stage 1 is due

## ADDED Requirements

### Requirement: HIGH overdue reminders repeat until a cap
After stage 3 of a HIGH task was sent, the policy SHALL make an overdue reminder due every `app.reminder.repeat-interval` (default 2 minutes) while the task is PENDING and its deadline has been reached. The n-th overdue reminder is stage 3 + n, for n from 1 to `app.reminder.max-repeats` (default 10), and the last sent stage is the repeat counter. The spacing is measured from the time the previous reminder was actually sent (the task's last reminded time), not from the deadline, so a late send does not cause a burst afterwards. A stage above 3 SHALL never be due before the deadline.

#### Scenario: First overdue reminder
- **WHEN** a pending HIGH task whose stage 3 was sent at T is evaluated 1 minute 59 seconds after T, and again 2 minutes after T
- **THEN** no reminder is due at the first evaluation and stage 4 is due at the second

#### Scenario: Spacing follows the actual send time
- **WHEN** stage 4 was sent 10 seconds late, at T+2m10s, and the task is evaluated at T+4m and at T+4m10s
- **THEN** no reminder is due at T+4m and stage 5 is due at T+4m10s

#### Scenario: Evaluation between repeats
- **WHEN** a pending HIGH task with last sent stage 6 is evaluated less than the repeat interval after its last reminded time
- **THEN** no reminder is due

#### Scenario: Never before the deadline
- **WHEN** a pending HIGH task with last sent stage 5 but a deadline still in the future is evaluated more than the repeat interval after its last reminded time
- **THEN** no reminder is due

#### Scenario: Custom interval
- **WHEN** `app.reminder.repeat-interval` is 1 minute and a pending HIGH task whose stage 3 was sent at T is evaluated at T+1m
- **THEN** stage 4 is due

#### Scenario: Done stops the repeats
- **WHEN** a HIGH task with last sent stage 5 becomes DONE and is evaluated after the repeat interval
- **THEN** no reminder is due

### Requirement: A final message ends the nagging after the cap
After the last allowed overdue reminder (stage 3 + max-repeats) was sent and the repeat interval has passed, the policy SHALL make one final stop stage (stage 3 + max-repeats + 1) due, after which no further reminder SHALL be due for the task. With the defaults the overdue stages are 4 to 13 and the final stage is 14.

#### Scenario: Cap reached
- **WHEN** a pending HIGH task with last sent stage 13 (the 10th overdue reminder) is evaluated after the repeat interval, with the default cap
- **THEN** stage 14, the final stop stage, is due

#### Scenario: Nothing after the final stage
- **WHEN** a pending HIGH task with last sent stage 14 is evaluated hours later
- **THEN** no reminder is due

#### Scenario: Custom cap
- **WHEN** `app.reminder.max-repeats` is 3 and a pending HIGH task with last sent stage 6 is evaluated after the repeat interval
- **THEN** stage 7, the final stop stage, is due

#### Scenario: Ignored task over time
- **WHEN** a HIGH task is ignored from T onward, with the policy evaluated continuously and each due stage sent when due, using the defaults
- **THEN** exactly one stage 3, ten overdue reminders (stages 4 to 13) and one final message are sent, about 2 minutes apart, and nothing afterwards

### Requirement: LOW and MEDIUM tasks never repeat
LOW and MEDIUM tasks SHALL receive no reminder after their last scheduled stage, regardless of how long they stay pending or overdue.

#### Scenario: Overdue MEDIUM task
- **WHEN** a pending MEDIUM task with last sent stage 2 is evaluated 1 hour after its deadline
- **THEN** no reminder is due

#### Scenario: Overdue LOW task
- **WHEN** a pending LOW task with last sent stage 1 is evaluated 1 hour after its deadline
- **THEN** no reminder is due

### Requirement: A snoozed task restarts at stage 1
A pending task whose last reminder stage was reset to 0 and whose deadline was moved to 10 minutes ahead SHALL have stage 1 due immediately, then stages 2 and 3 at their offsets, for every urgency with those stages.

#### Scenario: Immediate stage 1 after snooze
- **WHEN** a pending HIGH task with stage 0 and a deadline exactly 10 minutes ahead is evaluated
- **THEN** stage 1 is due
