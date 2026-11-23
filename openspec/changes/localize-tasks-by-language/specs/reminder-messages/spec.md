## RENAMED Requirements

- FROM: `### Requirement: Language follows the configured language`
- TO: `### Requirement: Language follows the task`

## MODIFIED Requirements

### Requirement: Language follows the task
Reminders SHALL be rendered in the language of the task they concern (`en` or `pt-BR`, stored when the task is saved), not in `app.language`. Both languages MUST provide the same set of texts with the same inserted values. `app.language` remains the language of replies that are not about a task.

#### Scenario: Portuguese reminder
- **WHEN** a task saved with language `pt-BR` gets a reminder, whatever `app.language` is
- **THEN** the reminder text is in Brazilian Portuguese and contains the task title and the minutes

#### Scenario: English task
- **WHEN** a task saved with language `en` gets a reminder while `app.language` is `pt-BR`
- **THEN** the reminder text is in English

#### Scenario: Parity between languages
- **WHEN** the reminder text keys of the English and Portuguese resources are compared
- **THEN** they are identical

## ADDED Requirements

### Requirement: Dates follow the task language
Every date and time shown about a task SHALL use the date pattern of the task's language: `dd/MM/yyyy HH:mm` for `pt-BR` and `yyyy-MM-dd HH:mm` for `en`. This applies to the saved-task summary, the snoozed new deadline and the `/tasks` list. The time SHALL be shown in `app.timezone`.

#### Scenario: Portuguese deadline in the summary
- **WHEN** a pt-BR task due at 2026-11-23T12:00:00Z is confirmed and `app.timezone` is `America/Fortaleza`
- **THEN** the summary shows its deadline as `23/11/2026 09:00`

#### Scenario: Portuguese snoozed deadline
- **WHEN** a pt-BR task is snoozed to 2026-11-23T12:10:00Z in `America/Fortaleza`
- **THEN** the confirmation shows the new deadline as `23/11/2026 09:10`

#### Scenario: English snoozed deadline
- **WHEN** an en task is snoozed to 2026-11-23T12:10:00Z in `America/Fortaleza`
- **THEN** the confirmation shows the new deadline as `2026-11-23 09:10`
