# Proposal

## Why

`.claude/CLAUDE.md` now defines the code rules for this project, but the existing code was written before it: comments everywhere, `package-info.java`, records, `else` / `switch` / early `return`, a hand-written `EntityManager` repository, custom validation next to a validation dependency, and a layout (`domain`, `application/port/*`, `infrastructure/*`) that is hard to follow. The goal is a small project that its owner can open and understand, plus a check that fails the build when a rule is broken again.

## What Changes

- Remove every comment from main and test code and delete the `package-info.java` files.
- Data that is only created and passed around becomes a DTO record: requests in `application/dto/request`, responses in `application/dto/response`. Records are allowed only for DTOs and HTTP payloads; other classes use Lombok with fluent accessors.
- Rewrite control flow without `else`, `else if`, `switch`, nested or consecutive `if`, or early `return`, using `Optional`, streams, maps and small methods.
- Use a DDD layout: `domain` (`models` with the JPA entity and `models/enums` with every enum, `exceptions`, `repositories`), `application` (`dto/request`, `dto/response`, `service`, `component`), `transport` (`telegram`, `stt`, `llm`, `http`) and `config`.
- The task repository becomes a Spring Data interface (`extends JpaRepository<TaskEntity, UUID>`) with `@Query` methods; a `TaskService` maps entity and DTOs through a `TaskMapper`. The hand-written `EntityManager` repository is removed.
- Drop the `*UseCase` interfaces: services are called directly. Interfaces stay only for the outbound clients that tests fake (`MessageSender`, `TaskParser`, `Transcriber`, `AudioDownloader`), next to their implementation in `transport`.
- Validate tasks with Bean Validation (`@NotBlank`, `@Size`, `@NotNull`, `@Future`) on `CreateTaskRequest` through a `Validator` configured with the application clock; only the cross-field "start not after deadline" rule is a custom constraint. The hand-written `Guard` is removed.
- Add `CodeConventionsTest`, which parses the sources and fails the build on any violation.
- No user-visible behavior change.

## Capabilities

### New Capabilities

- `code-conventions`: the verifiable rules for the source tree and the automated check that enforces them.

### Modified Capabilities

None. Behavior described by the existing specs does not change.

## Impact

- Code: every main and test class changes package or content; `pom.xml` gains a test-scoped source parser; a `lombok.config` is added.
- Persistence: queries move from an `EntityManager` class to Spring Data `@Query` methods, with the same conditional-update semantics; the schema is unchanged.
- Tests: same scenarios; tests that exercised the removed constructor validation of `Button`, `IncomingCommand` and `ReminderPolicy` are dropped (their inputs are built by the code itself, or are already validated by `AppProperties`), and the task rules are tested through `TaskFactory`.
- Logs: logger names follow the new class packages.
- In-flight changes `voice-to-task` and `telegram-bot-base` describe behavior only and are unaffected.
