# reminder-actions Specification

## Purpose
Lets the owner act on a reminder straight from the chat: mark the task done or snooze it by 10 minutes with inline buttons, safely even when buttons are pressed twice, are stale, or point at a task that no longer exists.

## Requirements

### Requirement: Reminder messages carry Done and Snooze buttons
Every reminder message, whatever the urgency or stage (including HIGH overdue and final messages), SHALL be sent with two inline buttons: Done and Snooze 10 min, in the configured language. The callback data of each button SHALL identify the action and the task id and MUST fit Telegram's 64-byte callback data limit.

#### Scenario: Buttons on a scheduled reminder
- **WHEN** a stage-1 reminder is sent for a task
- **THEN** the message has a Done button and a Snooze 10 min button whose callback data identify that task

#### Scenario: Buttons on overdue and final reminders
- **WHEN** a HIGH overdue reminder or the final "stop reminding" message is sent
- **THEN** it carries the same two buttons

#### Scenario: Callback data fits the limit
- **WHEN** the callback data of either button is measured in UTF-8 bytes
- **THEN** it is at most 64 bytes

### Requirement: Every button press is acknowledged
Each callback query SHALL be answered, before or regardless of the outcome of the action, so the client stops showing the button's progress indicator. This applies to valid presses, stale presses, presses on unknown tasks and callback data that cannot be understood.

#### Scenario: Valid press
- **WHEN** the owner presses Done on a pending task's reminder
- **THEN** the callback query is answered and the task is marked done

#### Scenario: Unknown or malformed data
- **WHEN** a callback arrives with data that is not a recognized action and task id
- **THEN** the callback query is answered and nothing else happens

### Requirement: The task must exist and belong to the chat
An action SHALL apply only if the task identified by the callback exists and belongs to the chat the button was pressed in. Otherwise nothing MUST change and the user receives a polite reply that the task is no longer available; the reply MUST NOT reveal whether the task exists in another chat.

#### Scenario: Unknown task
- **WHEN** Done is pressed for a task id that does not exist
- **THEN** no task changes and a polite "task not available" reply is sent

#### Scenario: Task of another chat
- **WHEN** a button for a task belonging to chat A is pressed in chat B
- **THEN** the task is unchanged and the same polite reply as for an unknown task is sent in chat B

### Requirement: Done completes the task
Pressing Done on a PENDING task SHALL set its status to DONE and edit the reminder message into a confirmation that no longer has buttons. A DONE task never receives another reminder, including one already in flight at the next run.

#### Scenario: Done on a pending task
- **WHEN** the owner presses Done on the reminder of a pending HIGH task that is being nagged
- **THEN** the task is DONE, the message becomes a done confirmation without buttons, and no further reminder is sent for it

#### Scenario: Done pressed twice
- **WHEN** Done is pressed again, for instance on an older reminder message of the same task
- **THEN** the task stays DONE and the user receives a polite reply that it was already done

#### Scenario: Done on a canceled task
- **WHEN** Done is pressed for a CANCELED task
- **THEN** its status is unchanged and the user receives a polite reply that the task is no longer active

### Requirement: Snooze moves the deadline and restarts the reminder cycle
Pressing Snooze 10 min on a PENDING task that has had a reminder in its current cycle (last reminder stage above 0) SHALL set its deadline to the later of its current deadline and now, plus 10 minutes, reset its last reminder stage to 0, and edit the reminder message into a confirmation that the task was snoozed and states the new deadline in the display timezone. The task's start time and urgency MUST NOT change.

Because the stage restarts at 0 and the new deadline is at most 10 minutes ahead when the old one had passed, snoozing a task past its deadline makes the next scheduler run send an immediate "10 minutes left" (stage 1) reminder. This is intended: the cycle restarts and the stage-2 and stage-3 reminders follow at 5 minutes and at the new deadline.

#### Scenario: Snooze before the deadline
- **WHEN** a task due in 4 minutes with stage 1 sent is snoozed
- **THEN** its deadline moves by 10 minutes to 14 minutes from now, its stage becomes 0 and the message is edited to say it was snoozed until the new deadline

#### Scenario: Snooze after the deadline
- **WHEN** a HIGH task overdue by 20 minutes, with an overdue reminder sent, is snoozed
- **THEN** its deadline becomes now plus 10 minutes, its stage becomes 0, and the next run sends a stage-1 "10 minutes left" reminder

#### Scenario: Snooze restarts the full cycle
- **WHEN** a HIGH task is snoozed after its deadline and runs then happen until 1 minute after the new deadline with no further presses
- **THEN** stages 1, 2 and 3 are sent at about now, now plus 5 minutes and now plus 10 minutes, followed by overdue reminders from the repeat interval onward

#### Scenario: Snooze pressed twice
- **WHEN** Snooze is pressed again before any reminder of the restarted cycle was sent (stage still 0)
- **THEN** the deadline is not moved a second time and the user receives a polite reply that the task is already snoozed

#### Scenario: Snooze on a finished task
- **WHEN** Snooze is pressed for a DONE or CANCELED task
- **THEN** the task is unchanged and the user receives a polite reply that the task is no longer active

### Requirement: Failed message edits fall back to a new message
If editing the reminder message fails after an action took effect, the confirmation SHALL be sent as a new message instead, and the action MUST NOT be applied a second time.

#### Scenario: Message cannot be edited
- **WHEN** the edit after Done is rejected
- **THEN** the confirmation is sent as a new message and the task is DONE once

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
