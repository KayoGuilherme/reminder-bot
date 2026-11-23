# Spec Delta

## Purpose

Defines the user-visible flow that turns a voice note or text message into a saved task: validation of the input, a confirmation step with Confirm and Cancel buttons, short-lived drafts, and idempotent saving.

## ADDED Requirements

### Requirement: Voice and plain text start the flow
The application SHALL treat a voice message, and a text message that does not start with `/`, from the allowed user as the description of a task. A voice message SHALL be transcribed first; a text message SHALL skip transcription and be parsed directly.

#### Scenario: Voice note
- **WHEN** the owner sends a voice note saying "preciso enviar o relatório até as 18h, é urgente" at 10:00 in `app.timezone`
- **THEN** it is transcribed, parsed, and a confirmation message is sent

#### Scenario: Plain text
- **WHEN** the owner sends the text "pay rent tomorrow 9am"
- **THEN** no transcription happens and the text is parsed and a confirmation message is sent

### Requirement: Reject audio that is too long
A voice message longer than `app.voice.max-seconds` (default 60) SHALL be rejected with a message saying the limit, before the audio is downloaded or transcribed. A voice message exactly at the limit SHALL be accepted.

#### Scenario: Too long
- **WHEN** the owner sends a 61-second voice note and the limit is 60
- **THEN** the bot replies that voice notes must be at most 60 seconds, nothing is downloaded or transcribed, and no draft exists

#### Scenario: At the limit
- **WHEN** the owner sends a 60-second voice note and the limit is 60
- **THEN** it is processed normally

### Requirement: A deadline in the future is required
When the parsed deadline is missing, or is not strictly in the future at that moment, the bot SHALL reply asking the owner to resend the task with a time, SHALL NOT create a draft and SHALL NOT save anything.

#### Scenario: No time given
- **WHEN** the owner sends "comprar leite" and the parser finds no deadline
- **THEN** the bot asks to resend including a time and there is no draft

#### Scenario: Time already passed
- **WHEN** the owner sends "enviar o relatório até as 8h" at 10:00 the same day
- **THEN** the bot says the time has already passed, asks to resend with a time, and there is no draft

### Requirement: Missing urgency defaults to medium and says so
When the parsed task has no urgency, the draft SHALL use `MEDIUM`, and the confirmation message MUST state clearly that the urgency was assumed (for example "Medium (assumed)") so the owner can cancel and resend.

#### Scenario: No urgency stated
- **WHEN** the owner sends "pay rent tomorrow 9am" and the parser finds no urgency
- **THEN** the confirmation shows urgency Medium marked as assumed

#### Scenario: Urgency stated
- **WHEN** the owner says it is urgent
- **THEN** the confirmation shows High without an "assumed" marker

### Requirement: Confirmation with Confirm and Cancel
For a valid parse the bot SHALL reply in the same chat with a summary containing the title, the urgency and the deadline rendered in `app.timezone`, with two inline buttons, Confirm and Cancel. It SHALL keep the draft in memory under an id carried in the buttons. Nothing SHALL be saved to the database at this point.

#### Scenario: Summary and buttons
- **WHEN** the voice note "preciso enviar o relatório até as 18h, é urgente" is processed at 10:00 on 2026-11-22 in `America/Fortaleza`
- **THEN** the bot sends a message with the title "Enviar o relatório", urgency High, deadline 2026-11-22 18:00, and the buttons Confirm and Cancel, and the tasks table is still empty

#### Scenario: Several drafts
- **WHEN** the owner sends two task messages before pressing anything
- **THEN** two independent drafts exist and each button set acts only on its own draft

### Requirement: Confirm saves a pending task
Pressing Confirm on a live draft SHALL save exactly one task with status `PENDING`, the chat id of the draft, and the draft's title, urgency, optional start time and deadline, remove the draft, remove the buttons by editing the confirmation message to show it was saved, and acknowledge the button press.

#### Scenario: Confirm
- **WHEN** the owner presses Confirm on the draft for "Enviar o relatório"
- **THEN** the tasks table contains one `PENDING` row with that title, urgency `HIGH`, the owner's chat id and the 18:00 deadline, and the message is updated to say the task was saved

