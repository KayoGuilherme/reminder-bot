# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Placeholder replies for text and voice`
- TO: `### Requirement: Routing of text, voice and button presses`

## MODIFIED Requirements

### Requirement: Routing of text, voice and button presses
The application SHALL answer unknown slash commands from the allowed user with a "not supported yet" message in the configured language. A voice message and any text that does not start with `/` SHALL start the task-creation flow instead of being answered with a placeholder. A button press SHALL always be acknowledged without any visible message; a press for the task-creation flow SHALL additionally be handled by that flow, and a press with any other data SHALL do nothing more.

#### Scenario: Plain text
- **WHEN** the allowed user sends "remind me tomorrow at 9"
- **THEN** the task-creation flow handles it

#### Scenario: Voice
- **WHEN** the allowed user sends a voice message
- **THEN** the task-creation flow handles it

#### Scenario: Unknown command
- **WHEN** the allowed user sends `/foo`
- **THEN** the bot replies that this is not supported yet

#### Scenario: Task button
- **WHEN** the allowed user presses a Confirm or Cancel button of the task-creation flow
- **THEN** the flow handles the press and the callback is answered so the client stops showing progress

#### Scenario: Other button
- **WHEN** the allowed user presses an inline button whose data the flow does not recognize
- **THEN** the callback is answered silently, and no message is sent
