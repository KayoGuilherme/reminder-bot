# Proposal

## Why

The foundation change left the app configured but silent: it has Telegram credentials and the Telegram libraries on the classpath, yet it never connects to Telegram. Before any task, voice or scheduling logic can exist, the bot must be able to receive updates, accept them only from its single owner, and answer — with Telegram details kept behind ports so later business logic never touches them.

## What Changes

- Receive updates by long polling using the Telegram library already in `pom.xml` (`telegrambots` 10.3.0 long-polling Spring starter + client); no direct Bot API / `RestClient` code is needed.
- New setting `app.telegram.enabled` (env `APP_TELEGRAM_ENABLED`, default `true`) turns polling off for tests and local runs. New setting `app.language` (env `APP_LANGUAGE`, `en` or `pt-BR`, default `en`) selects the language of every bot message.
- **Allowlist**: only updates sent by `TELEGRAM_ALLOWED_USER_ID` are processed. Everyone else is ignored silently (no reply) and a WARN is logged containing the user id only — never message content.
- **Inbound adapter** converts a Telegram update into a Telegram-agnostic `IncomingCommand` (chat id, sender id, message id, text, voice file id + duration, callback id + data) and hands it to an inbound use case. Telegram classes are confined to `infrastructure.telegram`; a test enforces it.
- **Outbound port** `MessageSenderPort` in `application.port.out` with `sendText`, `sendWithButtons`, `editText`, `answerCallback`, each returning a `SendResult` the caller can check. The Telegram implementation honors 429 `retry_after`, retries transient failures a small bounded number of times with backoff, and never throws to the caller once retries are exhausted.
- **Behavior**: `/start` and `/help` reply with a short usage message; any other text and any voice message get a localized "not supported yet" placeholder. No task logic.
- **Resilience**: every update is processed inside a global error handler that logs the failure (update id only) and continues, so one bad update can never stop polling.
- **i18n**: all bot texts live in `messages.properties` (en) and `messages_pt_BR.properties`, resolved through Spring `MessageSource` for the configured language.
- Tests for allowlist behavior, update→command mapping, sender retry/429 handling against a mocked HTTP server, the global error handler and the package boundary. Integration tests switch polling off.
- Docs: README and `.env.example` updated with the new settings and behavior.

Out of scope: task creation/parsing, voice download and transcription, LLM calls, scheduler/reminders, persistence of any kind, webhook mode, multi-user support, and replying to the user when a handler fails.

## Capabilities

### New Capabilities
- `telegram-inbound`: long-polling reception, owner-only allowlist, conversion to a Telegram-agnostic command, `/start` + `/help` + placeholder replies, failure isolation, and the rule that Telegram types stay inside the Telegram adapter.
- `telegram-outbound`: the outbound messaging contract (send text, send with buttons, edit text, answer callback) with checkable results, rate-limit (429) handling, bounded retry with backoff and no exceptions leaking to callers.
- `bot-localization`: all user-facing bot texts come from per-language message bundles for `en` and `pt-BR`, selected by `app.language`.

### Modified Capabilities
- `app-configuration`: adds requirements for the `app.telegram.enabled` switch and the validated `app.language` setting (both with defaults and fail-fast on invalid values).

## Impact

- **Code**: new `application.port.in` (inbound use case + `IncomingCommand`), `application.port.out` (`MessageSenderPort`, `SendResult`, `Button`), `application.service` (command handling, message lookup), `infrastructure.telegram` (bot registration, update consumer, mapper, allowlist, sender, client config); `AppProperties` gains `enabled`, `language` and retry settings.
- **Dependencies**: none added — `telegrambots-springboot-longpolling-starter` and `telegrambots-client` 10.3.0 are already declared. Tests use JUnit/Mockito/AssertJ already on the test classpath and the JDK's built-in `HttpServer` as the fake Telegram API.
- **Resources**: `application.yaml` (new `app.*` keys, `spring.messages.*`), new `messages.properties` and `messages_pt_BR.properties`.
- **Existing tests**: `AbstractIntegrationTest` must set `app.telegram.enabled=false` so the Spring context does not poll Telegram with a fake token.
- **Docs/config**: `README.md`, `.env.example`.
- **Systems**: outbound HTTPS to `api.telegram.org` (long polling), no new inbound ports.
