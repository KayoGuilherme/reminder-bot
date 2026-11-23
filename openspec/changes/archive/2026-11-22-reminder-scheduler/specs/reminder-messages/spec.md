# Spec Delta

## Purpose

Defines the wording of reminders: one text per urgency and stage, in English and Brazilian Portuguese, with a tone that matches the urgency and grows firmer with each stage.

## ADDED Requirements

### Requirement: One text per urgency and stage
A reminder text SHALL exist for every urgency and stage the schedule can produce: LOW stage 1; MEDIUM stages 1 and 2; HIGH stages 1, 2 and 3. Each SHALL include the task title and the number of minutes left.

#### Scenario: Every combination has a text
- **WHEN** a reminder is rendered for each urgency and stage combination in both languages
- **THEN** each renders without a missing-text error and contains the task title

#### Scenario: Title and minutes inserted
- **WHEN** the LOW stage-1 text is rendered in English for the title "Send the report" with 10 minutes left
- **THEN** the text contains "Send the report" and "10"

### Requirement: Tone follows urgency
LOW reminders SHALL be friendly and relaxed, MEDIUM reminders firm, and HIGH reminders aggressive, with the shouted emphasis in capitals. Later stages of the same urgency SHALL use the same tone with increasing intensity. As the reference wording, the English stage-1 texts are: LOW "Hey, 10 minutes left for X, whenever you're ready."; MEDIUM "X is due in 10 minutes. Time to start."; HIGH "X is due in 10 MINUTES. Drop everything and do it now."

#### Scenario: Reference English wording
- **WHEN** the stage-1 reminder for the title "X" with 10 minutes left is rendered in English for each urgency
- **THEN** the texts equal the reference wording of that urgency with the title and minutes in place

#### Scenario: Intensity grows with the stage
- **WHEN** the stage 1, 2 and 3 HIGH texts are read in order
- **THEN** each is more forceful than the previous while staying in the aggressive tone

### Requirement: Language follows the configured language
Reminders SHALL be rendered in the language set by `app.language` (`en` or `pt-BR`), and both languages MUST provide the same set of texts with the same inserted values.

#### Scenario: Portuguese reminder
- **WHEN** `app.language` is `pt-BR` and a reminder is rendered
- **THEN** the text is in Brazilian Portuguese and contains the task title and the minutes

#### Scenario: Parity between languages
- **WHEN** the reminder text keys of the English and Portuguese resources are compared
- **THEN** they are identical

### Requirement: Minutes left reflect the real time remaining
The minutes shown SHALL be the time remaining to the deadline at the moment of sending, rounded up to whole minutes and never below 0, so a reminder sent a few seconds late for stage 1 still reads 10 and a reminder for a task created late or already overdue never shows a negative number.

#### Scenario: Slightly late stage 1
- **WHEN** the stage-1 reminder is sent 9 minutes and 40 seconds before the deadline
- **THEN** the minutes shown are 10

#### Scenario: Task created late
- **WHEN** a stage-1 reminder is sent for a task due in 7 minutes
- **THEN** the minutes shown are 7

#### Scenario: Overdue
- **WHEN** a reminder is sent after the deadline
- **THEN** the minutes shown are 0

### Requirement: Task titles are shown literally
The task title SHALL appear in the reminder exactly as stored, without being interpreted as a formatting pattern.

#### Scenario: Title with braces and quotes
- **WHEN** a reminder is rendered for the title `Fix {0} it's "ok"`
- **THEN** the text contains that title unchanged
