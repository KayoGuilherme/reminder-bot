## ADDED Requirements

### Requirement: Task language column
Migration V2 SHALL add a column `language` (VARCHAR(5), required, default `en`) to `tasks`. The database SHALL reject `language` values other than `en` and `pt-BR`. Existing rows MUST receive `en`.

#### Scenario: Existing tasks after migration
- **WHEN** migration V2 is applied to a database that already holds tasks
- **THEN** every existing task has `language` set to `en`

#### Scenario: New task without an explicit language
- **WHEN** a row is inserted with only `id`, `chat_id`, `title`, `urgency` and `deadline_at`
- **THEN** `language` is `en`

#### Scenario: Invalid language
- **WHEN** a row is inserted with `language = 'es'`
- **THEN** the database rejects the insert

## REMOVED Requirements

### Requirement: No persistence model yet
**Reason**: The `tasks` table is now mapped by a JPA entity and read through a repository, so the requirement no longer describes the code.

**Migration**: None needed in the database. The entity and repository are covered by the existing persistence behaviour and by the tests of the task services.
