# Spec Delta

## Purpose

Defines how free text describing a task is turned into a structured, validated task draft by a language model, including the information the model is given and how little of its answer is trusted.

## ADDED Requirements

### Requirement: Structured result from free text
Given the text of a task description, the application SHALL ask an OpenAI-compatible chat-completions model, configured by `app.llm.base-url`, `app.llm.model` and `app.llm.api-key`, for a strict JSON object with the fields `title`, `urgency`, `startAt` and `deadlineAt`, and SHALL return a parsed task with a title and optional urgency, start time and deadline.

#### Scenario: Complete description
- **WHEN** the text "preciso enviar o relatório até as 18h, é urgente" is parsed at 10:00 on a given day in `app.timezone` and the model answers with title "Enviar o relatório", urgency `HIGH` and deadline 18:00 that day
- **THEN** the parsed task has that title, urgency `HIGH`, no start time and a deadline of 18:00 that day in `app.timezone`

#### Scenario: Provider is configurable
- **WHEN** `app.llm.base-url` points at a different OpenAI-compatible provider
- **THEN** the request is sent to that base URL with the configured model and key, and no provider-specific behavior is required

### Requirement: The model is told the current time and the rules
Every request SHALL include the current date, time and UTC offset in `app.timezone`, taken from the application clock, and SHALL instruct the model that: a deadline such as "até as 18h" / "by 6pm" means that time today; relative expressions such as "daqui a 2 horas" / "in 2 hours" are relative to the stated current time; urgency words in Portuguese and English (for example "urgente", "urgent", "importante", "asap" for high; "quando der", "whenever", "sem pressa" for low) map to `LOW`, `MEDIUM` or `HIGH`; fields not stated by the user MUST be null rather than guessed; and the answer MUST be only the JSON object with times as ISO-8601 with offset.

#### Scenario: Prompt carries the current time
- **WHEN** the clock reads 2026-11-22T13:00:00Z and `app.timezone` is `America/Fortaleza` (UTC-3)
- **THEN** the request states the current time as 2026-11-22 10:00 with offset -03:00

#### Scenario: Prompt carries the rules
- **WHEN** a request is built
- **THEN** it contains the same-day rule for "até as 18h", the relative-time rule, the urgency vocabulary and the nulls-instead-of-guessing rule, and the user's text is delivered as data, separate from the instructions

### Requirement: The model's answer is validated in code
The application MUST NOT trust the model's answer. It SHALL accept only a JSON object (an answer wrapped in a markdown code fence SHALL be unwrapped first) and SHALL validate each field: the title must be a non-blank string of at most 500 characters after trimming; urgency is accepted case-insensitively only if it is `LOW`, `MEDIUM` or `HIGH`, otherwise it is treated as absent; start time and deadline are accepted only as valid ISO-8601 date-times (with an offset, or without one, in which case they are read in `app.timezone`), otherwise they are treated as absent; a start time later than the deadline is treated as absent; unknown extra fields are ignored. An answer that is not a JSON object, or whose title is missing or invalid, SHALL be reported as an invalid response.

#### Scenario: Table of answers
- **WHEN** the model answers with each of the following, at the fixed clock of the test
- **THEN** the outcome is as listed

| Model answer | Outcome |
|---|---|
| `{"title":"Pay rent","urgency":"high","startAt":null,"deadlineAt":"2026-11-23T09:00:00-03:00"}` | title "Pay rent", `HIGH`, deadline 2026-11-23 09:00 -03:00 |
| the same wrapped in a ```json fence | same as above |
| `{"title":"Pay rent","urgency":"URGENT","deadlineAt":"2026-11-23T09:00:00-03:00"}` | urgency absent, deadline kept |
| `{"title":"Pay rent","urgency":"LOW","deadlineAt":null}` | deadline absent |
| `{"title":"Pay rent","urgency":"LOW","deadlineAt":"tomorrow morning"}` | deadline absent |
| `{"title":"Pay rent","deadlineAt":"2026-11-23T09:00:00"}` | deadline read as 09:00 in `app.timezone` |
| `{"title":"Pay rent","startAt":"2026-11-24T09:00:00-03:00","deadlineAt":"2026-11-23T09:00:00-03:00"}` | start absent, deadline kept |
| `{"title":"   ","deadlineAt":"2026-11-23T09:00:00-03:00"}` | invalid response |
| `{"urgency":"LOW"}` | invalid response |
| `Sure! Here is your task: ...` | invalid response |
| `[]` | invalid response |

#### Scenario: Past or missing deadline is not judged here
- **WHEN** the model answers with a deadline that is already in the past
- **THEN** the parsed task still carries that deadline; deciding what to do about it belongs to the task-creation flow

### Requirement: Timeouts, a single retry and failure kinds
The call to the model SHALL use configured connect and read timeouts, SHALL retry a transient failure (connection error, timeout, HTTP 429 or 5xx) exactly once, and SHALL NOT retry other failures. The step SHALL report one of: unavailable/timeout, rejected by the provider, or invalid response, so that callers can reply sensibly.

#### Scenario: Transient failure then success
- **WHEN** the first request fails with HTTP 502 and the second returns a valid answer
- **THEN** the parsed task is returned after exactly two requests

#### Scenario: Provider rejects the key
- **WHEN** the provider answers 401
- **THEN** one request is made and the step reports a provider rejection

### Requirement: No sensitive data in logs
Logs and error messages produced by parsing MUST NOT contain the task text, the model's answer or the API key. A failure SHALL be logged with the failure kind and the HTTP status or exception type only.

#### Scenario: Invalid answer logged safely
- **WHEN** the model answers with text that is not valid JSON
- **THEN** the log entry reports an invalid response and contains neither the user's text nor the model's text
