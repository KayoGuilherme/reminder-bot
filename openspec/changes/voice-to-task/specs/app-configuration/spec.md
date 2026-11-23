# Spec Delta

## ADDED Requirements

### Requirement: Speech-to-text settings
The application SHALL expose `app.stt.language` (env `APP_STT_LANGUAGE`, default `pt`, a non-blank language code), `app.stt.model` (default `whisper-large-v3-turbo`) and `app.stt.base-url` (default Groq's OpenAI-compatible API root), and SHALL authenticate transcription with the existing Groq API key. It SHALL expose connect and read timeouts for the transcription and file-download calls with defaults of 5 seconds (connect) and 30 seconds (read).

#### Scenario: Defaults
- **WHEN** the application starts without any `APP_STT_*` variable
- **THEN** the language is `pt` and the model is `whisper-large-v3-turbo`

#### Scenario: Override
- **WHEN** `APP_STT_LANGUAGE=en` is set
- **THEN** transcription requests use language `en`

### Requirement: Language-model settings
The application SHALL expose `app.llm.base-url`, `app.llm.model` and `app.llm.api-key` (envs `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`) for the OpenAI-compatible chat-completions provider used to parse tasks. The defaults SHALL target Groq (using the Groq API key when `LLM_API_KEY` is not set); pointing the base URL, model and key at Gemini's OpenAI-compatible endpoint SHALL work without code changes. The settings SHALL also expose connect and read timeouts (defaults 5 and 30 seconds). A blank API key or base URL, or a non-positive timeout, MUST cause startup to fail naming the setting. The API key MUST NOT appear in the settings' string representation or in logs.

#### Scenario: Default provider
- **WHEN** the application starts with only `GROQ_API_KEY` set
- **THEN** parsing uses Groq's endpoint with that key

#### Scenario: Gemini
- **WHEN** `LLM_BASE_URL`, `LLM_MODEL` and `LLM_API_KEY` point at Gemini
- **THEN** parsing requests go to that URL with that model and key

#### Scenario: Blank key
- **WHEN** the resolved LLM API key is blank
- **THEN** startup fails with an error naming `app.llm.api-key`

#### Scenario: Key not printed
- **WHEN** the settings object is converted to text
- **THEN** the API key is masked

### Requirement: Voice and draft settings
The application SHALL expose `app.voice.max-seconds` (env `APP_VOICE_MAX_SECONDS`, default 60) and `app.draft.ttl` (env `APP_DRAFT_TTL`, default 10 minutes). Both MUST be positive; otherwise startup fails naming the setting.

#### Scenario: Defaults
- **WHEN** the application starts without these variables
- **THEN** the maximum voice length is 60 seconds and the draft TTL is 10 minutes

#### Scenario: Invalid value
- **WHEN** `APP_VOICE_MAX_SECONDS=0` is set
- **THEN** startup fails with an error identifying `app.voice.max-seconds`
