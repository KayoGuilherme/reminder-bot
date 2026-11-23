# Spec Delta

## ADDED Requirements

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
