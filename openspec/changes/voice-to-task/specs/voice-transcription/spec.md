# Spec Delta

## Purpose

Defines how a Telegram voice message becomes text: fetching the audio and transcribing it with a speech-to-text service, and how failures of either step are reported without leaking audio or credentials.

## ADDED Requirements

### Requirement: Fetch the voice file
Given the file id of a voice message, the application SHALL obtain the audio bytes from Telegram. The audio SHALL be handled in memory only: it MUST NOT be written to disk and MUST NOT be logged.

#### Scenario: Voice file available
- **WHEN** the audio of a voice message is requested by its file id
- **THEN** the OGG/Opus bytes are returned

#### Scenario: Voice file unavailable
- **WHEN** Telegram cannot provide the file (unknown id, file too large, network failure)
- **THEN** the step reports a failure without bytes and nothing is written to disk

### Requirement: Transcribe with Groq Whisper
The application SHALL transcribe the audio by sending it to Groq's OpenAI-compatible audio transcription endpoint with the model `whisper-large-v3-turbo`, authenticated with the configured Groq API key and with the language taken from `app.stt.language` (default `pt`). The audio SHALL be sent as an Ogg/Opus file upload. The result SHALL be the transcribed text, trimmed.

#### Scenario: Successful transcription
- **WHEN** a voice note is transcribed and the service answers with the text "preciso enviar o relatório até as 18h"
- **THEN** that text is returned, and the request carried model `whisper-large-v3-turbo`, language `pt` and the audio as a file part

#### Scenario: Language override
- **WHEN** `app.stt.language` is set to `en`
- **THEN** the request carries language `en`

#### Scenario: Empty transcript
- **WHEN** the service answers successfully but the text is empty or only whitespace
- **THEN** the step reports a failure (nothing intelligible was heard) instead of returning an empty text

### Requirement: Timeouts and a single retry
Calls to Telegram's file download and to the transcription service SHALL use configured connect and read timeouts. A transient failure (connection error, timeout, HTTP 429 or 5xx) SHALL be retried exactly once; other failures (for example HTTP 400, 401, 403) SHALL NOT be retried. After the allowed attempts the step SHALL report a failure rather than waiting indefinitely.

#### Scenario: Transient failure then success
- **WHEN** the first transcription request fails with HTTP 503 and the second succeeds
- **THEN** the text is returned and exactly two requests were made

#### Scenario: Persistent transient failure
- **WHEN** both attempts time out
- **THEN** the step reports a timeout/unavailable failure after exactly two requests

#### Scenario: Non-transient failure
- **WHEN** the service answers 401 (bad key)
- **THEN** one request is made and the step reports a failure

### Requirement: No sensitive data in logs
Logs and error messages produced by transcription and download MUST NOT contain the audio, the transcript, the Groq API key or the Telegram bot token. A failure SHALL be logged with the step, the failure kind and the HTTP status or exception type only.

#### Scenario: Failed request logged safely
- **WHEN** a transcription request fails with HTTP 500
- **THEN** the log entry names the step and the status and does not contain the API key, the bot token or any text from the audio
