# Spec Delta

## ADDED Requirements

### Requirement: Telegram integration switch
The application SHALL provide the setting `app.telegram.enabled`, populated from `APP_TELEGRAM_ENABLED` and defaulting to `true`. When it is `false`, the application MUST NOT register with or poll Telegram, while all other behavior (configuration validation, database, health endpoints) is unchanged. A value that is not a boolean MUST cause startup to fail.

#### Scenario: Default
- **WHEN** the application starts without `APP_TELEGRAM_ENABLED`
- **THEN** `app.telegram.enabled` is `true`

#### Scenario: Switched off
- **WHEN** the application starts with `APP_TELEGRAM_ENABLED=false`
- **THEN** it starts successfully, `/actuator/health` reports `UP`, and no Telegram connection is made

#### Scenario: Invalid value
- **WHEN** the application starts with `APP_TELEGRAM_ENABLED=maybe`
- **THEN** startup fails with an error identifying the invalid setting

### Requirement: Bot language setting
The application SHALL provide the setting `app.language`, populated from `APP_LANGUAGE`, accepting exactly `en` or `pt-BR` and defaulting to `en`. Any other value MUST cause startup to fail with an error naming the setting.

#### Scenario: Default language
- **WHEN** the application starts without `APP_LANGUAGE`
- **THEN** `app.language` is `en`

#### Scenario: Portuguese
- **WHEN** the application starts with `APP_LANGUAGE=pt-BR`
- **THEN** `app.language` is `pt-BR`

#### Scenario: Unsupported language
- **WHEN** the application starts with `APP_LANGUAGE=fr`
- **THEN** startup fails with an error identifying `app.language`
