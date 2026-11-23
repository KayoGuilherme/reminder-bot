# task-retention Specification

## Purpose

Keeps the database small on a long-running single machine by automatically removing tasks that were finished or canceled long ago, without ever touching tasks that are still active.

## Requirements

### Requirement: Old finished tasks are deleted daily
Once a day the application SHALL permanently delete every task whose status is `DONE` or `CANCELED` and whose last change is older than `app.retention.days` (default 30). Age counts from the moment the task was last changed, which for a finished task is when it was marked done or canceled. A `PENDING` task MUST never be deleted by this job, however old, and a finished task at or inside the retention window MUST be kept.

#### Scenario: Old done task removed
- **WHEN** the job runs and a task has been `DONE` for 31 days with a 30-day retention
- **THEN** the task is deleted

#### Scenario: Old canceled task removed
- **WHEN** the job runs and a task has been `CANCELED` for 45 days
- **THEN** the task is deleted

#### Scenario: Recent finished task kept
- **WHEN** the job runs and a task was marked `DONE` 29 days ago
- **THEN** the task is kept

#### Scenario: Pending tasks never deleted
- **WHEN** the job runs and a `PENDING` task was created 200 days ago and is still unfinished
- **THEN** the task is kept

#### Scenario: Retention configured
- **WHEN** `app.retention.days` is 7 and a `CANCELED` task is 8 days old
- **THEN** the task is deleted

### Requirement: Retention can be disabled
`app.retention.enabled` (default true) SHALL turn the job off entirely: when false, no cleanup is scheduled and no task is ever deleted automatically. `app.retention.days` MUST be at least 1; a smaller value stops startup with an error naming the setting.

#### Scenario: Disabled
- **WHEN** the application runs with `app.retention.enabled=false` for longer than a day
- **THEN** no task is deleted, however old

#### Scenario: Invalid days
- **WHEN** the application starts with `app.retention.days=0`
- **THEN** startup fails with an error naming `app.retention.days`

### Requirement: The cleanup is scheduled daily and reported
The job SHALL run once per day at a fixed local time in `app.timezone` (03:00), using the application's clock, and MUST NOT run again if the application restarts later the same day. Each run SHALL log at INFO how many tasks it deleted, without task titles or other task content. A failure in one run SHALL be logged and MUST NOT stop the job from running the next day or affect the reminder job.

#### Scenario: Count logged
- **WHEN** a run deletes 3 tasks
- **THEN** an INFO log entry states that 3 tasks were deleted and contains no task titles

#### Scenario: Failure does not stop the job
- **WHEN** the database is unavailable during a run
- **THEN** an error is logged, the reminder job keeps running, and the next day's run happens normally
