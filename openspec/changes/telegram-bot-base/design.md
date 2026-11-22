# Design

## Context

See proposal.md for motivation and the specs for required behavior. Observed state:

- `pom.xml` already declares `org.telegram:telegrambots-springboot-longpolling-starter` and `telegrambots-client`, both `10.3.0` (property `telegrambots.version`); both are in the local Maven repo. The starter auto-configures a `TelegramBotsLongPollingApplication` and a `TelegramBotInitializer` that registers every `SpringLongPollingBot` bean. So no direct Bot API / `RestClient` code is needed.
- Library facts checked from the jars: `SpringLongPollingBot` has `getBotToken()` and `getUpdatesConsumer()`; `LongPollingUpdateConsumer.consume(List<Update>)`; `TelegramClient` (`execute`, `OkHttpTelegramClient(token[, TelegramUrl])`); API failures surface as `TelegramApiRequestException` exposing `getErrorCode()` and `getParameters().getRetryAfter()`; `Voice` has `getFileId()`/`getDuration()`.
- `AppProperties` (`@Validated` record, `@ConfigurationPropertiesScan`) already holds `telegram.botToken` and `telegram.allowedUserId` and masks secrets in `toString`.
- Hexagonal packages exist (`application.port.in`, `application.port.out`, `application.service`, `infrastructure`, `config`) but only contain `package-info.java`.
- `AbstractIntegrationTest` boots the full context with a fake bot token (`test-bot-token-9f8e7d`) and asserts the token is never logged.
- Single instance, single allowed user, Spring Boot 4.2.0, Java 21.

## Goals / Non-Goals

**Goals:**
- A thin Telegram adapter (`infrastructure.telegram`) on both sides of two small ports; the application layer has no Telegram imports.
- Deterministic, fast tests: no sleeping, no real network.
- Failure isolation: nothing thrown by a handler or by Telegram can stop polling or reach a caller of the sender.

**Non-Goals:**
- No concurrency/async design: a single user means updates are handled sequentially on the polling callback.
- No persistence, task parsing, voice download, STT or LLM.
- No error reply to the user when a handler fails (only logging); can be added with the first real handler.
- No webhook mode, multi-user allowlist or group-chat policy.

## Decisions

1. **Use the existing `telegrambots` starter; register a `SpringLongPollingBot` bean.** `TelegramBot` implements `SpringLongPollingBot`, returns the token from `AppProperties` and the update consumer. The starter's initializer does the registration. *Alternative*: raw `RestClient` calling `getUpdates` — rejected: reimplements offset handling, backoff and JSON models that the dependency (already present) provides.

2. **`app.telegram.enabled` gates the bot bean with `@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)`** on the whole Telegram adapter configuration (bot, consumer, client, sender). With no `SpringLongPollingBot` bean the starter registers nothing, so no connection is made. The `MessageSenderPort` bean therefore does not exist when disabled; application services that need it are also conditional on the same property (see 3). `enabled` is also a field of `AppProperties.Telegram` (`@DefaultValue("true")`) so it is validated and documented, and `application.yaml` binds it to `${APP_TELEGRAM_ENABLED:true}`. *Alternative*: exclude the starter's auto-configuration when disabled — not possible per-property without an `EnvironmentPostProcessor`; the conditional bean is simpler. Bot token stays required even when disabled (existing fail-fast spec unchanged); tests already supply one.

