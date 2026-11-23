# Design

## Context

See `proposal.md` for the motivation. What the code looks like today:

- Spring Boot 4.2.0, Java 21, `spring-boot-starter-webmvc` already on the classpath (it serves the actuator on port 8080). There is **no Spring Security**.
- Every task action is written for Telegram. `ReminderActionService`, `ListTasksService` and `CreateTaskFromVoiceService` take a `MessageSender`, reply through it and are only created when `app.telegram.enabled` is true. They cannot be called by something that is not Telegram.
- What is reusable and Telegram-free: `TaskService` (transactional, with conditional updates that make Done/Cancel/Snooze race safely), `TaskFactory` (trims and validates a `CreateTaskRequest` with Bean Validation and throws `TaskValidationException(TaskRule)`), `TaskMapper`, `TaskRepository`, `ReminderPolicy.SNOOZE`, `CorrelationId`, `Clock`, and `AppProperties` (`timezone`, `language`, `telegram.allowedUserId`).
- Telegram tasks are stored per `chatId`; the owner's private chat id equals `TELEGRAM_ALLOWED_USER_ID`, which is how the MCP tools find "the owner's tasks".
- `CLAUDE.md` rules that shape the code: DDD folders only (a new external entry point gets a new `transport` folder), no comments, no `else`/`switch`/early `return`/nested `if`, records only for DTOs and HTTP payloads, validation through Jakarta Bean Validation, reuse dependencies before writing code, services separate from components. `CodeConventionsTest` enforces the folder list through `SourceFile.PACKAGES`.
- Clients: Codex and OpenClaw speak MCP over Streamable HTTP with a bearer header. Claude Desktop launches local stdio servers, and reaches a remote HTTP server through a proxy such as `mcp-remote` (or a connector URL when the server is public over TLS).

## Goals / Non-Goals

**Goals:**
- One MCP server in the running app, reusing the existing task rules, with no schema change.
- Secure by default: off until configured, token required, no user content in logs.
- Keep the Telegram services and their tests untouched.

**Non-Goals:**
- Refactoring the Telegram services onto the new ones (see Open Questions).
- A stdio mode of the jar, OAuth, multi-user, MCP resources/prompts, task editing, idempotency keys.
- TLS termination. The operator puts the endpoint behind localhost, a VPN or a TLS tunnel.

## Decisions

### 1. Spring AI MCP server starter, annotation style

Add `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` 2.1.0 (imported through the `spring-ai-bom` in `dependencyManagement`). Its Maven POM depends on Spring Boot 4.2.0, the version in use, and it wraps the official MCP Java SDK with auto-configuration of the Streamable HTTP transport on the webmvc stack we already have. Tools are plain methods annotated `@McpTool` with `@McpToolParam` arguments; the JSON schema and descriptions are generated from them.

Alternatives:
- **Official MCP Java SDK directly** (`mcp-spring-webmvc`): fewer moving parts, but we would write the tool registration and schema by hand. Kept as the fallback if the starter conflicts with the project (see task 1.1).
- **Hand-written JSON-RPC controller**: rejected, it re-implements the protocol (sessions, capability negotiation, SSE) that a dependency already offers; against the "reuse dependencies" rule.
- **Spring AI `@Tool` + `spring-ai-starter-mcp-server`** (non-annotation): same dependency, more boilerplate.

The exact property names (`spring.ai.mcp.server.*`: protocol, endpoint, enabled, name) are confirmed in the spike of task 1.1 and then pinned in `application.yaml`.

### 2. In-process, Streamable HTTP, opt-in

The server lives in the bot process, on `/mcp`, so it shares the database pool, the clock and the config and needs no extra deployment. Spring AI's switch is bound to `app.mcp.enabled` (`MCP_ENABLED`, default `false`), so when it is off no `/mcp` route and no tool bean exists (404). Telegram long polling, the scheduler and the actuator are unaffected.

Alternatives: a separate stdio process (needs the database on the client machine, duplicates the scheduler/Telegram wiring or needs profile switches); a separate HTTP service (a second deployment unit for five tools). Rejected, per the owner's choice of "HTTP inside the bot".

### 3. Bearer token with a plain servlet filter, no Spring Security

A `OncePerRequestFilter` in `transport/mcp`, registered through a `FilterRegistrationBean` for `/mcp` and `/mcp/*` only, checks `Authorization: Bearer <token>`. Both sides are hashed with SHA-256 and compared with `MessageDigest.isEqual`, so the compare is constant-time regardless of length. A miss answers 401 with `WWW-Authenticate: Bearer` and never reaches the MCP servlet. The filter is written with `Optional` (no `if`/`else`); the checked exceptions of `chain.doFilter` are confined to one small method.

Alternatives: Spring Security (a large new dependency, auto-secures actuator and Telegram-unrelated paths, many knobs; against "keep the architecture simple"); the MCP SDK's own transport-context hooks (still need a token check somewhere); an IP allowlist only (not enough once a tunnel is used). OAuth is a non-goal; every named client can send a static bearer header.

### 4. Configuration and fail-fast

`McpProperties` (`enabled`, `token`) is a nested `@Valid` object of `AppProperties`, with a `toString` that masks the token like `TelegramProperties`. Conditional validation uses Jakarta's `@AssertTrue` on a method that is true when MCP is disabled, or when the token is at least 32 characters. A binding failure then names `app.mcp...` without echoing the token (the rejected value of that constraint is the boolean). No custom constraint is written.

### 5. Telegram-free use-case services returning DTOs

New classes, each in the folder of its role:

