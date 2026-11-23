# app-configuration Specification

## Purpose

Defines how the application is configured, which values it requires at startup, how time and timezones are handled, and which operational endpoints it exposes. It is the contract every later feature relies on for settings and time.

## Requirements

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

### Requirement: Scheduler settings
The application SHALL accept `app.scheduler.enabled` (boolean, default true) and `app.scheduler.interval` (duration, default 30 seconds). The interval MUST be greater than zero; an invalid value MUST stop startup with an error naming the setting.

#### Scenario: Defaults
- **WHEN** the application starts without any `app.scheduler.*` setting
- **THEN** the scheduler is enabled with a 30-second interval

#### Scenario: Overridden interval
- **WHEN** `app.scheduler.interval` is set to `1m`
- **THEN** the job waits one minute between runs

#### Scenario: Invalid interval
- **WHEN** `app.scheduler.interval` is set to `0s` or a negative value
- **THEN** startup fails and the error output names `app.scheduler.interval`

### Requirement: Overdue reminder settings
The application SHALL expose `app.reminder.repeat-interval`, the wait between HIGH overdue reminders (default 2 minutes, populated from `APP_REMINDER_REPEAT_INTERVAL`), and `app.reminder.max-repeats`, the number of overdue reminders sent before the final stop message (default 10, populated from `APP_REMINDER_MAX_REPEATS`). The interval MUST be greater than zero and the cap at least 1; an invalid value MUST cause startup to fail with an error naming the setting. The settings are optional and are documented in `.env.example` and the README.

#### Scenario: Defaults
- **WHEN** the application starts without any reminder settings
- **THEN** the repeat interval is 2 minutes and the cap is 10

#### Scenario: Custom values
- **WHEN** the application starts with `APP_REMINDER_REPEAT_INTERVAL=1m` and `APP_REMINDER_MAX_REPEATS=3`
- **THEN** HIGH overdue reminders are spaced 1 minute apart and the final message follows the third

#### Scenario: Invalid interval
- **WHEN** the application starts with `APP_REMINDER_REPEAT_INTERVAL=0s`
- **THEN** startup fails with an error identifying the repeat interval setting

#### Scenario: Invalid cap
- **WHEN** the application starts with `APP_REMINDER_MAX_REPEATS=0`
- **THEN** startup fails with an error identifying the max repeats setting

### Requirement: Retention, retry and Telegram timeout settings
The application SHALL read these optional settings, each with the stated default, from environment variables, and MUST fail startup with an error naming the setting when a value is invalid (not a number or duration, or out of range): `app.retention.days` from `APP_RETENTION_DAYS` (30, at least 1), `app.retention.enabled` from `APP_RETENTION_ENABLED` (true), `app.resilience.retry-backoff` from `APP_RETRY_BACKOFF` (500ms, greater than zero), `app.telegram.connect-timeout` from `APP_TELEGRAM_CONNECT_TIMEOUT` (5s) and `app.telegram.read-timeout` from `APP_TELEGRAM_READ_TIMEOUT` (30s), the last two greater than zero. Nothing needs to be set for the defaults to apply, and `.env.example` MUST list each as a commented-out optional variable.

#### Scenario: Defaults
- **WHEN** the application starts without any of these variables
- **THEN** retention is enabled with 30 days, the retry backoff is 500 ms and the Telegram timeouts are 5 s and 30 s

#### Scenario: Override
- **WHEN** `APP_RETENTION_DAYS=90` is set
- **THEN** `app.retention.days` is 90

#### Scenario: Invalid value
- **WHEN** `APP_RETRY_BACKOFF=0s` is set
- **THEN** startup fails with an error identifying `app.resilience.retry-backoff`

### Requirement: Graceful shutdown timeout is configurable
The application SHALL wait for in-flight work on shutdown for `app.shutdown.timeout` (from `APP_SHUTDOWN_TIMEOUT`, default 30s, greater than zero) before exiting, as described by the reminder dispatch requirements.

#### Scenario: Default
- **WHEN** the application starts without `APP_SHUTDOWN_TIMEOUT`
- **THEN** the grace period for in-flight work is 30 seconds

#### Scenario: Invalid value
- **WHEN** `APP_SHUTDOWN_TIMEOUT=-5s` is set
- **THEN** startup fails with an error identifying `app.shutdown.timeout`

### Requirement: MCP settings come from environment variables
The application SHALL read whether MCP is enabled and its bearer token from the environment variables `MCP_ENABLED` (default `false`) and `MCP_TOKEN` (default empty), exposed as the `app.mcp.*` settings. The token is a secret: it MUST NOT be committed to the repository, and `.env.example` MUST hold only an empty placeholder for it. The token MUST be required, validated and kept out of logs as described in the `mcp-access` capability.

#### Scenario: Defaults
- **WHEN** the application starts without `MCP_ENABLED` and `MCP_TOKEN`
- **THEN** `app.mcp.enabled` is false and startup completes

#### Scenario: No token in the repository
- **WHEN** the committed configuration files and `.env.example` are inspected
- **THEN** they contain no MCP token value

#### Scenario: Token never logged
- **WHEN** the application starts with MCP enabled
- **THEN** no startup log line, including the printed settings, contains the token value

### Requirement: The MCP endpoint is not a management endpoint
The `/mcp` path SHALL be an application endpoint protected by its own token. Enabling it MUST NOT expose any additional management endpoint: only `health` and `info` remain reachable under the management paths.

#### Scenario: Management surface unchanged
- **WHEN** MCP is enabled and a client requests `/actuator/env` or `/actuator/beans`
- **THEN** the response is 404

#### Scenario: Health stays public to the probe
- **WHEN** MCP is enabled and a client requests `/actuator/health` without any token
- **THEN** the response reports status `UP`