3. **Ports and application service.**
   - `application.port.in`: `IncomingCommand` (record: `chatId`, `userId`, `messageId`, `text`, `voiceFileId`, `voiceDurationSeconds`, `callbackId`, `callbackData`, nullable fields; a derived `kind()` of `TEXT | VOICE | CALLBACK`; static factories enforce that exactly one kind is populated) and `HandleIncomingCommandUseCase.handle(IncomingCommand)`.
   - `application.port.out`: `MessageSenderPort`, `Button(text, callbackData)` (callback data ≤ 64 UTF-8 bytes, validated in the constructor), `SendResult(success, messageId, failure)` with `FailureReason { RATE_LIMITED, TRANSIENT, REJECTED, UNEXPECTED }`. `sendWithButtons` takes `List<List<Button>>` (rows).
   - `application.service.IncomingCommandService` implements the use case: strips `@botname` and arguments from a leading `/command`, routes `/start`, `/help` → usage text, any other text or voice → "not supported yet", callback → `answerCallback` only. It depends only on `MessageSenderPort` and a small `Messages` helper. Failed `SendResult`s are not thrown on; the sender has already logged them.
   - `application.service.Messages` wraps Spring `MessageSource` + the configured `Locale` (`get(key, args…)`). Using `MessageSource` in the application layer is acceptable: it is a Spring abstraction, not an infrastructure detail.
   The service is `@ConditionalOnProperty` like the adapter (no sender → nothing to serve).
   *Alternative*: making `IncomingCommand` a sealed interface with three records — cleaner types but more mapping/test code for a three-variant, field-light object; revisit when business logic needs pattern matching.

4. **Allowlist lives in the inbound adapter, before mapping.** A small `UpdateAllowlist` (infrastructure.telegram) extracts the sender id (`message.from`, `callback_query.from`) and compares it with `AppProperties.telegram.allowedUserId`. Rejected updates produce `log.warn("Ignoring update from unauthorized user id={}", userId)` — nothing else from the update is logged — and never become commands, so no application code can accidentally answer a stranger. Updates without a sender are dropped at DEBUG. The check uses the sender id, not the chat id (differs in groups). *Alternative*: put `userId` in the command and check in the service — leaks the security decision into the application layer and risks forgetting it in future handlers.

5. **Mapping (`TelegramUpdateMapper`, pure function `Update → Optional<IncomingCommand>`).** Text message → TEXT; voice → VOICE (`fileId`, `duration`); callback query → CALLBACK (callback id, data, chat id and message id from the attached message; an inaccessible/missing message yields empty). Everything else → empty. Unit-testable with hand-built `Update` objects.

5b. **Global error handler in `TelegramUpdateConsumer.consume(List<Update>)`.** For each update: `try { allowlist → map → useCase.handle } catch (Exception e) { log.error("Failed to process update id={}", id, e); }`. Message/update contents are never logged; only the id. Errors do not escape `consume`, so the library's polling loop is unaffected. Sequential processing preserves order for the single user. *Alternative*: `LongPollingSingleThreadUpdateConsumer` (library executor per update) — rejected: hides ordering/error behavior behind the library and gains nothing at this volume.

6. **Sender (`TelegramMessageSender implements MessageSenderPort`) wraps `TelegramClient` with a retry loop.**
   - Operations: `SendMessage`, `SendMessage` + `InlineKeyboardMarkup`, `EditMessageText`, `AnswerCallbackQuery`. A generic `execute(op, name, chatId, Callable)` helper centralizes classification and retry.
   - Classification of `TelegramApiException`: `TelegramApiRequestException` with code 429 → rate limit; code ≥ 500, or a cause/`IOException` (timeouts, connect errors) → transient; other 4xx → rejected (no retry). EditMessageText "message is not modified" (400) → treated as success. Anything else (including `RuntimeException`) → `UNEXPECTED`, no retry.
   - 429: wait `retry_after` seconds, only if ≤ `maxRetryAfter` (default 30 s); otherwise return `RATE_LIMITED` immediately so the single polling thread is never parked for minutes. Each retry counts against `maxAttempts`.
   - Transient: exponential backoff `initialBackoff * 2^(attempt-1)` capped at `maxBackoff`. Defaults: `maxAttempts=3`, `initialBackoff=500ms`, `maxBackoff=5s`. No jitter (single client).
   - Waiting goes through a tiny `Sleeper` interface (default `Thread::sleep`), injected so tests assert the requested delays with no real waiting. An `InterruptedException` re-sets the interrupt flag and returns `UNEXPECTED`.
   - Never throws: the whole operation is in a catch-all; the final failure is logged once at WARN with operation, chat id and reason. Exception messages are passed through a `redact(token)` step because HTTP client errors can embed the request URL, which contains the bot token.
   - Settings come from `AppProperties.Telegram.retry` (`maxAttempts`, `initialBackoff`, `maxBackoff`, `maxRetryAfter` as `Duration`, defaults via `@DefaultValue`), so no new env vars are required.
   *Alternative*: Spring Retry / Resilience4j — rejected: a new dependency for a ~60-line loop whose delays must be driven by Telegram's `retry_after`.

