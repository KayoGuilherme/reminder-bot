## MODIFIED Requirements

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
