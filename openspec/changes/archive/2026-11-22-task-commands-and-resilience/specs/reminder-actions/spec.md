# Spec Delta

## ADDED Requirements

### Requirement: Cancel drops the task
Pressing Cancel on a `PENDING` task SHALL set its status to `CANCELED` and edit the message the button was on into a confirmation that no longer has buttons. A canceled task never receives another reminder, including one already due at the next run. The callback data of Cancel MUST identify the action and the task id, MUST be distinct from the data of the task-creation Confirm/Cancel buttons, and MUST fit Telegram's 64-byte limit. Like every button press it is always acknowledged.

#### Scenario: Cancel a pending task
- **WHEN** the owner presses Cancel on the listed message of a pending HIGH task that is being nagged
- **THEN** the task is `CANCELED`, the message becomes a canceled confirmation without buttons, and no further reminder is sent for it

#### Scenario: Cancel pressed twice
- **WHEN** Cancel is pressed again on an older message of the same task
- **THEN** the task stays `CANCELED` and the owner receives a polite reply that it was already canceled

#### Scenario: Cancel on a done task
- **WHEN** Cancel is pressed for a `DONE` task
- **THEN** its status is unchanged and the owner receives a polite reply that the task is no longer active

#### Scenario: Unknown task or another chat
- **WHEN** Cancel is pressed for a task id that does not exist, or for a task of another chat
- **THEN** nothing changes and the same polite "task not available" reply is sent in both cases

#### Scenario: Message cannot be edited
- **WHEN** the edit after Cancel is rejected
- **THEN** the confirmation is sent as a new message and the task is canceled once

#### Scenario: Concurrent Done and Cancel
- **WHEN** Done and Cancel are pressed for the same pending task at nearly the same time
- **THEN** exactly one of them takes effect and the other receives the polite "no longer active" or "already done" reply
