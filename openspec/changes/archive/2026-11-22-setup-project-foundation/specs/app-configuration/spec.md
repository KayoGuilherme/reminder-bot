## Purpose

Defines how the application is configured, which values it requires at startup, how time and timezones are handled, and which operational endpoints it exposes. It is the contract every later feature relies on for settings and time.

## ADDED Requirements

### Requirement: Required settings come from environment variables
The application SHALL read the Telegram bot token, the allowed Telegram user id, the Groq API key, the Gemini API key, the database URL, user and password from the environment variables `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USER_ID`, `GROQ_API_KEY`, `GEMINI_API_KEY`, `DB_URL`, `DB_USER` and `DB_PASSWORD`. No secret or environment-specific value SHALL be committed to the repository.

#### Scenario: All variables provided
- **WHEN** the application starts with every required variable set
- **THEN** startup completes and the values are available to application code through the `app.*` settings

#### Scenario: No secrets in the repository
- **WHEN** the committed configuration files and `.env.example` are inspected
- **THEN** they contain only placeholders or empty references to environment variables, never real credentials

### Requirement: Fail fast on missing or invalid settings
The application MUST refuse to start, with an error message naming the missing or invalid setting, when any required `app.*` value is absent or blank, or when `TELEGRAM_ALLOWED_USER_ID` is not a valid number.

#### Scenario: Missing required variable
- **WHEN** the application starts without `TELEGRAM_BOT_TOKEN`
- **THEN** startup fails and the error output identifies the missing Telegram bot token setting

#### Scenario: Invalid user id
- **WHEN** the application starts with `TELEGRAM_ALLOWED_USER_ID=abc`
- **THEN** startup fails with an error identifying the invalid setting

### Requirement: Configuration profiles
The application SHALL provide a common configuration plus `local` and `prod` profiles. The `prod` profile MUST NOT enable verbose or debug logging of application internals.

#### Scenario: Local profile
- **WHEN** the application starts with the `local` profile active
- **THEN** it uses local-development settings while still reading secrets from environment variables

#### Scenario: Prod profile
- **WHEN** the application starts with the `prod` profile active
- **THEN** logging is at informational level or quieter for application and framework packages

### Requirement: UTC internally, display timezone configurable
The application SHALL operate in UTC internally (JVM default timezone, database session and persisted timestamps) and SHALL expose the user's display timezone as the setting `app.timezone`, populated from `APP_TIMEZONE` and defaulting to `America/Fortaleza`. An unknown timezone id MUST cause startup to fail.

#### Scenario: Default timezone
- **WHEN** the application starts without `APP_TIMEZONE`
- **THEN** `app.timezone` is `America/Fortaleza` and internal time handling remains UTC

#### Scenario: Invalid timezone
- **WHEN** the application starts with `APP_TIMEZONE=Not/AZone`
- **THEN** startup fails with an error identifying the invalid timezone

### Requirement: Injectable clock
The application SHALL provide a single injectable UTC clock as the source of "now" for all time-based logic, so that tests can substitute a fixed or controllable clock.

#### Scenario: Clock is UTC
- **WHEN** the clock is obtained from the application context
- **THEN** its zone is UTC

#### Scenario: Clock is replaceable in tests
- **WHEN** a test context supplies its own clock
- **THEN** application components obtain time from that clock

### Requirement: Health and info endpoints only
The application SHALL expose only the `health` and `info` management endpoints, including liveness and readiness probe endpoints, and MUST NOT expose any other management endpoint over HTTP.

#### Scenario: Health is UP
- **WHEN** a client requests `/actuator/health` on a running application with a reachable database
- **THEN** the response reports status `UP`

#### Scenario: Probes
- **WHEN** a client requests `/actuator/health/liveness` and `/actuator/health/readiness`
- **THEN** each responds with status `UP` while the application is running and ready

#### Scenario: Other endpoints hidden
- **WHEN** a client requests `/actuator/env` or `/actuator/beans`
- **THEN** the response is 404

### Requirement: Secrets never logged
The application MUST NOT write the values of secret settings (bot token, API keys, database password) to its logs, including at startup.

#### Scenario: Startup logs
- **WHEN** the application starts with real-looking secret values
- **THEN** none of those values appear in the log output
