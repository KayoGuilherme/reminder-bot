# Spec Delta

## Purpose

Makes the bot keep working when an outside service is slow or briefly failing: no call waits forever, transient speech-to-text and LLM errors get one more chance, and a failure on one message never prevents the next one from being handled.

## ADDED Requirements

### Requirement: Every external HTTP client has connect and read timeouts
Each client the application uses to call an external service (Telegram sending, Telegram file download, speech-to-text, the LLM provider) SHALL have a connect timeout and a read timeout, taken from configuration with defaults of 5 seconds and 30 seconds. No external call MAY wait indefinitely. Telegram's long-polling request, which legitimately waits for new updates, is exempt from the 30-second read timeout but MUST still end after a bounded time.

#### Scenario: Server never answers
- **WHEN** the speech-to-text or LLM service accepts the connection but sends no response
- **THEN** the call fails after the configured read timeout instead of waiting forever

#### Scenario: Server unreachable
- **WHEN** the connection to an external service cannot be established
- **THEN** the call fails after at most the configured connect timeout

#### Scenario: Timeouts are configurable
- **WHEN** `app.telegram.read-timeout` is set to 10 seconds
- **THEN** a Telegram send that gets no answer fails after 10 seconds

### Requirement: One retry with backoff for transient speech-to-text and LLM errors
A speech-to-text or LLM call that fails transiently (connection error, timeout, HTTP 429 or any 5xx) SHALL be retried exactly once, after a pause of `app.resilience.retry-backoff` (default 500 ms). Failures that are not transient (for example HTTP 400, 401, 403) and malformed successful responses SHALL NOT be retried. When the retry also fails, the step reports a failure of the same kind as before.

#### Scenario: Transient failure then success
- **WHEN** the first request fails with HTTP 503 and the second succeeds
- **THEN** the result of the second request is used, exactly two requests were made, and the second was made after the configured pause

#### Scenario: Both attempts fail
- **WHEN** both attempts time out
- **THEN** the step fails as unavailable after exactly two requests

#### Scenario: Authentication failure
- **WHEN** the service answers 401
- **THEN** exactly one request is made and the step fails as rejected

### Requirement: A failed call never loses the next message
When transcription or task parsing fails for a message, the owner SHALL receive a friendly error saying to try again, nothing SHALL be saved, and the next voice or text message the owner sends MUST be handled normally, including when it arrives while services are still recovering.

#### Scenario: Recovery after an STT outage
- **WHEN** a voice note fails because speech-to-text is down, then the service recovers and the owner sends another voice note
- **THEN** the first gets a "try again" reply with no draft or task created, and the second is transcribed and answered with a confirmation

#### Scenario: Recovery after an LLM outage
- **WHEN** parsing a text fails with HTTP 503 twice, then the owner sends another text and the service answers
- **THEN** the second text produces a confirmation message

#### Scenario: Unexpected error in one update
- **WHEN** handling one update throws an unexpected error
- **THEN** it is logged, the following update in the same batch is still handled, and polling continues

### Requirement: Startup verification of the bot token
Shortly after startup, when Telegram is enabled, the application SHALL ask Telegram which bot the configured token belongs to. If Telegram rejects the token as invalid or revoked, the application SHALL log one clear ERROR entry that names `TELEGRAM_BOT_TOKEN` and says the bot cannot receive messages, and MUST NOT include the token. If Telegram cannot be reached, it SHALL log a WARN that the check could not be completed and MUST NOT claim the token is invalid. In both cases the application keeps running, so a temporary outage at boot does not stop the container. The check MUST NOT run when Telegram is disabled.

#### Scenario: Invalid token
- **WHEN** Telegram answers 401 Unauthorized to the check
- **THEN** an ERROR entry mentions `TELEGRAM_BOT_TOKEN` and does not contain the token value

#### Scenario: Valid token
- **WHEN** Telegram accepts the token
- **THEN** one INFO entry states the bot's username and no error is logged

#### Scenario: Telegram unreachable
- **WHEN** the check cannot connect to Telegram
- **THEN** a WARN entry is logged, no "invalid token" error is logged, and the application stays up

#### Scenario: Telegram disabled
- **WHEN** `app.telegram.enabled` is false
- **THEN** no request is made to Telegram
