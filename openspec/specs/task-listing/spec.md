# task-listing Specification

## Purpose

Lets the owner see every task still waiting to be done, most urgent deadline first, and finish or drop any of them straight from the list.

## Requirements

### Requirement: The /tasks command lists pending tasks
When the owner sends `/tasks` (also `/tasks@botname`, in any letter case), the bot SHALL reply with the owner's `PENDING` tasks, ordered by deadline with the earliest first and, for equal deadlines, in a stable order. Tasks that are `DONE` or `CANCELED`, and tasks of any other chat, MUST NOT appear. The command is available only to the allowed user, like every other command.

#### Scenario: Tasks ordered by deadline
- **WHEN** the owner has pending tasks due at 18:00, 09:00 and 12:30 and sends `/tasks`
- **THEN** the tasks are presented in the order 09:00, 12:30, 18:00

#### Scenario: Finished tasks are hidden
- **WHEN** the owner has one `PENDING`, one `DONE` and one `CANCELED` task and sends `/tasks`
- **THEN** only the `PENDING` task is presented

#### Scenario: Overdue tasks come first
- **WHEN** a pending task is already past its deadline and another is due tomorrow
- **THEN** the overdue task is presented first

#### Scenario: Other chats are not listed
- **WHEN** a pending task belongs to a different chat than the one that sent `/tasks`
- **THEN** it is not presented

### Requirement: Each listed task is readable at a glance
Each task SHALL be presented as its own message showing its title, its deadline rendered in `app.timezone` (not in UTC) with the date pattern of the task's language, and an urgency marker that distinguishes LOW, MEDIUM and HIGH and is labelled in the task's language. A task whose deadline has already passed SHALL additionally be flagged as overdue, in the task's language. Task titles are shown as stored, with no formatting interpreted from them.

#### Scenario: Deadline in the display timezone
- **WHEN** a task is due at 2026-11-22T21:00:00Z and `app.timezone` is `America/Fortaleza`
- **THEN** its message shows the deadline as 2026-11-22 18:00 for an `en` task, and as 22/11/2026 18:00 for a `pt-BR` task

#### Scenario: Urgency marker
- **WHEN** a HIGH task and a LOW task are listed
- **THEN** their messages show different urgency markers and the HIGH one is the more prominent

#### Scenario: Overdue flag
- **WHEN** a task's deadline is 30 minutes in the past
- **THEN** its message is flagged as overdue in the task's language, and a task due in the future is not

#### Scenario: Title with markup characters
- **WHEN** a task title contains characters such as `<`, `*` or `_`
- **THEN** the message is delivered with the title text unchanged

### Requirement: Done and Cancel buttons on every listed task
Each task message SHALL carry two inline buttons, Done and Cancel, in the language of the task. The callback data of each MUST identify the action and the task id and fit Telegram's 64-byte limit. Done behaves exactly as the Done button of a reminder.

#### Scenario: Buttons present
- **WHEN** `/tasks` lists a task
- **THEN** its message has a Done button and a Cancel button whose callback data identify that task

#### Scenario: Buttons follow the task language
- **WHEN** `/tasks` lists a pt-BR task
- **THEN** its buttons read "Feito ✅" and "Cancelar ❌"

#### Scenario: Done from the list
- **WHEN** the owner presses Done on a listed pending task
- **THEN** the task becomes `DONE`, the message is replaced by a done confirmation without buttons, and no reminder is sent for it afterwards

#### Scenario: Cancel from the list
- **WHEN** the owner presses Cancel on a listed pending task
- **THEN** the task becomes `CANCELED`, the message is replaced by a canceled confirmation without buttons, and no reminder is sent for it afterwards

### Requirement: Empty list
When the owner has no pending tasks, `/tasks` SHALL reply with a single friendly message saying so, in the configured language, and no task messages.

#### Scenario: Nothing pending
- **WHEN** the owner has no `PENDING` task and sends `/tasks`
- **THEN** one message such as "You have no pending tasks 🎉" is sent and nothing else

#### Scenario: All finished
- **WHEN** every task of the owner is `DONE` or `CANCELED`
- **THEN** the same friendly message is sent

### Requirement: A long list is bounded
`/tasks` SHALL present at most 20 tasks per call, the 20 with the earliest deadlines. When more are pending, a final message SHALL state how many more there are, so the owner knows the list is incomplete. Acting on a task and sending `/tasks` again reveals the next ones.

#### Scenario: More than the limit
- **WHEN** the owner has 25 pending tasks and sends `/tasks`
- **THEN** 20 task messages are sent, in deadline order, followed by a message saying 5 more are pending

#### Scenario: Exactly the limit
- **WHEN** the owner has exactly 20 pending tasks
- **THEN** 20 task messages are sent and no "more" message
