# Spec Delta

## Purpose

Defines what a task is — its fields, urgency levels, lifecycle statuses and validity rules — so that every task the application creates is well-formed regardless of where its data came from.

## ADDED Requirements

### Requirement: Task fields and enumerations
A task SHALL have an id, the chat it belongs to, a title, an urgency, a status, an optional start time, a deadline, and creation and update timestamps. Urgency SHALL be one of `LOW`, `MEDIUM` or `HIGH`. Status SHALL be one of `PENDING`, `DONE` or `CANCELED`. A newly created task SHALL have status `PENDING`.

#### Scenario: New task is pending
- **WHEN** a valid task is created
- **THEN** its status is `PENDING` and its start time is empty unless one was given

### Requirement: Title must not be blank
A task MUST have a title that contains at least one non-whitespace character and is at most 500 characters after surrounding whitespace is removed. The stored title SHALL be the trimmed text.

#### Scenario: Blank title rejected
- **WHEN** a task is created with a title that is empty or only whitespace
- **THEN** creation is rejected and no task exists

#### Scenario: Title is trimmed
- **WHEN** a task is created with the title "  send report  "
- **THEN** its title is "send report"

#### Scenario: Overlong title rejected
- **WHEN** a task is created with a title of 501 characters
- **THEN** creation is rejected

### Requirement: Urgency is required
A task MUST have an urgency. The domain SHALL NOT apply a default; defaulting a missing urgency is the responsibility of the flow that builds the task.

#### Scenario: Missing urgency rejected
- **WHEN** a task is created without an urgency
- **THEN** creation is rejected

### Requirement: Deadline must be strictly in the future
A task MUST have a deadline that is strictly later than the current instant, as given by the application clock. A deadline equal to the current instant or earlier SHALL be rejected. If a start time is given, it MUST NOT be later than the deadline.

#### Scenario: Future deadline accepted
- **WHEN** a task is created at 10:00 with a deadline of 10:00:01
- **THEN** creation succeeds

#### Scenario: Deadline equal to now rejected
- **WHEN** a task is created at 10:00 with a deadline of exactly 10:00
- **THEN** creation is rejected

#### Scenario: Past deadline rejected
- **WHEN** a task is created at 10:00 with a deadline of 09:00
- **THEN** creation is rejected

#### Scenario: Missing deadline rejected
- **WHEN** a task is created without a deadline
- **THEN** creation is rejected

#### Scenario: Start after deadline rejected
- **WHEN** a task is created with a start time later than its deadline
- **THEN** creation is rejected

### Requirement: Validation failures identify the rule, not the content
When task validation fails, the error SHALL state which rule was broken and MUST NOT embed the task's title text.

#### Scenario: Error message
- **WHEN** a task with a blank title is rejected
- **THEN** the error identifies the title rule and contains no user-supplied text
