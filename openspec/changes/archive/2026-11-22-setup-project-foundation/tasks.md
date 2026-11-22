# Tasks

## 1. Project structure and dependencies

- [x] 1.1 Move `BotApplication` and its test to `com.kayo.reminder`, set `groupId` to `com.kayo.reminder`, remove the old `com.telegram.reminder.bot` directories; verify `./mvnw -q compile` succeeds and no `com.telegram` reference remains (`grep -r "com.telegram" src pom.xml`)
- [x] 1.2 Create hexagonal packages `domain`, `application.port.in`, `application.port.out`, `application.service`, `infrastructure`, `config` (with `package-info.java` where otherwise empty); verify the directories exist and the project still compiles
- [x] 1.3 Add `spring-boot-starter-actuator`, `spring-boot-testcontainers` and Testcontainers PostgreSQL / JUnit test dependencies (versions from the Boot BOM); verify `./mvnw -q dependency:resolve` succeeds

## 2. Configuration and time

- [x] 2.1 Rewrite `application.yaml` (common settings: `app.*` bound to the env vars with no secret defaults, `app.timezone: ${APP_TIMEZONE:America/Fortaleza}`, datasource from `DB_URL`/`DB_USER`/`DB_PASSWORD`, `ddl-auto: validate`, `open-in-view: false`, Hibernate JDBC time zone UTC, Flyway enabled, Actuator exposing only `health,info` with probes, logging levels) and add `application-local.yaml` and `application-prod.yaml`; verify by starting the app with all variables set and checking `/actuator/health`
- [x] 2.2 Implement validated `AppProperties` (nested records for telegram, groq, gemini, timezone) with masked secret `toString`, and register it; verify with an `ApplicationContextRunner` unit test that a missing token, blank API key, non-numeric user id and invalid timezone each fail startup with a message naming the property, and that valid values bind (timezone default applies)
- [x] 2.3 Add `config.ClockConfig` providing `Clock.systemUTC()`; verify with a test that the injected clock's zone is UTC and that a test-supplied `Clock` bean overrides it
- [x] 2.4 Verify Actuator exposure and secret hygiene in a test: `/actuator/health` and probes report `UP`, `/actuator/env` returns 404, and captured startup logs do not contain the test secret values

## 3. Database migration

- [x] 3.1 Create `src/main/resources/db/migration/V1__create_tasks_table.sql` per the spec (columns, defaults, named CHECK constraints, index on `(status, deadline_at)`); verify by applying it to a throwaway PostgreSQL (e.g. via the smoke test in 3.2)
- [x] 3.2 Write the smoke test (`@SpringBootTest` + Testcontainers PostgreSQL via `@ServiceConnection`, dummy `app.*` values) asserting the context loads, `flyway_schema_history` records V1 as successful, `tasks` has the expected columns, and the `(status, deadline_at)` index exists; verify `./mvnw verify` passes
- [x] 3.3 Add constraint tests in the same suite: insert with defaults yields `PENDING`/stage 0/timestamps set; invalid `urgency` and `status` and missing `deadline_at` are rejected; verify they pass in `./mvnw verify`

## 4. Container image and compose

- [x] 4.1 Write `.dockerignore` and the multi-stage `Dockerfile` (JDK 21 build with cached `dependency:go-offline`, layered extraction, JRE 21 runtime, non-root user, `JAVA_OPTS` with `-XX:MaxRAMPercentage` and `-Duser.timezone=UTC`, `HEALTHCHECK` on Actuator); verify `docker build -t reminder-bot:test .` succeeds, `docker run --rm reminder-bot:test id -u` is non-zero, and a rebuild after a source-only change reuses the dependency layer
- [x] 4.2 Write `docker-compose.yml` (`db` postgres pinned major with named volume, `pg_isready` healthcheck, `restart: unless-stopped`; `app` built from Dockerfile, `env_file: .env`, `depends_on` `service_healthy`, `restart: unless-stopped`, no published ports), `docker-compose.override.yml` (db and app ports on `127.0.0.1`, `local` profile) and `.env.example` with all variables and placeholders; verify `docker compose config` is valid and `.env.example` contains every required variable
- [x] 4.3 Verify end to end: copy `.env.example` to `.env`, run `docker compose up --build -d`, confirm `docker compose ps` shows both services healthy, Flyway logs V1 applied, `curl http://127.0.0.1:8080/actuator/health` returns `UP`; then remove `TELEGRAM_BOT_TOKEN` from `.env` and confirm the app container exits with a clear error naming the missing setting; finally `docker compose down`

## 5. CI/CD and documentation

- [x] 5.1 Add `.github/workflows/ci.yml` (pull_request + push to main, checkout, Java 21 temurin with Maven cache, `./mvnw -B verify`); verify the YAML with `actionlint` (or a YAML parse) and, if possible, a dry run with `act`
- [x] 5.2 Add `.github/workflows/docker.yml` (`workflow_run` on `ci` success for pushes to `main`, `packages: write` via `GITHUB_TOKEN`, login to ghcr.io, tags `sha-<sha>` and `latest`, lower-cased image name); verify with `actionlint` and by reviewing that no PR trigger can push
- [x] 5.3 Update `.gitignore` (`.env`, keep existing entries) and write `README.md` (what the project is, run locally with docker compose, required env vars table, how CI and image publishing work, pulling and running the published image on a server / home machine / Raspberry Pi with `docker compose -f docker-compose.yml`); verify `git check-ignore .env` reports it ignored and the documented commands match the files

## 6. Final integration checks

- [x] 6.1 From a clean clone (fresh `git clone` or `git stash -u` equivalent), run `./mvnw -B verify` and `docker compose up --build`; verify tests pass, health is `UP`, and `grep -rniE "(token|api_key|password)=.{8,}" --exclude-dir=target --exclude-dir=.git .` finds no real secrets in tracked files
- [x] 6.2 Run `openspec validate setup-project-foundation` and verify every acceptance criterion of the request is covered by a passing check above
