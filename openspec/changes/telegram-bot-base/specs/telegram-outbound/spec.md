# Spec Delta

## Purpose

Defines the outbound messaging contract the rest of the application uses to talk to the user, and how it behaves when Telegram rate-limits or fails, so callers never deal with transport errors.

## ADDED Requirements

### Requirement: Outbound messaging port
The application SHALL expose a single outbound messaging contract offering: send a text to a chat, send a text with inline buttons to a chat, edit the text of an existing message, and answer a callback query. Each operation SHALL return a result the caller can check for success or failure; a successful send SHALL include the id of the sent message. The contract MUST NOT expose Telegram types.

#### Scenario: Send text
- **WHEN** a caller sends a text to a chat and Telegram accepts it
- **THEN** the result reports success and includes the new message id

#### Scenario: Send with buttons
- **WHEN** a caller sends a text with rows of buttons (label and callback data)
- **THEN** the message is delivered with those inline buttons and the result reports success with the message id

#### Scenario: Edit text
- **WHEN** a caller edits the text of an existing message
- **THEN** the message text changes and the result reports success

#### Scenario: Edit with identical text
- **WHEN** a caller edits a message to the text it already has and Telegram reports that nothing was modified
- **THEN** the result reports success

#### Scenario: Answer callback
- **WHEN** a caller answers a callback query
- **THEN** Telegram is told the callback was handled and the result reports success

#### Scenario: Oversized callback data
- **WHEN** a caller builds a button whose callback data is longer than Telegram's 64-byte limit
- **THEN** the button is rejected at construction rather than failing later at send time

### Requirement: Honor rate limits
When Telegram answers with HTTP 429 and a `retry_after` value, the application SHALL wait that many seconds and retry, provided the wait does not exceed a configured maximum; when it does exceed the maximum, the operation SHALL fail immediately with a rate-limited result instead of blocking.

#### Scenario: Short rate limit
- **WHEN** Telegram answers 429 with `retry_after` of 2 and the retry succeeds
- **THEN** the operation waits at least 2 seconds before the retry and the result reports success

#### Scenario: Excessive rate limit
- **WHEN** Telegram answers 429 with a `retry_after` above the configured maximum wait
- **THEN** no retry is attempted, no waiting beyond the maximum occurs, and the result reports a rate-limited failure

### Requirement: Bounded retry of transient failures
The application SHALL retry an operation that failed transiently (network error, timeout, HTTP 5xx) a small configured number of times with increasing backoff between attempts. It SHALL NOT retry failures that will not succeed on repeat (for example HTTP 400, 401, 403, 404 such as "chat not found" or "bot was blocked by the user").

#### Scenario: Transient failure then success
- **WHEN** the first attempt fails with HTTP 502 and the second succeeds
- **THEN** the result reports success and exactly two attempts were made, with a pause between them

#### Scenario: Retries exhausted
- **WHEN** every attempt up to the configured maximum fails transiently
- **THEN** the operation stops after that maximum, the pauses between attempts grow, and the result reports a transient failure

#### Scenario: Permanent failure
- **WHEN** Telegram answers 403 because the user blocked the bot
- **THEN** exactly one attempt is made and the result reports a rejected failure

### Requirement: Outbound operations never throw to callers
After retries are exhausted or on a non-retryable error, the application MUST return a failed result and MUST NOT throw an exception to the caller, including for unexpected errors and for interruption while waiting to retry. Each final failure SHALL be logged once, and logs MUST NOT contain the message text or the bot token.

#### Scenario: Unexpected error
- **WHEN** the Telegram client fails with an unexpected exception
- **THEN** the caller receives a failed result and no exception propagates

#### Scenario: Failure log hygiene
- **WHEN** a send fails and the failure is logged
- **THEN** the log entry names the operation, the chat id and the failure reason, and contains neither the message text nor the bot token
