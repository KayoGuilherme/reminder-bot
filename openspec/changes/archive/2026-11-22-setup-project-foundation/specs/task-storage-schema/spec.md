## Purpose

Defines the PostgreSQL schema that stores reminder tasks and the rule that versioned migrations, not the ORM, own that schema.

## ADDED Requirements

### Requirement: Migrations own the schema
The database schema SHALL be created and changed only by versioned Flyway migrations applied automatically at startup. The persistence layer MUST only validate the schema and MUST NOT create or alter it.

#### Scenario: Fresh database
- **WHEN** the application starts against an empty PostgreSQL database
- **THEN** migration V1 is applied and recorded and the application starts successfully

#### Scenario: Already migrated database
- **WHEN** the application starts against a database where V1 has been applied
- **THEN** no migration is re-applied and startup succeeds

### Requirement: Tasks table structure
Migration V1 SHALL create a table `tasks` with columns: `id` (UUID, primary key), `chat_id` (BIGINT, required), `title` (VARCHAR(500), required), `urgency` (VARCHAR(10), required), `status` (VARCHAR(10), required, default `PENDING`), `start_at` (TIMESTAMPTZ, optional), `deadline_at` (TIMESTAMPTZ, required), `last_reminder_stage` (SMALLINT, required, default 0), `last_reminded_at` (TIMESTAMPTZ, optional), `created_at` (TIMESTAMPTZ, required, default current time) and `updated_at` (TIMESTAMPTZ, required, default current time).

#### Scenario: Defaults applied
- **WHEN** a row is inserted with only `id`, `chat_id`, `title`, `urgency` and `deadline_at`
- **THEN** `status` is `PENDING`, `last_reminder_stage` is 0, `start_at` and `last_reminded_at` are null, and `created_at` and `updated_at` are set

#### Scenario: Required columns enforced
- **WHEN** a row is inserted without `deadline_at` or without `title`
- **THEN** the database rejects the insert

### Requirement: Value constraints
The database SHALL reject `urgency` values other than `LOW`, `MEDIUM`, `HIGH` and `status` values other than `PENDING`, `DONE`, `CANCELED`.

#### Scenario: Invalid urgency
- **WHEN** a row is inserted with `urgency = 'URGENT'`
- **THEN** the database rejects the insert

#### Scenario: Invalid status
- **WHEN** a row is updated to `status = 'ARCHIVED'`
- **THEN** the database rejects the update

### Requirement: Reminder query index
The schema SHALL include an index on `(status, deadline_at)` to serve the query that finds pending tasks by deadline.

#### Scenario: Index present
- **WHEN** the `tasks` table indexes are inspected after migration
- **THEN** an index covering `status` then `deadline_at` exists

### Requirement: No persistence model yet
This foundation MUST NOT introduce entity or repository types for `tasks`; the schema exists ahead of the code that uses it.

#### Scenario: Startup validation passes without entities
- **WHEN** the application starts with schema validation enabled and no entities defined
- **THEN** startup succeeds
