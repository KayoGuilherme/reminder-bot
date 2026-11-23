# Design

## Context

A task is created from a draft, and the draft already carries the language the parser detected (`en` or `pt-BR`, or null for anything else). Nothing stores that language: `DraftResponse.language` is dropped at save time, `TaskEntity` and `TaskResponse` have no language, and `ReminderMessageFactory`, `ReminderActionService` and `ListTasksService` take their `Messages` from the application-wide `app.language`. The reminder is sent hours after the task was written, so the language must be kept with the task rather than detected again later.

`Messages` already switches language with `forLanguage(tag)`, and both bundles already contain the same keys, including `date.pattern`. The gap is that the task-facing call sites never call it with a task's language, and `TaskSummaryFormatter` reads `date.pattern` once in its constructor from the default `Messages`.

See proposal.md for the motivation and specs/ for the requirements.

## Goals / Non-Goals

**Goals:**
- A task keeps the language of the message that created it, and every task-facing text and date follows it.
- Date and time use the task language's `date.pattern` in all task-facing output.

**Non-Goals:**
- Translating user content (titles are shown as written).
- Per-user language preferences or a language setting for the chat.
- Changing the LLM prompt, the speech-to-text settings or the text of the draft summary beyond the date format.

## Decisions

### 1. Store the language on the task, resolved at draft creation

The language is written on the task as a column `language` (VARCHAR(5), `en` or `pt-BR`, default `en`). The value is resolved once, when the draft is created in `CreateTaskFromVoiceService.propose`: the parser's language if it is supported, otherwise `app.language`. Storing the resolved value means the task never depends on the app setting changing later.

- Alternative: one language for the whole bot (last message wins). Rejected: a task written in English would start speaking Portuguese after a Portuguese message, which is the mixing the user reported.
- Alternative: store null and fall back to `app.language` at reminder time. Rejected: a later config change would silently re-language old tasks.

### 2. Validate the language with Bean Validation

`CreateTaskRequest` gets a `language` component with `@Pattern(regexp = "en|pt-BR")`, matching the rule in `AppProperties.language`. `TaskFactory` already validates that request, so an unsupported value cannot reach the database. The database check constraint is the second line of defence, the same way `urgency` and `status` are guarded in V1.

### 3. Each task-facing call site asks `Messages` for the task's language

`Messages.forLanguage(tag)` already returns a `Messages` for a supported tag and `this` otherwise. The reminder factory, the action service and the list service call `messages.forLanguage(task.language())` when they render a task. No new abstraction is added: `Messages` stays a thin wrapper and the bundles stay the single source of text.

### 4. `TaskSummaryFormatter` becomes a component that takes the language per call

Today it is built with `new TaskSummaryFormatter(messages, zone)` in three services, and its pattern is fixed at construction. It becomes a `@Component` that takes `ZoneId` from `AppProperties` once, and its methods receive the `Messages` to use: `summary(Messages, DraftResponse)`, `listItem(Messages, TaskResponse, Instant)` and `format(Messages, Instant)`. The pattern is read from `texts.get("date.pattern")` inside `format`. This removes the duplicate construction and makes the date follow the language of the item being formatted.

- Alternative: keep the constructor and build one formatter per language. Rejected: two instances of the same logic would need a lookup by language, which is more code than passing `Messages` per call.

### 5. Replies that are not about a task keep `app.language`

The usage text, the not-supported reply and the empty `/tasks` reply have no task to take a language from, so they keep `app.language`. The spec for the empty list already says "configured language", so it does not change.

## Risks / Trade-offs

- [A pt-BR user sends `/tasks` with no tasks and gets English from the default] → Set `APP_LANGUAGE=pt-BR` for this single-user deployment, or answer the open question below. The change itself does not depend on it.
- [Existing tasks become `en`] → Their reminders stay in English. This is the expected result of the default, and the user can cancel and recreate them; no data is rewritten.
- [Tests build `TaskResponse` in many places] → Every constructor call gets a `language` argument; the test fake `InMemoryTaskService` stores it as given. Mechanical, covered by the compiler.
- [The stale requirement "No persistence model yet" contradicts the existing entity] → It is removed in the task-storage-schema delta, so the spec matches the code.

## Migration Plan

1. Add `V2__add_task_language.sql`: `ALTER TABLE tasks ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'en'` and a check constraint for `en` and `pt-BR`. Flyway applies it at startup; existing rows get `en`.
2. Deploy the code with the mapper and entity changes in the same release. The schema is validated at startup, so the column must exist before the new entity is used. Flyway runs first, which satisfies this.
3. Rollback: the previous release ignores the extra column because the schema is validated, not generated. A rollback therefore needs no database step; the column can stay or be dropped in a later migration.

## Open Questions

- Should `/tasks` with no pending tasks, and the usage and not-supported replies, follow the language of the user's last message instead of `app.language`? This does not change the specs written here (they say "configured language"), so it can be answered later. The simple answer is to set `APP_LANGUAGE=pt-BR` for this deployment.
