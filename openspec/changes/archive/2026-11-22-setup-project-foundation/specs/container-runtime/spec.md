## Purpose

Defines how the application is packaged and run as containers for a single instance on a developer machine, a server or a Raspberry Pi.

## ADDED Requirements

### Requirement: Reproducible container image
The project SHALL provide a Dockerfile that builds the application from source in a JDK 21 stage and runs it in a slim JRE 21 stage, as a non-root user, with JVM memory options derived from container limits and a healthcheck against the application health endpoint. A `.dockerignore` MUST keep build output, VCS data and local env files out of the build context.

#### Scenario: Image builds from a clean clone
- **WHEN** `docker build .` runs on a clean checkout
- **THEN** it produces an image without needing a pre-built jar or local tools other than Docker

#### Scenario: Non-root runtime
- **WHEN** a container from the image is inspected
- **THEN** its process runs as a user other than root

#### Scenario: Container healthcheck
- **WHEN** the container is running and the application is up
- **THEN** Docker reports it as healthy

#### Scenario: Secrets not baked in
- **WHEN** the image layers are inspected
- **THEN** no `.env` file or secret value is present

### Requirement: Compose stack with database
The project SHALL provide a compose definition with an `app` service built from the Dockerfile and a `db` service running PostgreSQL pinned to a major version, with data on a named volume, a `pg_isready` healthcheck, `restart: unless-stopped` on both, and the `app` service starting only after `db` is healthy.

#### Scenario: One-command start
- **WHEN** a user with a filled `.env` runs `docker compose up --build`
- **THEN** `db` becomes healthy, `app` starts, Flyway applies V1 and `/actuator/health` returns `UP`

#### Scenario: Data survives restart
- **WHEN** the stack is stopped and started again without removing volumes
- **THEN** previously stored data is still present

#### Scenario: Database not exposed publicly
- **WHEN** the base compose file is used on a server
- **THEN** the database port is not published on any host interface

### Requirement: Environment-file configuration
The `app` service SHALL read its configuration from a `.env` file. The repository SHALL contain `.env.example` listing every variable with placeholder values, and `.env` MUST be git-ignored.

#### Scenario: Example is complete
- **WHEN** `.env.example` is compared with the required variables (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USER_ID`, `GROQ_API_KEY`, `GEMINI_API_KEY`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_TIMEZONE`)
- **THEN** every variable appears with a non-secret placeholder

#### Scenario: Missing variable at container start
- **WHEN** the stack is started with a required variable absent from `.env`
- **THEN** the `app` container exits with an error naming the missing setting

### Requirement: Local development override
A compose override file SHALL, for local development only, publish the database port bound to `127.0.0.1` and MAY activate the `local` profile.

#### Scenario: Local DB access
- **WHEN** the stack runs with the override file present
- **THEN** the database is reachable from the host at `127.0.0.1` only
