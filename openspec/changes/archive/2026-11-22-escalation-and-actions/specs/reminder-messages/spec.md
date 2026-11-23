# Spec Delta

## MODIFIED Requirements

### Requirement: One text per urgency and stage
A reminder text SHALL exist for every urgency and stage the schedule can produce: LOW stage 1; MEDIUM stages 1 and 2; HIGH stages 1, 2 and 3; HIGH overdue reminders (stages above 3 up to the cap), each in one of at least three intensity levels that grow with the repeat number (repeats 1 to 3, 4 to 6, and 7 and above, the last level also used for any higher repeat number); and the HIGH final stop message. Scheduled texts SHALL include the task title and the number of minutes left; overdue and final texts SHALL include the task title and the number of whole minutes overdue.

#### Scenario: Every combination has a text
- **WHEN** a reminder is rendered for each urgency and stage combination, for repeats 1 to 12 and for the final message, in both languages
- **THEN** each renders without a missing-text error and contains the task title

#### Scenario: Title and minutes inserted
- **WHEN** the LOW stage-1 text is rendered in English for the title "Send the report" with 10 minutes left
- **THEN** the text contains "Send the report" and "10"

#### Scenario: Repeat number beyond the defined levels
- **WHEN** an overdue reminder is rendered for repeat 25 because `app.reminder.max-repeats` was raised
- **THEN** it uses the most intense overdue text without a missing-text error

#### Scenario: Overdue minutes
- **WHEN** an overdue reminder is rendered 7 minutes and 30 seconds after the deadline
- **THEN** it shows 7 minutes overdue

### Requirement: Tone follows urgency
LOW reminders SHALL be friendly and relaxed, MEDIUM reminders firm, and HIGH reminders aggressive, with the shouted emphasis in capitals. Later stages of the same urgency SHALL use the same tone with increasing intensity, and HIGH overdue reminders keep growing more aggressive as the repeat number rises. The final HIGH message SHALL tell the user the bot is stopping its reminders. As the reference wording, the English stage-1 texts are: LOW "Hey, 10 minutes left for X, whenever you're ready."; MEDIUM "X is due in 10 minutes. Time to start."; HIGH "X is due in 10 MINUTES. Drop everything and do it now."

#### Scenario: Reference English wording
- **WHEN** the stage-1 reminder for the title "X" with 10 minutes left is rendered in English for each urgency
- **THEN** the texts equal the reference wording of that urgency with the title and minutes in place

#### Scenario: Intensity grows with the stage
- **WHEN** the stage 1, 2 and 3 HIGH texts are read in order
- **THEN** each is more forceful than the previous while staying in the aggressive tone

#### Scenario: Overdue intensity grows with the repeat
- **WHEN** the HIGH overdue texts for repeat 1, repeat 4 and repeat 7 are read in order
- **THEN** each is more forceful than the previous while staying in the aggressive tone

#### Scenario: Final message announces the stop
- **WHEN** the final HIGH message is rendered in English
- **THEN** it says the bot will stop reminding the user

### Requirement: Minutes left reflect the real time remaining
For scheduled stages, the minutes shown SHALL be the time remaining to the deadline at the moment of sending, rounded up to whole minutes and never below 0, so a reminder sent a few seconds late for stage 1 still reads 10 and a reminder for a task created late or already overdue never shows a negative number. Overdue and final messages are shown as the whole minutes elapsed since the deadline, rounded down.

#### Scenario: Slightly late stage 1
- **WHEN** the stage-1 reminder is sent 9 minutes and 40 seconds before the deadline
- **THEN** the minutes shown are 10

#### Scenario: Task created late
- **WHEN** a stage-1 reminder is sent for a task due in 7 minutes
- **THEN** the minutes shown are 7

#### Scenario: Overdue
- **WHEN** a scheduled stage-3 reminder is sent after the deadline
- **THEN** the minutes shown are 0

## ADDED Requirements

### Requirement: Button labels and action confirmations in both languages
The Done and Snooze 10 min button labels, the confirmation texts shown after Done and after Snooze, and the polite replies for unknown, finished, already-done and already-snoozed tasks SHALL exist in both English and Brazilian Portuguese, with the same keys and inserted values in both. The task title in these texts SHALL be shown literally.

#### Scenario: Parity between languages
- **WHEN** the button, confirmation and reply text keys of the English and Portuguese resources are compared
- **THEN** they are identical

#### Scenario: Snoozed confirmation
- **WHEN** the snoozed confirmation is rendered for a task title and a new deadline
- **THEN** it contains the title and the new deadline in the display timezone
