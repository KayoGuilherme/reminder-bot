# mcp-task-tools Specification

## Purpose
Lets an AI assistant, connected through MCP, read and manage the owner's tasks with the same rules the Telegram bot applies, so a task made or finished in one place is the same task everywhere and keeps being reminded about in Telegram.

## Requirements

### Requirement: The server publishes five described tools
The MCP server SHALL publish exactly the tools `list_pending_tasks`, `create_task`, `complete_task`, `cancel_task` and `snooze_task`. Each tool MUST have a description that tells an AI when to use it and an input schema that names every argument, its type, whether it is required and the accepted values and formats.

#### Scenario: Tools are listed
- **WHEN** a client lists the tools
- **THEN** it receives those five tools, each with a non-empty description and an input schema

#### Scenario: Required arguments are marked
- **WHEN** the schema of `create_task` is read
- **THEN** `title` and `deadline` are required and `urgency`, `startAt` and `language` are optional

### Requirement: Tasks are returned in one readable shape
Every tool that returns a task SHALL return its id, title, urgency, status, language, start time (when set), the deadline in UTC (ISO-8601 with `Z`), the same deadline in the display timezone `app.timezone` (ISO-8601 with its offset), and whether the task is overdue (its deadline is not after now and its status is `PENDING`).

#### Scenario: Deadline in both zones
- **WHEN** a task is due at 2026-11-22T21:00:00Z and `app.timezone` is `America/Fortaleza`
- **THEN** its result shows `2026-11-22T21:00:00Z` as the UTC deadline and `2026-11-22T18:00:00-03:00` as the local one

#### Scenario: Overdue flag
- **WHEN** a pending task's deadline was 30 minutes ago
- **THEN** its result marks it overdue, and a pending task due in the future is not marked overdue

### Requirement: list_pending_tasks returns the owner's pending tasks
`list_pending_tasks` SHALL return the owner's `PENDING` tasks ordered by deadline with the earliest first (a stable order for equal deadlines), together with the total number of pending tasks. An optional `limit` between 1 and 50 (default 20) caps how many are returned; a value outside that range MUST be refused with the reason `INVALID_LIMIT`. `DONE` and `CANCELED` tasks MUST NOT appear.

#### Scenario: Ordered by deadline
- **WHEN** the owner has pending tasks due at 18:00, 09:00 and 12:30
- **THEN** the result lists them as 09:00, 12:30, 18:00

#### Scenario: Finished tasks are hidden
- **WHEN** the owner has one `PENDING`, one `DONE` and one `CANCELED` task
- **THEN** only the `PENDING` task is returned

#### Scenario: No tasks
- **WHEN** the owner has no pending task
- **THEN** the result is an empty list with a total of 0, not an error

#### Scenario: Limit smaller than the total
- **WHEN** the owner has 30 pending tasks and `limit` is 5
- **THEN** 5 tasks are returned and the total is 30

#### Scenario: Limit out of range
- **WHEN** `limit` is 0 or 51
- **THEN** the call fails with the reason `INVALID_LIMIT`

### Requirement: create_task saves a validated task directly
`create_task` SHALL create a `PENDING` task for the owner and return it, without the Telegram Confirm step: the call itself is the confirmation. It accepts a required `title`, a required `deadline`, and optional `urgency` (`LOW`, `MEDIUM` or `HIGH`, default `MEDIUM`), `startAt` and `language` (`en` or `pt-BR`, default `app.language`). A date-time argument is accepted either with an offset or `Z`, or without one, in which case it is read in `app.timezone`. The task MUST obey the same rules as a task confirmed in Telegram: the title is trimmed, not blank and at most 500 characters, the deadline is present and in the future, and the start is not after the deadline. When a rule fails, nothing MUST be saved and the call fails with the reason naming the rule.

#### Scenario: Minimal task
- **WHEN** a client calls `create_task` with a title and a future deadline
- **THEN** a `PENDING` task with urgency `MEDIUM` and the default language is saved and returned

#### Scenario: Deadline without an offset
- **WHEN** `deadline` is `2026-11-25T18:00:00`, `app.timezone` is `America/Fortaleza`
- **THEN** the task's deadline is 2026-11-25T21:00:00Z

#### Scenario: Deadline in the past
- **WHEN** the deadline is not after now
- **THEN** nothing is saved and the call fails with `DEADLINE_NOT_IN_FUTURE`

#### Scenario: Missing deadline
- **WHEN** `deadline` is absent
- **THEN** nothing is saved and the call fails with `DEADLINE_REQUIRED`

