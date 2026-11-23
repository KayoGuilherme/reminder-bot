# Tasks

## 1. Baseline and conventions check foundation

- [x] 1.1 Run `./mvnw verify` with Docker running and note the baseline (515 tests, green)
- [x] 1.2 Add `lombok.config` (fluent accessors, constructor properties) and verify `./mvnw compile` passes
- [x] 1.3 Add `javaparser-core` with test scope and verify the dependency resolves
- [x] 1.4 Create the rule classes and `CodeConventionsTest` with a per-rule enable switch
- [x] 1.5 Add fixtures and `ConventionRulesTest` that proves every scenario of the spec

## 2. Comments and package-info

- [x] 2.1 Delete the `package-info.java` files and enable the rule
- [x] 2.2 Strip every comment from main code; the reasons they gave are covered by named tests (for example `savingTheSameIdTwiceFailsAndKeepsTheFirstRow`)
- [x] 2.3 Strip every comment from test code and enable the comments rule

## 3. DDD folders

- [x] 3.1 Move every class into `domain/models`, `domain/models/enums`, `domain/exceptions`, `domain/repositories`, `application/dto/*`, `application/service`, `application/component`, `transport/telegram|stt|llm|http` or `config`, and move the tests to the same packages
- [x] 3.2 Remove the `contract` folder and the `*UseCase` interfaces; keep `MessageSender`, `TaskParser`, `Transcriber` and `AudioDownloader` next to their implementations
- [x] 3.3 Enable the folder, enum-folder, exception-folder and tests-mirror rules and verify `./mvnw verify` is green

## 4. Types without records and models without logic

- [x] 4.1 Turn the data classes into request and response record DTOs (`CreateTaskRequest`, `IncomingCommandRequest`, `TaskResponse`, `DraftResponse`, `ParsedTaskResponse`, `ReminderDecisionResponse`, `ButtonResponse`, `SendResultResponse`), move every nested enum to `domain/models/enums`, and split `AppProperties` into one Lombok class per section; add `DtoValueEqualityTest`
- [x] 4.2 Keep DTOs plain: static factories only, no constructor validation; enable the records, dto-logic and dto-persistence rules
- [x] 4.3 Replace `Guard` with Bean Validation: annotations on `CreateTaskRequest` and `ParsedTaskResponse`, `StartNotAfterDeadline` custom constraint, `TaskFactory` with the application clock; cover the rules in `TaskFactoryTest`

## 5. Spring Data repository

- [x] 5.1 Replace the `EntityManager` repository with `TaskRepository extends JpaRepository<TaskEntity, UUID>`, make `TaskEntity` a `Persistable` in `domain`, add `TaskMapper` and `TaskService`
- [x] 5.2 Move the former repository tests to `TaskServiceTest` (PostgreSQL) and add `TaskMapperTest`; enable the domain layer and no-`EntityManager` rules

## 6. Control flow

- [x] 6.1 Rewrite `ReminderPolicy`, `IncomingCommandService` (with `CallbackRoute`), `ReminderMessageFactory`, `Messages`, `SingleRetry` and `CorrelationId`
- [x] 6.2 Rewrite `ReminderActionService`, `CreateTaskFromVoiceService`, `SendDueRemindersService` and `ListTasksService`
- [x] 6.3 Rewrite the Telegram classes, `ParsedTaskValidator`, `DraftRepository` and the test doubles
- [x] 6.4 Enable the else, switch, nested-if, consecutive-if and tail-return rules and verify they report nothing

## 7. Names, documents and final checks

- [x] 7.1 Rename vague names (`useCase`, short exception variables) and review the rest
- [x] 7.2 Update `.claude/CLAUDE.md` to the DDD layout and the new rules
- [x] 7.3 Enable every rule, introduce one deliberate violation to confirm the build fails with file and line, revert it, and verify `./mvnw verify` is green (549 tests)
- [ ] 7.4 Start the application with the README's Docker Compose command and verify it boots and answers `/start`; verify `git log` for this change carries no assistant attribution
