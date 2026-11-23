# Proposal

## Why

The bot can now receive Telegram updates and answer them (`telegram-bot-base`), but every text or voice message still gets a "not supported yet" reply, and nothing ever writes to the `tasks` table that the foundation created. The core promise of the product — say a task out loud ("preciso enviar o relatório até as 18h, é urgente") and have it captured with the right urgency and deadline — does not exist yet. Reminders and scheduling need real tasks in the database first, so this is the next step.

## What Changes

- **Voice or text → draft → confirmation → task.** A voice note (or plain text that is not a slash command) from the owner is transcribed (voice only), parsed into `{title, urgency, startAt, deadlineAt}`, shown back as a summary with inline **Confirm / Cancel** buttons, and only saved when Confirm is pressed.
- **Domain model**: `Task`, `Urgency` (`LOW`, `MEDIUM`, `HIGH`) and `TaskStatus` (`PENDING`, `DONE`, `CANCELED`) with validation (title not blank, deadline strictly in the future, urgency required). Pure Java, no framework types.
- **Persistence**: `TaskRepository` port, a JPA entity and adapter on the existing `tasks` table from V1. No new migration is expected (V1 already has every column; see design). The "No persistence model yet" requirement of `task-storage-schema` is removed.
- **New outbound ports** in `application.port.out`: `AudioDownloadPort` (voice file bytes from Telegram), `TranscriptionPort`, `TaskParserPort`.
- **Adapters**: Telegram file download (in `infrastructure.telegram`); Groq Whisper transcription (`whisper-large-v3-turbo`, OpenAI-compatible `/audio/transcriptions`, multipart via `RestClient`, language from `app.stt.language`, default `pt`); one OpenAI-compatible chat-completions client for parsing (`app.llm.base-url`, `app.llm.model`, `app.llm.api-key`; works with Groq or Gemini) asking for strict JSON and validating it in code.
- **Use case `CreateTaskFromVoice`** (plain text skips transcription): rejects audio longer than `app.voice.max-seconds` (default 60); asks to resend with a time when the deadline is missing or already past; defaults a missing urgency to `MEDIUM` and says so clearly in the confirmation; keeps drafts in an in-memory TTL store (default 10 min, single instance); Confirm saves a `PENDING` task for the chat, Cancel discards the draft; a second press or a press after the TTL never creates a duplicate.
- **Failure handling**: STT/LLM/download failures and timeouts produce a friendly reply and no draft. Connect/read timeouts and one retry for transient errors are configurable. Audio, transcripts, task text and API keys are never logged.
- **Routing**: `IncomingCommandService` now routes voice, non-command text and the Confirm/Cancel callbacks to the new use case; `/start`, `/help` and unknown `/commands` keep their current behavior. The usage text no longer says reminders cannot be created.
- **Config**: new `app.stt.*`, `app.llm.*`, `app.voice.max-seconds`, `app.draft.ttl` settings with defaults, documented in `application.yaml`, `.env.example` and the README. New message keys in both `en` and `pt-BR` bundles.
- Tests: parser prompt/response validation (table-driven, fixed `Clock`), draft TTL and double-confirm idempotency, use case with mocked ports, adapters against a fake HTTP server, JPA adapter against PostgreSQL (Testcontainers).

Out of scope: reminders and scheduling, listing/completing/canceling saved tasks, editing a draft, recurring tasks, multi-user support, persistent drafts across restarts, and any model-side guarantee about relative-time arithmetic (the model is asked to resolve it; the code only validates the result).

## Capabilities

### New Capabilities
- `task-domain`: the `Task`, `Urgency` and `TaskStatus` model and its validation rules.
- `task-persistence`: the `TaskRepository` port and its JPA adapter over the existing `tasks` table.
- `voice-transcription`: downloading the Telegram voice file and transcribing it with Groq Whisper, including timeouts, one retry and failure mapping.
- `task-parsing`: turning text into a validated structured task via an OpenAI-compatible chat-completions model, including the prompt contract (current time in `app.timezone`, relative-time and urgency rules) and strict validation of the model's JSON.
- `task-creation-flow`: the `CreateTaskFromVoice` use case — length limit, missing/past deadline, default urgency, confirmation message with buttons, in-memory draft store with TTL, Confirm/Cancel and idempotency, friendly failures, no sensitive logging.

### Modified Capabilities
- `task-storage-schema`: removes the "No persistence model yet" requirement (entity and repository types now exist); the table structure and constraints are unchanged.
- `app-configuration`: adds the settings `app.stt.*`, `app.llm.*`, `app.voice.max-seconds` and `app.draft.ttl` with defaults and fail-fast validation.
- `telegram-inbound`: replaces "Placeholder replies for text and voice" — voice and non-command text start the task flow and button presses are routed to Confirm/Cancel instead of being acknowledged and dropped. (This capability is introduced by the unarchived `telegram-bot-base` change; see design, Risks.)

## Impact

- **Code**: new `domain` types; new `application.port.out` ports (`TaskRepository`, `AudioDownloadPort`, `TranscriptionPort`, `TaskParserPort`) and result types; new `application.service` use case, draft store and message formatting; `IncomingCommandService` routing; new `infrastructure.persistence` (entity, Spring Data repository, adapter), `infrastructure.stt` (Groq), `infrastructure.llm` (chat completions) and a download adapter in `infrastructure.telegram`; `AppProperties` extended.
- **Dependencies**: none added expected — `RestClient`, Jackson and Spring Data JPA are already on the classpath via the existing starters.
- **Database**: no migration unless implementation finds a real gap (design records why none is needed).
- **Config/docs**: `application.yaml`, `.env.example`, `README.md`, `messages*.properties`.
- **Systems**: outbound HTTPS to `api.telegram.org` (file download), the Groq STT endpoint and the configured LLM endpoint. `GROQ_API_KEY` becomes actually used; the LLM key defaults to it.
- **Ordering**: depends on `telegram-bot-base` (its ports, `IncomingCommand`, `Messages`); its last task (5.4, manual verification) is still open.
