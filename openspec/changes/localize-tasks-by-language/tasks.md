# Tasks

## 1. Storage and model

- [x] 1.1 Add `src/main/resources/db/migration/V2__add_task_language.sql` with `language VARCHAR(5) NOT NULL DEFAULT 'en'` and a check constraint allowing `en` and `pt-BR`, and verify a fresh and an already-migrated database both start with the migration applied
- [x] 1.2 Add the `language` field to `TaskEntity` and verify the application starts with schema validation enabled
- [x] 1.3 Add the `language` component to `TaskResponse` and map it in `TaskMapper` both ways, and verify `TaskMapperTest` round-trips the value

## 2. Language from the draft to the task

- [x] 2.1 Add `language` to `CreateTaskRequest` with `@Pattern(regexp = "en|pt-BR")` and carry it through `TaskFactory.create`, and verify `TaskFactoryTest` rejects `es` and keeps `pt-BR`
- [x] 2.2 Resolve the draft language in `CreateTaskFromVoiceService` (the parser's language if supported, otherwise `app.language`) and pass it to `TaskFactory`, and verify a draft with a null language saves the task as `app.language`
- [x] 2.3 Remove the `DraftStore` overload that takes no language if nothing calls it, and verify the project compiles and `DraftStoreTest` passes

## 3. Task-facing texts in the task's language

- [x] 3.1 Use `messages.forLanguage(task.language())` for the buttons and texts in `ReminderMessageFactory`, and verify `ReminderMessageFactoryTest` renders pt-BR for a pt-BR task and English for an en task
- [x] 3.2 Use the task's language in `ReminderActionService` for the Done and Snooze confirmations and the polite replies, and verify `ReminderActionServiceTest` checks Portuguese replies for a pt-BR task
- [x] 3.3 Use the task's language in `ListTasksService` for the list buttons and the overdue flag, and verify `ListTasksService` tests show pt-BR labels for a pt-BR task
- [x] 3.4 Update the `reminder-messages` reference wording in its tests so pt-BR tasks are asserted in Portuguese, and verify `SendDueRemindersServiceTest` passes

## 4. Dates in the task's language

- [x] 4.1 Turn `TaskSummaryFormatter` into a `@Component` that takes `ZoneId` from `AppProperties` and takes `Messages` per call in `summary`, `listItem` and `format`, reading `date.pattern` from the given `Messages`, and verify no class calls `new TaskSummaryFormatter`
- [x] 4.2 Update `CreateTaskFromVoiceService`, `ReminderActionService` and `ListTasksService` to use the injected formatter with the task's `Messages`, and verify the snoozed confirmation shows `23/11/2026 09:10` for a pt-BR task in `America/Fortaleza`
- [x] 4.3 Add formatter tests for the pt-BR and en patterns and for the display timezone, and verify they pass

## 5. Test support

- [x] 5.1 Add a language argument to every `new TaskResponse(...)` call in tests and to `InMemoryTaskService`, and verify the test sources compile
- [x] 5.2 Add a test that a pt-BR task gets a Portuguese reminder while `app.language` is `en`, and verify it passes

## 6. Integration check

- [x] 6.1 Run `./mvnw verify` (or the project's build command) and verify the build, the architecture conventions test and all tests pass
- [ ] 6.2 Run the app against a local database and a pt-BR draft through `propose`, `confirm`, a stage-1 reminder and a snooze, and verify every text and date on Telegram is Portuguese with `dd/MM/yyyy HH:mm`
