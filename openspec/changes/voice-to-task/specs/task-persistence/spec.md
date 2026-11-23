# Spec Delta

## Purpose

Defines how tasks are stored in and read back from the database, so a confirmed task survives restarts and can be found by later features such as reminders.

## ADDED Requirements

### Requirement: Save a task
The application SHALL persist a task as one row in the `tasks` table containing its id, chat id, title, urgency, status, start time, deadline, reminder stage (0 for a new task), and creation and update timestamps. Timestamps SHALL be stored as absolute instants (UTC internally).

#### Scenario: Pending task stored
- **WHEN** a new task with chat id 42, title "send report", urgency `HIGH` and a deadline is saved
- **THEN** the database holds exactly one row for it with status `PENDING`, urgency `HIGH`, chat id 42, reminder stage 0 and the same deadline instant

#### Scenario: Optional start time
- **WHEN** a task without a start time is saved
- **THEN** its start time column is null

### Requirement: Read a task back
The application SHALL be able to load a stored task by id with all its fields intact, and SHALL report that nothing was found for an unknown id.

#### Scenario: Round trip
- **WHEN** a task is saved and then loaded by its id
- **THEN** the loaded task equals the saved one in every field

#### Scenario: Unknown id
- **WHEN** a task is loaded by an id that was never saved
- **THEN** the result is empty

### Requirement: A task id is stored at most once
Saving a task whose id already exists SHALL NOT create a second row and SHALL NOT silently overwrite the existing one; it SHALL fail.

#### Scenario: Same id saved twice
- **WHEN** a task is saved and then a task with the same id is saved again
- **THEN** the second save fails and the table still contains one row for that id, unchanged

### Requirement: Existing schema is used as is
Persisting tasks SHALL work against the schema created by the existing migration V1 with schema validation enabled, without altering any table, column or constraint.

#### Scenario: Startup validation
- **WHEN** the application starts against a database migrated only to V1
- **THEN** startup succeeds with the task persistence in place