| Piece | Folder | Role |
|---|---|---|
| `CreateTaskService`, `ListPendingTasksService`, `FinishTaskService` (`complete` and `cancel`), `SnoozeTaskService` | `application/service` | one use case each; scope to the owner chat; return DTOs; no `MessageSender`, no `app.telegram.enabled` condition |
| `NewTaskRequest` | `application/dto/request` | raw `create_task` arguments (date-times still text) |
| `OwnedTaskLookup` | `application/component` | parses the id as UUID and finds a task of the owner chat, else throws `TaskActionException(TASK_NOT_FOUND)` |
| `TaskViewFactory` | `application/component` | builds `TaskViewResponse` from `TaskResponse`, `Clock` and `app.timezone` (UTC deadline, local deadline, overdue) |
| `DateTimeReader` | `application/component` | parses an ISO date-time with or without offset, using `app.timezone` for the latter, else `INVALID_DATETIME` |
| `TaskViewResponse`, `PendingTasksResponse` | `application/dto/response` | what the tools return |
| `TaskFailure` (enum), `TaskActionException` | `domain/models/enums`, `domain/exceptions` | reasons `INVALID_DATETIME`, `INVALID_LIMIT`, `TASK_NOT_FOUND`, `TASK_NOT_PENDING`, `NOT_SNOOZABLE` |

`CreateTaskService` builds the existing `CreateTaskRequest` (random id, owner chat, default urgency `MEDIUM`, default language `app.language`), calls `TaskFactory.create` (so every rule of Telegram applies, including validation) and `TaskService.save`.

`FinishTaskService` calls `TaskService.markDone` / `markCanceled`, then always re-reads the task: the target status means success (also when it was already there, so the call is idempotent), any other status means `TASK_NOT_PENDING`. That gives the "exactly one wins" race behavior without new SQL. Snooze applies the same rule as the button (`later(deadline, now) + ReminderPolicy.SNOOZE`, requires `lastReminderStage > 0`) through `TaskService.snooze`.

Alternative: reuse `ReminderActionService` by giving it a no-op `MessageSender`. Rejected: its outcomes are Telegram message keys, not data, and its conditional bean would still tie MCP to `app.telegram.enabled`.

### 6. Tools are a thin transport adapter

`TaskTools` in `transport/mcp` holds the five `@McpTool` methods: it only maps arguments to the services and the result to the MCP shape. A `ToolGuard` runs each call and converts `TaskActionException`, `TaskValidationException` (reason = the `TaskRule` name) and any other `RuntimeException` (`INTERNAL_ERROR`, no detail) into an error `CallToolResult` whose text starts with the code. It logs only the tool name, task id when known, outcome and the correlation id (`CorrelationId.open()` around each call). Titles and arguments are never passed to the logger.

### 7. Create saves directly (owner's decision)

The proposal's decision stands: `create_task` is the confirmation, because the MCP client already asks the human to approve a tool call. All validation is kept, so a task with no or a past deadline is still refused. To keep the project's written rule true, the apply phase edits `.claude/CLAUDE.md` ("Main business rule") to say the Confirm step applies to the Telegram flow and MCP `create_task` is the explicit exception.

### 8. Log hygiene and operations

`logging.level.io.modelcontextprotocol`, `org.springframework.ai` and `org.hibernate.orm.core` are pinned to `INFO` in `application.yaml` (like the REST client packages already are), so a root `DEBUG` cannot write JSON-RPC messages or the entities Hibernate prints on flush (title included; this also closes the same gap for Telegram-created tasks). The Spring AI annotation scanner is switched off together with MCP, otherwise every default start logs a "No tool methods found" warning. A `McpLogHygieneTest` mirrors `TelegramLogHygieneTest`. The compose stack already publishes the app only on `127.0.0.1:8080` in local; the README tells the operator how to expose `/mcp` safely and how to connect each client.

## Risks / Trade-offs

- **Spring AI 2.1.0 vs Spring Boot 4.2.0 conflict** (transitive versions, starter pulls `spring-boot-starter-web` next to `spring-boot-starter-webmvc`) → task 1.1 is a spike that builds and boots first; fallback is the MCP Java SDK directly (Decision 1).
- **A leaked token allows creating and finishing tasks** → off by default, 32+ characters, constant-time check, no logging of it, README says to rotate it and keep `/mcp` behind localhost, VPN or TLS. Read access exposes task titles, so the token is treated like the bot token.
- **Prompt injection through a task title**: an AI that lists tasks reads user text. The tools only create, list and finish tasks and cannot send messages or touch anything else, which bounds the damage → tool descriptions say titles are data.
- **Duplicates on client retry** (`create_task` has no idempotency key) → accepted; a duplicate is visible in `list_pending_tasks` and cancelable.
- **Snooze rule is duplicated** (the 10-minute rule exists in `ReminderActionService` and in `SnoozeTaskService`) → both read `ReminderPolicy.SNOOZE`; the Telegram side is intentionally not touched here.
- **The Confirm exception** weakens a stated invariant → scoped to one tool, written into `CLAUDE.md` and into the `mcp-task-tools` spec.
- **MCP tasks produce no Telegram message** → the owner sees them at the first reminder or with `/tasks`.
- **Claude Desktop needs `mcp-remote` or a public TLS URL** → documented, not solved in code.

## Migration Plan

1. Ship with `MCP_ENABLED=false` (default): no behavior change for existing deployments, no migration.
2. To use it: set `MCP_ENABLED=true` and `MCP_TOKEN` (generate with `openssl rand -hex 32`) in `.env`, restart; connect a client to `http://127.0.0.1:8080/mcp` (or the tunnel URL).
3. Rollback: set `MCP_ENABLED=false` or remove the variable and restart; tasks created through MCP stay as normal tasks. Removing the code needs no data migration.

## Open Questions

- Should the Telegram services later be rebuilt on top of the new use-case services so the snooze and finish rules live in one place? It does not change any spec or task here; decide after this lands.
