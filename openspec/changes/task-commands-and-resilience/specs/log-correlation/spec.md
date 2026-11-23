# Spec Delta

## Purpose

Makes the logs useful for diagnosing one message or one scheduler run in isolation, while guaranteeing the logs never hold the owner's audio, words or credentials.

## ADDED Requirements

### Requirement: Every update and run has a correlation id
Each Telegram update handled, and each scheduler run (reminders and retention), SHALL be given a short correlation id that appears in every log line written while it is being processed, including lines from the external-call steps it triggers, and that is cleared when processing ends so it never leaks into the next one. Two different updates MUST have different ids.

#### Scenario: Id on every line of one update
- **WHEN** a voice note is processed and its transcription fails
- **THEN** all log lines written for that update carry the same correlation id

#### Scenario: Different updates, different ids
- **WHEN** two updates are processed one after the other
- **THEN** their log lines carry different correlation ids

#### Scenario: Id cleared afterwards
- **WHEN** an update has finished and an unrelated log line is written by another thread of work
- **THEN** that line does not carry the finished update's id

#### Scenario: Id on a failure
- **WHEN** handling an update throws an unexpected error
- **THEN** the error log line carries that update's correlation id

#### Scenario: Scheduler run
- **WHEN** the reminder job logs while sending a reminder
- **THEN** its lines carry an id identifying that run

### Requirement: Sensitive content is never logged
The application MUST NOT write to its logs, at any level, audio data, transcripts, the text of a message the owner sent, task titles, API keys, the bot token or database credentials. Logs MAY contain ids (update, task, draft, chat), counts, durations, failure kinds and HTTP status codes. This holds at the default INFO level and at DEBUG.

#### Scenario: Transcript not logged
- **WHEN** a voice note is transcribed to "buy milk tomorrow at 9" and a task is drafted and confirmed
- **THEN** no log line at any level contains that text or any part of the title

#### Scenario: Failure logs carry no content
- **WHEN** an STT or LLM call fails with an error body that echoes the request
- **THEN** the log line states the service, the failure kind and the status code only

#### Scenario: Secrets not logged
- **WHEN** the application starts and handles updates with the bot token and API keys configured
- **THEN** none of those values appears in any log line
