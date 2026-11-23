## MODIFIED Requirements

### Requirement: Reminder messages carry Done and Snooze buttons
Every reminder message, whatever the urgency or stage (including HIGH overdue and final messages), SHALL be sent with two inline buttons: Done and Snooze 10 min, in the language of the task. The callback data of each button SHALL identify the action and the task id and MUST fit Telegram's 64-byte callback data limit.

#### Scenario: Buttons on a scheduled reminder
- **WHEN** a stage-1 reminder is sent for a task
- **THEN** the message has a Done button and a Snooze 10 min button whose callback data identify that task

#### Scenario: Buttons follow the task language
- **WHEN** a reminder is sent for a pt-BR task
- **THEN** the buttons read "Feito ✅" and "Adiar 10 min"

#### Scenario: Buttons on overdue and final reminders
- **WHEN** a HIGH overdue reminder or the final "stop reminding" message is sent
- **THEN** it carries the same two buttons

#### Scenario: Callback data fits the limit
- **WHEN** the callback data of either button is measured in UTF-8 bytes
- **THEN** it is at most 64 bytes

### Requirement: Snooze moves the deadline and restarts the reminder cycle
Pressing Snooze 10 min on a PENDING task that has had a reminder in its current cycle (last reminder stage above 0) SHALL set its deadline to the later of its current deadline and now, plus 10 minutes, reset its last reminder stage to 0, and edit the reminder message into a confirmation that the task was snoozed and states the new deadline in the display timezone, formatted with the date pattern of the task's language. The task's start time and urgency MUST NOT change.

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
- **THEN** the deadline is not moved a second time and the user receives a polite reply that the task is already snoozed, in the task's language

#### Scenario: Snooze on a finished task
- **WHEN** Snooze is pressed for a DONE or CANCELED task
- **THEN** the task is unchanged and the user receives a polite reply that the task is no longer active, in the task's language
