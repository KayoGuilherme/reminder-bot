# Proposal

## Why

The reminder bot can only be used through Telegram. The owner also works inside AI assistants (Claude Desktop, Codex, OpenClaw and other MCP clients) and wants to say "remind me to send the report by 6pm" or "what is pending?" there, without switching to Telegram. The Model Context Protocol (MCP) is the common way those clients call external tools, so one MCP server makes the bot reachable from all of them.

## What Changes

- Add an MCP server inside the running bot, served over **Streamable HTTP** at `/mcp` on the existing web server. No second process, no second database connection pool, no new deployment unit.
- The endpoint is **off by default** and requires a bearer token (`MCP_TOKEN`). A request without a valid token is refused before any tool runs.
- Expose five tools, all scoped to the owner (the chat of `TELEGRAM_ALLOWED_USER_ID`):
  - `list_pending_tasks`: the owner's `PENDING` tasks, earliest deadline first.
  - `create_task`: title, deadline, optional urgency, start time and language. The task is **saved directly**, without the Telegram Confirm step (decision of the owner: the MCP client's own tool approval is the confirmation). It still obeys every validation rule: a deadline is required and must be in the future.
  - `complete_task`, `cancel_task`, `snooze_task`: the same rules as the Done, Cancel and Snooze buttons (`DONE`/`CANCELED` are final, one of two racing finishes wins, snooze adds 10 minutes and restarts the cycle).
- Tool results are structured and carry the deadline both in UTC and in `app.timezone`, so an AI can speak about it correctly. Failures are returned as tool errors with a stable reason code, never as a stack trace.
- Tasks created or changed through MCP are normal tasks: the scheduler reminds about them in Telegram exactly as for tasks created there.
- Message, title and tool-argument content is **never logged**, same as for Telegram.
- Document how to connect Claude Desktop (through the `mcp-remote` proxy or a tunnel), Codex and OpenClaw in the README.
- **Business rule exception:** `create_task` bypasses "a task is saved only after the user presses Confirm". It is limited to the MCP entry point; the Telegram flow is unchanged. `.claude/CLAUDE.md` states the rule without that exception, so the apply phase updates its wording.

## Capabilities

### New Capabilities
- `mcp-access`: how an MCP client reaches the bot: the `/mcp` endpoint, the opt-in switch, bearer-token authentication, owner scoping, and what is never logged.
- `mcp-task-tools`: the five tools, their inputs, results and failure reasons, and how they reuse the existing task rules.

### Modified Capabilities
- `app-configuration`: new settings `MCP_ENABLED` and `MCP_TOKEN`, validated at startup only when the server is enabled, and a statement that `/mcp` is an application endpoint, not a management endpoint (the "health and info only" rule stays true).

## Impact

- **Code**
  - New `transport/mcp` folder (tool class, token filter) and new use-case services in `application/service` that return DTOs and do not depend on Telegram (the existing action services reply through `MessageSender`, so they cannot be reused as they are).
  - New DTOs in `application/dto`, a new `McpProperties` and `McpConfiguration` in `config`.
  - `TaskRepository` and `TaskService` are reused as they are; no schema change, no Flyway migration.
  - The code-conventions test (`SourceFile.PACKAGES`) gains `transport.mcp`.
- **Dependencies:** `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` 2.1.0 (targets Spring Boot 4.2.0, the version this project uses). It brings the official MCP Java SDK.
- **Security:** a new network-reachable write path. Mitigated by being disabled by default, a mandatory token compared in constant time, no CORS, and the README advice to bind it to localhost or put it behind a TLS tunnel.
- **Docs and runtime:** README section for MCP and its environment variables, `.env.example` placeholders, `docker-compose.override.yml` already binds the app to `127.0.0.1:8080`.
- **Non-goals:** OAuth, several users, stdio launcher, MCP resources or prompts, editing a task title or deadline, creating a task from audio, idempotency keys for `create_task`, and notifying Telegram when a task changes through MCP.
