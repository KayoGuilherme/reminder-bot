# Spec Delta

## ADDED Requirements

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
