# Spec Delta

## Purpose

Defines which reminder, if any, a task is owed at a given moment, so reminders arrive at the right times, never repeat and never pile up. It depends only on the task and the current time.

## ADDED Requirements

### Requirement: Stages per urgency
A task's reminders SHALL be numbered stages, each due at a fixed offset before the task's deadline (T): LOW has stage 1 due at T-10 minutes; MEDIUM has stage 1 at T-10 minutes and stage 2 at T; HIGH has stage 1 at T-10 minutes, stage 2 at T-5 minutes and stage 3 at T. A stage is due from its offset onward, inclusive of the exact instant.

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

### Requirement: Only pending tasks are reminded
Tasks whose status is not PENDING SHALL never have a reminder due, regardless of the time.

#### Scenario: Done task
- **WHEN** a DONE task is evaluated inside its reminder window
- **THEN** no reminder is due

#### Scenario: Canceled task
- **WHEN** a CANCELED task is evaluated inside its reminder window
- **THEN** no reminder is due

### Requirement: A stage is never repeated or sent out of order
A stage SHALL be due only if it is greater than the task's last sent reminder stage. Once the last stage of an urgency has been sent, no further reminder SHALL be due.

#### Scenario: Stage already sent
- **WHEN** a pending MEDIUM task with last sent stage 1 is evaluated at T-9 minutes
- **THEN** no reminder is due

#### Scenario: All stages sent
- **WHEN** a pending HIGH task with last sent stage 3 is evaluated after its deadline
- **THEN** no reminder is due

### Requirement: Catch-up sends only the highest due stage
When more than one unsent stage is due at the evaluation time (for example after downtime, or because the task was created late), the policy SHALL return only the highest due stage, so a single reminder is sent and the earlier stages are skipped rather than sent in a burst.

#### Scenario: Downtime across several stages
- **WHEN** a pending HIGH task with last sent stage 0 is evaluated 1 minute after its deadline
- **THEN** stage 3 is due and stages 1 and 2 are not

#### Scenario: Skipped stages are not revisited
- **WHEN** a HIGH task is evaluated after stage 3 was sent as a catch-up
- **THEN** no reminder is due

#### Scenario: Task created inside the window
- **WHEN** a pending HIGH task with last sent stage 0 is evaluated 7 minutes before its deadline
- **THEN** stage 1 is due

#### Scenario: Deadline already passed for a lower urgency
- **WHEN** a pending LOW task with last sent stage 0 is evaluated after its deadline
- **THEN** stage 1 is due

### Requirement: Decisions depend only on the task and the instant
The decision SHALL be a pure function of the task and the evaluation instant: evaluating the same task at the same instant always yields the same result and has no side effects.

#### Scenario: Repeated evaluation
- **WHEN** the same task is evaluated twice at the same instant
- **THEN** both evaluations return the same decision and the task is unchanged