7. **Client bean.** `TelegramClient` is an `OkHttpTelegramClient(botToken)` bean declared in `infrastructure.telegram` (not `config`, so Telegram imports stay in one package). Tests build the sender with an `OkHttpTelegramClient` whose `TelegramUrl` points at a local JDK `com.sun.net.httpserver.HttpServer`, giving real HTTP and real exception mapping with no new test dependency. If the library's 429 mapping cannot be reproduced that way, the fallback is a Mockito `TelegramClient` throwing `TelegramApiRequestException` built with a `ResponseParameters(retryAfter)`.

8. **Language.** `AppProperties.language` (`@NotNull @Pattern(regexp = "en|pt-BR")`, default via `${APP_LANGUAGE:en}`). A `Locale` is derived with `Locale.forLanguageTag`. `spring.messages.basename=messages` (Boot default), `spring.messages.fallback-to-system-locale=false` (so a `pt_BR` host never changes English output), `spring.messages.use-code-as-default-message=false`. `messages.properties` holds English (the fallback), `messages_pt_BR.properties` Portuguese; keys such as `usage`, `not-supported-yet`. Properties files are UTF-8 (Boot's default encoding). A parity test compares the two key sets.

9. **Boundary enforcement.** A test scans `src/main/java` and fails if any file outside `com/kayo/reminder/infrastructure/telegram` contains `import org.telegram`. Cheap, dependency-free (no ArchUnit), and covers `config`, `application` and `domain` together.

10. **Logging.** `logging.level.org.telegram` stays at the Spring default for prod; we add nothing at DEBUG that could print updates. Our own logs carry ids, kinds and reasons only. Existing `secretsNotLogged` test remains the guard; a new sender test asserts the token is absent from failure logs.

11. **Existing test impact.** `AbstractIntegrationTest` sets `app.telegram.enabled=false`; otherwise the fake token would make the context poll Telegram. An extra context test (`ApplicationContextRunner`) asserts the Telegram beans are absent when disabled and present when enabled (with a mock `TelegramClient`, no network).

## Risks / Trade-offs

- [The starter's `TelegramBotsLongPollingApplication` bean exists even when no bot is registered] → It does nothing until `registerBot`; verify in the disabled-context test that no thread/connection to Telegram is started. If it proves noisy, exclude the auto-configuration for `enabled=false` via a conditional import.
- [Library behavior for 429/`retry_after` may differ from the Bot API docs] → The fake-server test pins the real mapping; fallback to a mocked client documented in decision 7.
- [Bad token or network down at startup] → Library backs off and retries; not simulated here. Acceptance is checked manually with a real token. Mitigation: a bad token logs an error from the library (never our token, see redaction).
- [Blocking the polling thread while retrying] → Bounded by `maxAttempts` × `maxBackoff` and `maxRetryAfter` (worst case ~1 min for a rare 429); acceptable for a single user, revisit with async processing if handlers get slow.
- [Unauthorized-user WARN flooding logs] → Accepted for a private bot; rate-limiting log lines is a later hardening.
- [Placeholder text hard to translate later] → Texts are keys from day one; adding a language is a new bundle plus allowing the value in `AppProperties`.
- [Application layer using `MessageSource`] → Small, deliberate coupling to a Spring interface; isolated in `Messages`.

## Migration Plan

No data migration. Deploy by setting the existing `TELEGRAM_*` variables (new optional: `APP_LANGUAGE`, `APP_TELEGRAM_ENABLED`). Rollback: set `APP_TELEGRAM_ENABLED=false` or redeploy the previous image tag; nothing persists.

## Open Questions

- Should the bot reply with a generic "something went wrong" message when a handler fails? Deferred: it needs the sender to be usable from the error handler and does not change specs or tasks here.
