# Spec Delta

## Purpose

Defines how the bot receives Telegram updates, who it listens to, what it does with them in this first version, and how failures are contained. It is the entry point later features (tasks, voice, callbacks) plug into.

## ADDED Requirements

### Requirement: Receive updates by long polling
When `app.telegram.enabled` is true, the application SHALL receive Telegram updates by long polling using the configured bot token, and SHALL process them in the order received. When it is false, the application MUST NOT contact Telegram.

#### Scenario: Polling enabled
- **WHEN** the application starts with `app.telegram.enabled=true` and a valid bot token
- **THEN** it begins receiving updates for that bot

#### Scenario: Polling disabled
- **WHEN** the application starts with `app.telegram.enabled=false`
- **THEN** it starts normally, makes no connection to Telegram and processes no updates

### Requirement: Only the allowed user is served
The application SHALL process an update only when its sender's user id equals `TELEGRAM_ALLOWED_USER_ID`. For an update from any other sender, the application MUST NOT reply, MUST NOT process it further, and SHALL log one WARN entry that contains the sender's user id and no message content (no text, no voice data, no callback data). An update with no identifiable sender SHALL be ignored without a WARN.

#### Scenario: Allowed user
- **WHEN** an update arrives whose sender id equals the allowed user id
- **THEN** it is processed

#### Scenario: Other user
- **WHEN** an update arrives from a different user id
- **THEN** nothing is sent back, no command is produced, and a WARN is logged containing that user id but not the message text

#### Scenario: Other user's callback
- **WHEN** a button press (callback) arrives from a different user id
- **THEN** it is ignored without any answer and the same WARN (user id only) is logged

#### Scenario: No sender
- **WHEN** an update with no sender (for example a channel post) arrives
- **THEN** it is ignored and no WARN naming a user is logged

### Requirement: Updates become Telegram-agnostic commands
The application SHALL convert each accepted update into an internal command carrying the chat id, the sender id, and — as applicable — the message id, the text, the voice file id and duration in seconds, and the callback id, callback data and the id of the message the button belongs to. Updates of any other kind (stickers, photos, edits, membership changes, …) SHALL be ignored without a reply. Code outside the Telegram adapter MUST NOT depend on Telegram library types.

#### Scenario: Text message
- **WHEN** an allowed user sends the text "buy milk"
- **THEN** a command with that text, the chat id and the sender id is produced and no voice or callback fields are set

#### Scenario: Voice message
- **WHEN** an allowed user sends a 7-second voice message
- **THEN** a command with the voice file id and a duration of 7 seconds is produced and no text is set

#### Scenario: Callback
- **WHEN** an allowed user presses an inline button with callback data `x:1`
- **THEN** a command with the callback id, the data `x:1`, the chat id and the id of the message holding the button is produced

#### Scenario: Unsupported update kind
- **WHEN** an allowed user sends a sticker
- **THEN** no command is produced and nothing is sent back

#### Scenario: Telegram types stay in the adapter
- **WHEN** the application sources outside the Telegram adapter package are inspected
- **THEN** none of them imports a Telegram library class

### Requirement: Start and help commands
The application SHALL reply to `/start` and `/help` from the allowed user with a short usage message in the configured language. A command addressed to the bot by name (for example `/start@my_bot`) or followed by arguments SHALL be treated like the bare command.

#### Scenario: Start
- **WHEN** the allowed user sends `/start`
- **THEN** the bot replies in the same chat with the usage message

#### Scenario: Help with bot name
- **WHEN** the allowed user sends `/help@my_bot`
- **THEN** the bot replies with the same usage message

### Requirement: Placeholder replies for text and voice
The application SHALL answer any other text (including unknown slash commands) and any voice message from the allowed user with a "not supported yet" message in the configured language, and SHALL NOT create, store or schedule anything. A button press SHALL be acknowledged without any visible message.

#### Scenario: Plain text
- **WHEN** the allowed user sends "remind me tomorrow"
- **THEN** the bot replies that this is not supported yet

#### Scenario: Voice
- **WHEN** the allowed user sends a voice message
- **THEN** the bot replies that this is not supported yet

#### Scenario: Unknown command
- **WHEN** the allowed user sends `/foo`
- **THEN** the bot replies that this is not supported yet

#### Scenario: Callback acknowledgement
- **WHEN** the allowed user presses an inline button
- **THEN** the callback is answered silently so the client stops showing progress, and no message is sent

### Requirement: A bad update never stops polling
The application SHALL process each update inside a global error handler. When processing an update fails for any reason, the application MUST log the failure with the update id (and the exception) but without the message content, and MUST continue with the next update and keep polling.

#### Scenario: Handler throws
- **WHEN** processing one update throws an exception and a later update arrives in the same batch
- **THEN** the failure is logged with the update id and the later update is still processed

#### Scenario: Malformed update
- **WHEN** an update cannot be converted to a command
- **THEN** it is logged and skipped and polling continues
