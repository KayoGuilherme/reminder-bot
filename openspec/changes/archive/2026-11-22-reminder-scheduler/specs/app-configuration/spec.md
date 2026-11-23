# Spec Delta

## ADDED Requirements

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