#### Scenario: Blank or oversized title
- **WHEN** the title is blank, or longer than 500 characters
- **THEN** nothing is saved and the call fails with `TITLE_REQUIRED` or `TITLE_TOO_LONG`

#### Scenario: Start after deadline
- **WHEN** `startAt` is after `deadline`
- **THEN** nothing is saved and the call fails with `START_AFTER_DEADLINE`

#### Scenario: Unreadable date-time
- **WHEN** `deadline` is `tomorrow evening`
- **THEN** nothing is saved and the call fails with `INVALID_DATETIME`

#### Scenario: Unsupported language
- **WHEN** `language` is `fr`
- **THEN** nothing is saved and the call fails with `LANGUAGE_UNSUPPORTED`

#### Scenario: Reminded like any other task
- **WHEN** a task created through `create_task` reaches its reminder stages
- **THEN** the scheduler sends the reminders to the owner's Telegram with the same stages as for its urgency, and the Done and Snooze buttons work on them

### Requirement: complete_task and cancel_task finish a pending task
`complete_task` SHALL mark a `PENDING` task of the owner `DONE`, and `cancel_task` SHALL mark it `CANCELED`; both return the task. A finished task MUST never be reminded again. Repeating the same action on a task already in that state SHALL succeed and change nothing. Applying the other action to a finished task MUST fail with `TASK_NOT_PENDING` and change nothing. When a completion and a cancellation race, exactly one wins and the other receives `TASK_NOT_PENDING`.

#### Scenario: Complete a pending task
- **WHEN** `complete_task` is called for a pending task
- **THEN** the task becomes `DONE` and no reminder is sent for it afterwards

#### Scenario: Same action twice
- **WHEN** `complete_task` is called twice for the same task
- **THEN** both calls succeed and the task stays `DONE`

#### Scenario: Opposite action on a finished task
- **WHEN** `cancel_task` is called for a task that is already `DONE`
- **THEN** the call fails with `TASK_NOT_PENDING` and the task stays `DONE`

#### Scenario: Racing finishes
- **WHEN** `complete_task` and `cancel_task` are called at the same time for a pending task
- **THEN** exactly one of them wins and the task ends in a single final status

### Requirement: snooze_task postpones a reminded task by 10 minutes
`snooze_task` SHALL apply the same rule as the Snooze button: on a `PENDING` task that has already had a reminder in its current cycle, the deadline becomes the later of its current deadline and now, plus 10 minutes, the reminder stage restarts at 0, and urgency and start time do not change; the updated task is returned. A task that has had no reminder yet MUST be refused with `NOT_SNOOZABLE`, and a task that is not `PENDING` with `TASK_NOT_PENDING`.

#### Scenario: Snooze a reminded task
- **WHEN** a task due in 4 minutes with stage 1 sent is snoozed
- **THEN** its deadline moves 10 minutes ahead of the later of its deadline and now and its stage becomes 0

#### Scenario: Not reminded yet
- **WHEN** `snooze_task` is called for a pending task whose last reminder stage is 0
- **THEN** the call fails with `NOT_SNOOZABLE` and the deadline does not change

#### Scenario: Finished task
- **WHEN** `snooze_task` is called for a `DONE` task
- **THEN** the call fails with `TASK_NOT_PENDING`

### Requirement: Unknown and foreign tasks look the same
A tool that receives a task id SHALL fail with `TASK_NOT_FOUND` when the id is not a valid UUID, does not exist, or belongs to another chat. The failure MUST NOT reveal which of these it was.

#### Scenario: Unknown id
- **WHEN** `complete_task` receives an id that no task has
- **THEN** the call fails with `TASK_NOT_FOUND`

#### Scenario: Another chat's task
- **WHEN** the id belongs to a task of another chat
- **THEN** the call fails with `TASK_NOT_FOUND` and the task is unchanged

#### Scenario: Not a UUID
- **WHEN** the id is `abc`
- **THEN** the call fails with `TASK_NOT_FOUND`

### Requirement: Failures are tool errors with a stable reason
A rejected call SHALL return a tool error, flagged as an error in the MCP result, whose text starts with one of the reason codes above and gives a short human sentence. It MUST NOT contain a stack trace, a class name or database detail. An unexpected internal failure MUST return the reason `INTERNAL_ERROR` and leave no half-saved task.

#### Scenario: Reason code present
- **WHEN** any call is refused by a rule
- **THEN** the result is marked as an error and its text starts with the reason code

#### Scenario: Database unavailable
- **WHEN** the database cannot be reached during a call
- **THEN** the result is an error with the reason `INTERNAL_ERROR` and no internal detail
