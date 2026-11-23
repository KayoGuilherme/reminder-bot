# Design

## Context

Existing code (see proposal.md for motivation):

- Hexagonal layout under `com.kayo.reminder`: `domain` (empty), `application.port.in` (`HandleIncomingCommandUseCase`, `IncomingCommand` with `kind()` TEXT/VOICE/CALLBACK), `application.port.out` (`MessageSenderPort` returning `SendResult`, `Button`), `application.service` (`IncomingCommandService`, `Messages`), `infrastructure.telegram` (the only place allowed to import `org.telegram`, enforced by a test), `config` (`AppProperties` record, UTC `Clock` bean).
- `IncomingCommandService` and the whole Telegram adapter exist only when `app.telegram.enabled` is true. `AbstractIntegrationTest` runs with it false, with a Testcontainers PostgreSQL.
- Table `tasks` (V1) already has every column needed; `spring.jpa.hibernate.ddl-auto=validate`. There is no entity yet.
- The update consumer handles updates one at a time, in order. A slow handler delays later updates.
- `AppProperties` already requires `app.groq.api-key` and `app.gemini.api-key`. Secrets are masked in `toString()`.
- `pom.xml` already has `spring-boot-starter-webmvc` (gives `RestClient` and Jackson), data-jpa, validation. Nothing needs adding.
- `telegram-bot-base` is implemented but not archived (task 5.4, a manual check, is open). This change builds on its ports.

## Goals / Non-Goals

**Goals:**
- Keep business rules (validation, flow, drafts) free of Telegram, HTTP and JPA types; adapters are replaceable and the use case is testable with mocked ports.
- Treat all model output as untrusted input and keep the user in the loop (confirmation) for the one thing code cannot verify: whether the model understood the time correctly.
- Idempotent confirm without relying on timing or the UI.

**Non-Goals:**
- Durable drafts, multi-instance support, progress indicators ("transcribing…"), editing a draft, any scheduling.
- Making the model's date arithmetic correct; the code only validates shape and plausibility.

## Decisions

### 1. Package placement

| Piece | Package |
|---|---|
| `Task`, `Urgency`, `TaskStatus`, `TaskValidationException` | `domain` |
| `TaskRepository`, `AudioDownloadPort`, `TranscriptionPort`, `TaskParserPort`, `ParsedTask`, `ExternalServiceException` | `application.port.out` |
| `CreateTaskFromVoiceUseCase` (inbound: `submit(IncomingCommand)`, `confirm(...)`, `cancel(...)`) | `application.port.in` |
| `CreateTaskFromVoiceService`, `DraftStore`, `Draft`, `TaskSummaryFormatter` | `application.service` |
| `TelegramAudioDownloader` | `infrastructure.telegram` |
| `GroqTranscriptionClient` | `infrastructure.stt` |
| `ChatCompletionsTaskParser`, `TaskParserPrompt`, `ParsedTaskValidator` | `infrastructure.llm` |
| `TaskEntity`, `JpaTaskRepository` | `infrastructure.persistence` |
| shared single-retry helper | `infrastructure.http` |

