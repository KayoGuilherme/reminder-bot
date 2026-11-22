# Proposal

## Why

The repository is a bare Spring Initializr skeleton (a single `BotApplication`, a two-line `application.yaml`, no migrations, no container or CI setup). Before building the Telegram handler, voice transcription, LLM parsing and reminder scheduler, we need a solid, single-instance-friendly base: validated fail-fast configuration, a Flyway-owned schema, a testable clock, reproducible container builds and CI. Doing this first keeps later changes small and focused on business logic.

## What Changes

- Establish the hexagonal package layout under `com.kayo.reminder` (`domain`, `application` with `port.in` / `port.out` / `service`, `infrastructure`, `config`). **BREAKING (internal)**: the generated package `com.telegram.reminder.bot` (and its `BotApplication` / `BotApplicationTests`) is moved to `com.kayo.reminder`.
- Add a `java.time.Clock` bean (system UTC) in `config`; JVM default timezone and Hibernate JDBC timezone are UTC; timestamps are `Instant` / `timestamptz`. The user display timezone is the `app.timezone` property (default `America/Fortaleza`).
- Add `@ConfigurationProperties("app")` + `@Validated` properties (Telegram token and allowed user id, Groq and Gemini keys, timezone) so startup fails fast with a clear error when a required value is missing.
- Configuration split into `application.yaml`, `application-local.yaml`, `application-prod.yaml`; all secrets and environment-specific values come from environment variables (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USER_ID`, `GROQ_API_KEY`, `GEMINI_API_KEY`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_TIMEZONE`). Nothing secret is committed.
- JPA `ddl-auto: validate`; Flyway owns the schema. Add Spring Boot Actuator exposing only `health` and `info`, with liveness/readiness probes. Conservative logging config that never logs secrets.
- Add Flyway migration `V1__create_tasks_table.sql` creating `tasks` with CHECK constraints and an index on `(status, deadline_at)`. No JPA entities or repositories yet.
- Add a multi-stage `Dockerfile` (JDK 21 build with Maven dependency caching, slim JRE 21 runtime, non-root user, container-aware JVM flags, `HEALTHCHECK` on Actuator) and `.dockerignore`.
- Add `docker-compose.yml` (`app` + `db` on pinned PostgreSQL major, named volume, `pg_isready` healthcheck, `depends_on: service_healthy`, `restart: unless-stopped`), `.env.example`, and a minimal `docker-compose.override.yml` for local development (db port bound to `127.0.0.1`).
- Add GitHub Actions: `ci` (build + tests on PR and push to `main`) and `docker` (after CI succeeds on `main`, build and push to `ghcr.io` tagged with commit SHA and `latest` using `GITHUB_TOKEN`). No automatic deployment.
- Housekeeping: extend `.gitignore` (`.env`), add a short `README.md` (what, local run, env vars, CI, running the published image on a server / home machine / Raspberry Pi), and one smoke test that boots the Spring context and applies Flyway against a real PostgreSQL (Testcontainers).

Out of scope: any business logic (Telegram handlers, STT, LLM parsing, scheduler jobs), JPA entities/repositories, and anything for horizontal scaling (Kubernetes, Redis, brokers, multi-replica concerns).

## Capabilities

### New Capabilities
- `app-configuration`: validated, fail-fast externalised configuration (`app.*` properties, env vars, profiles), UTC/timezone handling, injectable `Clock`, logging hygiene, Actuator health/info with probes.
- `task-storage-schema`: Flyway-managed PostgreSQL `tasks` table (columns, constraints, defaults, index) and the rule that Hibernate only validates the schema.
- `container-runtime`: Dockerfile and docker-compose stack (app + db) with healthchecks, non-root runtime, `.env`-based configuration and local override.
- `ci-delivery`: GitHub Actions CI (build + tests) and ghcr.io image publication on `main`, with no automatic deployment.

### Modified Capabilities
<!-- None: openspec/specs is empty. -->

## Impact

- **Code**: package rename to `com.kayo.reminder`; new `config` classes (`AppProperties`, `ClockConfig`); new test(s) under `src/test`.
- **Dependencies (`pom.xml`)**: add `spring-boot-starter-actuator`, Testcontainers (PostgreSQL + JUnit/Spring Boot integration); `groupId` aligned with the new package. Existing JPA, Flyway, validation, webmvc, PostgreSQL driver are retained. `spring-boot-devtools` and Lombok stay as is.
- **Resources**: `application.yaml` rewritten (and `application-local.yaml`, `application-prod.yaml` added); `db/migration/V1__create_tasks_table.sql`.
- **New root files**: `Dockerfile`, `.dockerignore`, `docker-compose.yml`, `docker-compose.override.yml`, `.env.example`, `README.md`, `.github/workflows/ci.yml`, `.github/workflows/docker.yml`; `.gitignore` updated.
- **Systems**: GitHub Container Registry (`ghcr.io`) for published images; PostgreSQL (pinned major) as the only runtime dependency.
