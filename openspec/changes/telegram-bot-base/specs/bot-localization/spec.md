# Spec Delta

## Purpose

Defines how the bot chooses the language of its messages, so every text shown to the user can be switched between English and Brazilian Portuguese through configuration and maintained outside the code.

## ADDED Requirements

### Requirement: Messages come from per-language bundles
Every user-facing text the bot sends SHALL be read from message bundles, one for English and one for Brazilian Portuguese, and SHALL NOT be hard-coded in application logic. The bundle used SHALL be the one for `app.language`.

#### Scenario: English
- **WHEN** `app.language` is `en` and the allowed user sends `/start`
- **THEN** the reply is the English usage message

#### Scenario: Brazilian Portuguese
- **WHEN** `app.language` is `pt-BR` and the allowed user sends `/start`
- **THEN** the reply is the Portuguese usage message

#### Scenario: Placeholder language
- **WHEN** `app.language` is `pt-BR` and the allowed user sends plain text
- **THEN** the "not supported yet" reply is in Portuguese

### Requirement: Language is independent of the host
The language used SHALL depend only on `app.language`, never on the operating system or JVM default locale, and a text missing from the selected bundle SHALL fall back to the English text rather than failing.

#### Scenario: Host locale differs
- **WHEN** the JVM default locale is `pt_BR` but `app.language` is `en`
- **THEN** replies are in English

#### Scenario: Missing translation
- **WHEN** a key exists in the English bundle but not in the Portuguese one and `app.language` is `pt-BR`
- **THEN** the English text is used

### Requirement: Bundles stay in sync
The English and Portuguese bundles MUST define the same set of keys.

#### Scenario: Key parity
- **WHEN** the two bundles are compared
- **THEN** every key of one is present in the other
