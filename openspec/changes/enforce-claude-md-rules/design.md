# Design

## Context

See `proposal.md` for the motivation. The code started as a hexagonal layout. During implementation the owner corrected the layout twice: first asking for a small project that can be read end to end (reuse the validation dependency, Spring Data repository, no logic in data classes), then asking for a DDD layout where models are database models in `domain`, plain data is DTO records in `application`, enums and exceptions have their own folders, and `transport` is split by external service. This design records what was built.

## Goals / Non-Goals

**Goals:**
- A tree a reader understands in one look: three layers (`domain`, `application`, `transport`) plus `config`.
- Every rule of `.claude/CLAUDE.md` true and checked on every build.
- The suite green after each step.

**Non-Goals:**
- No behavior, message text, configuration key or schema change.
- No rename of the base package, no change to `Dockerfile`, CI or `deploy/`.

## Decisions

### 1. DDD folders

```
com.kayo.reminder
├── domain/
│   ├── models/        TaskEntity
│   │   └── enums/     TaskStatus, Urgency, TaskRule, ReminderKind, CommandKind, SendFailureReason, ExternalFailureKind
│   ├── exceptions/    TaskValidationException, ExternalServiceException
│   └── repositories/  TaskRepository (Spring Data)
├── application/
│   ├── dto/request/   CreateTaskRequest, IncomingCommandRequest
│   ├── dto/response/  TaskResponse, DraftResponse, ParsedTaskResponse, ReminderDecisionResponse, ButtonResponse, SendResultResponse
│   ├── service/       TaskService and the use-case services, ReminderPolicy
│   └── component/     TaskMapper, TaskFactory, DraftStore, Messages, formatters, schedulers, ...
├── transport/
│   ├── telegram/      bot, consumer, sender, downloader, update mapper, allowlist, MessageSender, AudioDownloader
│   ├── stt/           GroqTranscriptionClient, Transcriber, payload
│   ├── llm/           ChatCompletionsTaskParser, TaskParser, prompt, validator, payload
│   └── http/          RestClients, SingleRetry
└── config/            AppProperties, one properties class per section, the *Configuration classes
```

Alternatives: keep `repository`, `service` and `component` at the top level beside `application` (offered and not chosen); feature subfolders and a `contract` folder (the first attempt, too hard to follow).

### 2. Spring Data repository, `TaskService` on top

`TaskRepository` (in `domain/repositories`) `extends JpaRepository<TaskEntity, UUID>` and carries the queries as `@Query` methods and derived finders. `TaskEntity` implements `Persistable` so `saveAndFlush` persists and never silently overwrites an existing id. `TaskService` is the only user of the repository: it maps with `TaskMapper` and exposes the same operations the rest of the code already used (`save`, `markReminded`, `snooze`, ...). The service computes the final stage per urgency and passes it in, so the repository does not depend on the reminder policy. Tests replace `TaskService` with an in-memory subclass.

Alternative: services use the repository and the mapper directly (rejected: the conditional-update boolean results and the mapping would be repeated in every service).

### 3. Bean Validation on the request DTO

`CreateTaskRequest` carries `@NotBlank`, `@Size`, `@NotNull`, `@Future` with the rule name as message, plus one custom class-level constraint for "start not after deadline" (the dependency has no cross-field constraint). `TaskFactory` trims the title, validates the request with a `Validator` whose clock provider is the application `Clock` (so `@Future` follows the injected clock), and returns a pending `TaskResponse`. When several constraints fail, the first rule in the `TaskRule` enum order is reported, matching the old order. `ParsedTaskResponse` validates its title the same way. Rules already enforced by `AppProperties` annotations are not checked again in `ReminderPolicy`.

DTOs have no constructor validation: `IncomingCommandRequest` and `SendResultResponse` are built through static factories by the code itself.

### 4. Records for DTOs, Lombok for the rest

DTOs and HTTP payloads are records, so `task.id()` style accessors are unchanged. Configuration properties are Lombok classes with `@DefaultValue` on constructor parameters (not allowed on fields); the entity is a Lombok class.

### 5. Control flow

`Optional` pipelines, streams, `EnumMap` and strategy maps replace `switch`, `else`, loops with `return` and guard `if`s. Callback routing is a list of `CallbackRoute` objects; text commands are a map from command to handler. The Telegram sender retry is a recursion over `attempt` with `waitBeforeRetry` returning an `Optional<Duration>`.

### 6. The conventions test

`CodeConventionsTest` parses every Java file with `javaparser-core` (test scope) and applies one rule class per convention; each reports file and line. Fixtures under `src/test/resources/conventions` prove every scenario of the spec. `-Dconventions.all=true` runs every rule even if one is not enabled yet.

## Risks / Trade-offs

- [Spring Data semantics differ from `EntityManager`] → `TaskServiceTest` (PostgreSQL) keeps all former repository tests, including duplicate id and the conditional updates.
- [Mocked services in tests] → stubs are Mockito mocks created in `testsupport`.
- [Tail-position `return` inside `try` is still a return] → stated in the spec so the check and a reviewer agree.
- [Logger names change with packages] → accepted; messages are unchanged.

## Migration Plan

One branch, steps in the order of `tasks.md`, `./mvnw verify` green after each. No deployment step: the image, configuration keys and schema are unchanged. Rollback is reverting the commits.
