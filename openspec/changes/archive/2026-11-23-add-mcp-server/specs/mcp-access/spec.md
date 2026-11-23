# Spec Delta

## Purpose

Defines how an MCP client reaches the bot: where the endpoint lives, when it exists, who may call it, and what the bot refuses to write to its logs while serving it. It keeps the new network entry point as closed as the Telegram one.

## ADDED Requirements

### Requirement: The MCP endpoint is opt-in
The bot SHALL serve the Model Context Protocol over Streamable HTTP at the path `/mcp` of its web server only when MCP is enabled by configuration. MCP MUST be disabled by default. When it is disabled, `/mcp` MUST answer as an unknown path and no tool MUST be reachable.

#### Scenario: Disabled by default
- **WHEN** the bot starts without `MCP_ENABLED`
- **THEN** a request to `/mcp` is answered with 404 and no MCP session can be opened

#### Scenario: Enabled
- **WHEN** the bot starts with `MCP_ENABLED=true` and a valid token configured, and an MCP client opens a session on `/mcp` with that token
- **THEN** the session is established and the client can list the bot's tools

#### Scenario: Telegram keeps working
- **WHEN** MCP is enabled
- **THEN** the Telegram bot, the reminder scheduler and the health endpoints behave exactly as before

### Requirement: Every MCP request needs the bearer token
Every request to `/mcp` SHALL carry the header `Authorization: Bearer <token>` where the token equals the configured `MCP_TOKEN`. A request with no header, a different scheme or a wrong token MUST be refused with status 401 before any tool, session or database access happens. The comparison MUST NOT reveal through its timing how much of the token matched.

#### Scenario: Valid token
- **WHEN** a client calls `/mcp` with `Authorization: Bearer <configured token>`
- **THEN** the request is processed

#### Scenario: Missing token
- **WHEN** a client calls `/mcp` without an `Authorization` header
- **THEN** the response is 401 and no tool runs

#### Scenario: Wrong token or scheme
- **WHEN** a client calls `/mcp` with a wrong token, or with `Authorization: Basic ...`
- **THEN** the response is 401 and no tool runs

#### Scenario: The token is not accepted anywhere else
- **WHEN** a client sends the token in a query string or a cookie instead of the header
- **THEN** the response is 401

### Requirement: Enabling MCP requires a usable token
When MCP is enabled, `MCP_TOKEN` MUST be present and at least 32 characters long, otherwise the bot MUST refuse to start with an error naming the MCP token setting. The token value MUST NOT appear in the startup error, in logs or in any health or info response.

#### Scenario: Enabled without a token
- **WHEN** the bot starts with `MCP_ENABLED=true` and no `MCP_TOKEN`
- **THEN** startup fails and the error output identifies the MCP token setting

#### Scenario: Token too short
- **WHEN** the bot starts with `MCP_ENABLED=true` and a 10-character `MCP_TOKEN`
- **THEN** startup fails and the error output identifies the MCP token setting without printing its value

#### Scenario: Disabled needs no token
- **WHEN** the bot starts with MCP disabled and no `MCP_TOKEN`
- **THEN** startup completes

### Requirement: Tools act only on the owner's tasks
Every tool SHALL operate on the tasks of the owner's chat, which is the chat of `TELEGRAM_ALLOWED_USER_ID`. A tool MUST NOT accept a chat or user identifier from the client, and MUST NOT list or change the task of any other chat.

#### Scenario: Other chats are invisible
- **WHEN** the database holds a pending task of another chat and a client calls `list_pending_tasks`
- **THEN** that task is not in the result

#### Scenario: No identity argument
- **WHEN** the tool definitions are listed
- **THEN** none of them has an argument for a chat id or a user id

### Requirement: MCP traffic never logs user content
The bot MUST NOT write task titles, tool arguments, tool results or the bearer token to its logs while serving MCP, at any log level the shipped profiles use. Log lines MAY state the tool name, the task id, the outcome and the correlation id.

#### Scenario: Create a task and inspect the logs
- **WHEN** a client creates a task titled "buy the secret birthday present"
- **THEN** no log line contains that title or the token

#### Scenario: Refused request
- **WHEN** a request with a wrong token is refused
- **THEN** the log, if it records the refusal at all, does not contain the token that was sent