#### Scenario: Deadline passed while waiting
- **WHEN** the owner presses Confirm after the draft's deadline has already passed
- **THEN** nothing is saved, the draft is removed, and the bot says the time has passed and asks to resend with a new time

### Requirement: Cancel discards the draft
Pressing Cancel on a live draft SHALL remove the draft, save nothing, edit the message to show it was canceled, and acknowledge the button press.

#### Scenario: Cancel
- **WHEN** the owner presses Cancel
- **THEN** no task is saved, the draft no longer exists and the message shows it was canceled

### Requirement: Buttons are idempotent and drafts expire
A draft SHALL expire after `app.draft.ttl` (default 10 minutes) and SHALL NOT survive an application restart. Pressing Confirm or Cancel on a draft that was already confirmed, already canceled, expired or never existed SHALL NOT save anything, SHALL tell the owner the request is no longer available, and SHALL acknowledge the press. Two presses of Confirm on the same draft, even concurrent ones, SHALL result in at most one saved task.

#### Scenario: Double confirm
- **WHEN** the owner presses Confirm twice on the same draft
- **THEN** exactly one task exists and the second press is answered with a "no longer available" message

#### Scenario: Confirm after cancel
- **WHEN** the owner presses Cancel and then Confirm on the same draft
- **THEN** no task is saved

#### Scenario: Confirm after the TTL
- **WHEN** the owner presses Confirm 10 minutes and 1 second after the draft was created, with the default TTL
- **THEN** no task is saved and the owner is told the draft expired

#### Scenario: Just inside the TTL
- **WHEN** the owner presses Confirm 9 minutes and 59 seconds after the draft was created
- **THEN** the task is saved

#### Scenario: Concurrent confirms
- **WHEN** two Confirm presses for the same draft are processed at the same time
- **THEN** exactly one task is saved

#### Scenario: Unknown button data
- **WHEN** a button press carries data that is not a Confirm or Cancel for a known draft format
- **THEN** it is acknowledged silently and nothing else happens

### Requirement: Saving failure keeps the draft
When saving on Confirm fails (for example the database is unavailable), the bot SHALL tell the owner that saving failed and to try again, SHALL save nothing, and SHALL leave the draft in place until its TTL so a later Confirm can succeed without creating a duplicate.

#### Scenario: Database down, then back
- **WHEN** Confirm is pressed while saving fails, and pressed again after it works
- **THEN** the first press reports an error and the second saves exactly one task

### Requirement: Friendly failures create no draft
When downloading the audio, transcribing, or parsing fails (including timeouts, an unintelligible or empty transcript, or an invalid model answer), the bot SHALL reply with a short friendly message in the configured language that distinguishes, at least, "could not process the audio / try again" from "could not understand the task / rephrase", and SHALL NOT create a draft or save anything. The message MUST NOT contain technical details, exception text or credentials.

#### Scenario: Transcription service down
- **WHEN** a voice note is sent and the transcription service is unavailable after its retry
- **THEN** the bot replies that it could not process the audio and asks to try again, and no draft exists

#### Scenario: Model answer unusable
- **WHEN** the parser reports an invalid response
- **THEN** the bot asks the owner to rephrase and no draft exists

#### Scenario: Silence
- **WHEN** the transcript is empty
- **THEN** the bot says it could not hear anything intelligible and no draft exists

### Requirement: Routing keeps existing commands working
`/start`, `/help` and unknown slash commands SHALL keep their previous replies; the usage text for `/start` and `/help` SHALL describe how to create a task by voice or text instead of saying that creation is unsupported. Replies SHALL be in the language configured by `app.language`.

#### Scenario: Start
- **WHEN** the owner sends `/start`
- **THEN** the bot replies with a usage message that explains sending a voice note or text with the task, urgency and time

### Requirement: No sensitive data in logs
Logs produced by this flow MUST NOT contain audio, transcripts, task titles or text, API keys or the bot token. Draft and task identifiers and failure kinds MAY be logged.

#### Scenario: Full flow logged
- **WHEN** a task is created from a voice note with logging at DEBUG
- **THEN** the captured logs contain none of the transcript, the title, the API keys or the bot token