`TaskRepository` lives in `application.port.out` next to the other ports (the user's list names it as a port; there is no separate `domain.port` convention in the codebase). The Telegram boundary test keeps passing because only `TelegramAudioDownloader` touches Telegram types.

### 2. Port failures are exceptions, not result objects
`MessageSenderPort` returns `SendResult` because sending is fire-and-forget and callers mostly ignore the outcome. The new ports return data, so the natural shape is a value plus an unchecked `ExternalServiceException(Kind)` with `Kind ∈ {UNAVAILABLE, REJECTED, INVALID_RESPONSE}` (UNAVAILABLE = timeout/network/429/5xx after the retry; REJECTED = other 4xx such as a bad key; INVALID_RESPONSE = unparseable or empty). The exception message never carries payloads. *Alternative:* a `Result<T>` type — more ceremony for three call sites; revisit if more ports appear.

The use case maps kinds to messages: transcription UNAVAILABLE/REJECTED → "couldn't process the audio, try again"; transcription INVALID_RESPONSE (including an empty transcript) → "couldn't hear anything intelligible"; parser INVALID_RESPONSE → "couldn't understand, rephrase"; parser UNAVAILABLE/REJECTED → "couldn't process, try again". Messages never include exception text.

### 3. Domain validation and rehydration
`Task.create(chatId, title, urgency, startAt, deadlineAt, now)` enforces the rules (trimmed non-blank title ≤ 500, urgency non-null, `deadlineAt > now`, `startAt ≤ deadlineAt`) and returns a `PENDING` task. `Task.restore(...)` rebuilds a task from storage **without** the future-deadline check, because stored tasks legitimately have deadlines in the past. `TaskValidationException` carries a rule enum, never the title.

### 4. Draft id is the task id
The draft gets a random UUID that becomes the task's primary key on Confirm. Button data is `task:ok:<uuid>` / `task:no:<uuid>` (≤ 49 bytes, under the 64-byte `Button` limit). Even if the in-memory guard failed, a second save of the same id hits the primary key and cannot create a duplicate row.

### 5. `DraftStore`: atomic take, lazy expiry, restore on failure
A `ConcurrentHashMap<UUID, Draft>`; each `Draft` holds its chat id, parsed fields and `expiresAt` (= creation + `app.draft.ttl`, from the injected `Clock`). `take(id)` uses `remove` — atomic, so of two concurrent Confirms exactly one gets the draft; the other sees empty. `take` returns empty for an expired draft (and the entry is gone either way). Expired entries are also swept on `put`, so the map cannot grow without bound without a scheduler thread. A press from a chat other than the draft's chat is treated as "not found". If saving fails after `take`, the service calls `restore(draft)` (same `expiresAt`, so the TTL is not extended) and tells the owner to retry. *Alternative:* a `DraftStorePort` — unnecessary for a single in-memory implementation; the class is small and unit-tested directly with a controllable `Clock`.

If a retry after a failed save hits the primary key (the first attempt actually committed), the service treats it as already saved and shows the saved confirmation.

### 6. Persistence with `EntityManager.persist`, not `save`
Spring Data's `save` on an entity with an assigned id calls `merge`, which would silently overwrite an existing row and violate the "stored at most once" requirement. The adapter therefore uses `EntityManager.persist` + `flush` inside `@Transactional` (so a duplicate id surfaces as an exception) and `find` for reads. The entity maps all V1 columns, including `last_reminder_stage` as a `short` (SMALLINT; `int` would fail schema validation) and `Instant` for `TIMESTAMPTZ`, enums as `STRING`. `created_at`/`updated_at` come from the application clock. **No migration**: V1 has every column and constraint this feature needs; the startup validation test proves the mapping matches.

### 7. Parser contract and validation
`TaskParserPort.parse(String text) -> ParsedTask(title, Urgency|null, Instant|null startAt, Instant|null deadlineAt)`.

- Request: `POST {app.llm.base-url}/chat/completions`, `Authorization: Bearer <key>`, `temperature: 0`, `response_format: {"type":"json_object"}`, a `system` message (rules + current time) and the user's text as the `user` message, so the text is data rather than instructions. The reply is read from `choices[0].message.content`.
- System prompt (English instructions, pt/en examples) states: the current local date-time **with offset** in `app.timezone` (from the `Clock`); "até as 18h"/"by 6pm" = today at that time; "daqui a 2 horas"/"in 2 hours" is relative to the stated now; urgency vocabulary in pt and en → `LOW|MEDIUM|HIGH`; unstated fields are `null`; output only the JSON object `{title, urgency, startAt, deadlineAt}` with ISO-8601 offset times.
- `ParsedTaskValidator` is a pure function (JSON text + zone → `ParsedTask` or INVALID_RESPONSE), applying the rules in the `task-parsing` spec: strip a markdown fence, require an object, non-blank title ≤ 500, tolerant urgency enum, ISO date-times with offset or local-in-`app.timezone`, invalid optional values become absent, start after deadline drops the start, unknown fields ignored. Bad optional values degrade to "absent" because the flow already handles absent deadline (ask to resend) and absent urgency (default MEDIUM, flagged); only a missing/invalid title is fatal.
- The parser does **not** judge whether the deadline is in the past; the use case does, using the same `Clock`, so that decision is unit-testable without HTTP.

The prompt builder and validator are separate pure classes so the table-driven tests (fixed `Clock`, e.g. 2026-11-22T13:00:00Z → 10:00 -03:00) need no network. Relative-time correctness by the real model cannot be unit-tested; tests assert that the prompt carries the right now and rules and that canned model answers are validated correctly. The real model is checked once manually (task 7.x).

### 8. Transcription client
`POST {app.stt.base-url}/audio/transcriptions`, `multipart/form-data`: `file` (ByteArrayResource with filename `voice.ogg`, part content type `audio/ogg`), `model` = `app.stt.model` (default `whisper-large-v3-turbo`), `language` = `app.stt.language` (default `pt`), `response_format=json`, `temperature=0`; bearer = `app.groq.api-key`. Reads `text`; blank → INVALID_RESPONSE.

### 9. HTTP clients, timeouts and the single retry
Both HTTP adapters use `RestClient` with connect/read timeouts from `app.stt.*` / `app.llm.*` (defaults 5 s / 30 s). A small shared helper runs a call, and on a transient failure (`ResourceAccessException` for I/O and timeouts, HTTP 429, HTTP ≥ 500) waits a short fixed delay and tries exactly once more; HTTP 4xx other than 429 is not retried. No request/response logging interceptor is registered; failure logs include only adapter name, `Kind`, and status/exception class — never `getMessage()` of an HTTP exception (it can embed response bodies) and never headers. How timeouts are applied to `RestClient` (Boot's request-factory settings vs. a `JdkClientHttpRequestFactory` configured by hand) is to be checked against Spring Boot 4.2 when implementing; the behavior in the specs is what matters.

The Telegram download uses a dedicated `OkHttpTelegramClient` (own `OkHttpClient` with the configured timeouts) for `GetFile` + file download, so changing its timeouts does not affect the message sender; same single-retry helper. Whether the 10.3.0 constructor accepts a custom `OkHttpClient` is verified at implementation; fallback is the default client plus a watchdog `Future.get(timeout)`.

### 10. Configuration
`AppProperties` gains `Stt(language, model, baseUrl, connectTimeout, readTimeout)`, `Llm(baseUrl, model, apiKey, connectTimeout, readTimeout)`, `Voice(maxSeconds)`, `Draft(ttl)`, all with `@DefaultValue`s and validation (positive durations/numbers, non-blank strings). `application.yaml`: `app.llm.api-key: ${LLM_API_KEY:${GROQ_API_KEY:}}`; `app.llm.base-url: ${LLM_BASE_URL:https://api.groq.com/openai/v1}`; Gemini is a pure env switch (`https://generativelanguage.googleapis.com/v1beta/openai`). `app.llm.model` default is a currently available Groq chat model — the exact id must be confirmed against Groq's model list at implementation because model ids are deprecated regularly. `Llm.toString()` masks the key.

### 11. Routing and the use case
`IncomingCommandService` keeps slash-command handling, and delegates: VOICE and non-slash TEXT → `submit`; CALLBACK → always `answerCallback`, then if the data matches `task:ok:`/`task:no:` + UUID → `confirm`/`cancel`. `CreateTaskFromVoiceService` is gated by the same `app.telegram.enabled` condition as `IncomingCommandService` (it needs `MessageSenderPort`).

`submit` order: duration > `app.voice.max-seconds` → reject (nothing downloaded) → download → transcribe (blank = no speech) → parse → deadline missing/`<= now` → ask to resend → urgency absent → MEDIUM + `assumed` flag → create draft → `sendWithButtons`. If sending the confirmation fails, the draft is simply left to expire. `confirm`: `take` → re-check deadline against `now` (may have passed during the wait) → `Task.create` → `repository.save` → `editText` (removes the buttons) → on any "no draft" outcome send "no longer available" via `answerCallback`-adjacent `sendText`. Button-press acknowledgement is done by the router before the flow runs, so the client never spins.

Deadline is displayed as `yyyy-MM-dd HH:mm` in `app.timezone` for both languages (unambiguous; the message text itself is localized). Messages are added to both bundles; a bundle-parity test already exists from `telegram-bot-base`.

### 12. Logging
Only ids, durations, counts, `Kind` and status codes are logged. Transcripts, titles, task text, audio, keys and tokens never are. A test runs the flow at DEBUG with captured output and asserts none of the sensitive strings appear.

## Risks / Trade-offs

- **Model gets a time wrong** (e.g. wrong day) → the confirmation shows the deadline in `app.timezone`; nothing is saved without Confirm. This is the main safety net and the reason for the confirmation step.
- **"até as 8h" said at 10:00** is rejected as past rather than assumed to mean tomorrow → matches the requested behavior; the owner resends with a day.
- **Provider rejects `response_format`** (some OpenAI-compatible endpoints) → the answer is still validated and fenced JSON is unwrapped; if a chosen provider rejects the parameter outright, it would surface as REJECTED and need a follow-up (not designed for here).
- **Drafts lost on restart** → old buttons answer "no longer available"; the owner resends. Accepted for a single instance.
- **Long synchronous handling blocks polling** (worst case ≈ 2 × (5 s + 30 s) per external call) → acceptable for one user; timeouts bound it. No concurrency added.
- **Third-party processing**: audio goes to Groq and the transcript to the LLM provider → inherent to the feature; noted in the README.
- **Rate limits on free tiers (429)** → one retry, then a friendly failure.
- **Ordering with `telegram-bot-base`**: the `telegram-inbound` delta here is a RENAMED + MODIFIED against a spec that exists only after that change is archived (`openspec validate` already notes archive would refuse it today) → archive `telegram-bot-base` first, then this change.
- **Hibernate validation vs. column types** (SMALLINT, TIMESTAMPTZ, UUID) → covered by the persistence integration test that boots the context with `ddl-auto=validate`.

## Migration Plan

- No database migration. Deploy the new image; `GROQ_API_KEY` is already provisioned, `LLM_*`, `APP_STT_*`, `APP_VOICE_MAX_SECONDS`, `APP_DRAFT_TTL` are optional.
- Rollback: redeploy the previous image. Saved tasks stay in the table and are ignored by the older version.

## Open Questions

- Which Groq chat model id to default `app.llm.model` to (confirm at implementation; configurable regardless).
- Whether a "processing…" or typing indicator is wanted for slow transcriptions (not required now; can be added later without changing the specs).
