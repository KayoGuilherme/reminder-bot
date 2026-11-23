# Telegram Reminder Bot

A Telegram bot that turns a voice note (or a text) into a task and then reminds you about it until it is done. Java 21 + Spring Boot, PostgreSQL, Flyway, packaged with Docker. It is designed to run **unattended as a single instance** on one machine: a home server, a Raspberry Pi or a small cloud VM.

You say what to do, how urgent it is and by when. The bot shows a summary and saves the task only after you press **Confirm**. It then reminds you before the deadline (more aggressively the more urgent the task is), and you can mark it **Done**, **Snooze** it or **Cancel** it from Telegram.

## Contents

- [Using the bot](#using-the-bot): example phrases, commands and buttons
- [How it behaves](#how-it-behaves): reminders, retries, privacy, what is logged
- [Configuration reference](#configuration-reference)
- [Run on a home machine or a Raspberry Pi](#run-on-a-home-machine-or-a-raspberry-pi)
- [Run locally with Docker Compose](#run-locally-with-docker-compose), [Develop and test](#develop-and-test), [CI and image publishing](#ci-and-image-publishing)
- [Deploy on Oracle Cloud Free Tier](#deploy-on-oracle-cloud-free-tier)

## Using the bot

Only the Telegram account in `TELEGRAM_ALLOWED_USER_ID` is served (to find your numeric id, message a bot such as `@userinfobot`). Messages from anyone else get no reply.

### Creating a task: example phrases

Send a **voice note** (up to `APP_VOICE_MAX_SECONDS`, 60 by default) or a plain **text** message, in Portuguese or English. The examples assume it is **Sunday 2026-11-22, 10:00** in `APP_TIMEZONE`. The title is written by the language model, so its exact wording may differ a little.

| You say (pt) | You say (en) | Bot understands |
| --- | --- | --- |
| "preciso enviar o relatório até as 18h, é urgente" | "send the report by 6pm, it's urgent" | Send the report · **High** · due 2026-11-22 18:00 |
| "pagar o aluguel amanhã às 9h" | "pay rent tomorrow at 9" | Pay rent · **Medium (assumed, you didn't say)** · due 2026-11-23 09:00 |
| "ligar para o dentista daqui a 2 horas, sem pressa" | "call the dentist in 2 hours, no rush" | Call the dentist · **Low** · due 2026-11-22 12:00 |
| "tenho uma reunião amanhã às 2 da tarde, urgente" | "I have a meeting tomorrow at 2pm, urgent" | Meeting · **High** · due 2026-11-23 **14:00** (hours 1-6 with no period of the day are read as afternoon) |
| "estudar inglês hoje às 20h, começar às 19h" | "study English today at 8pm, start at 7pm" | Study English · Medium (assumed) · start 19:00 · due 20:00 |
| "comprar leite" | "buy milk" | **No time given:** the bot asks you to resend including when it is due; nothing is saved |

Words that set the urgency: **High** ("urgente", "urgent", "muito importante", "asap", "crítico"), **Medium** ("importante", "important", "normal"), **Low** ("quando der", "sem pressa", "whenever", "no rush"). Without any of them the bot assumes Medium and says so in the summary, so you can cancel and resend. A time that has already passed is refused too.

The bot replies with the summary (title, urgency, deadline in your timezone) and **Confirm / Cancel** buttons. Confirm saves one `PENDING` task, Cancel discards the draft. A draft expires after `APP_DRAFT_TTL` (10 minutes) and is lost if the bot restarts; pressing a button twice never creates a duplicate.

### Commands

| Command | What it does |
| --- | --- |
| `/start`, `/help` | Short usage message |
| `/tasks` | Lists your **pending** tasks, earliest deadline first, one message per task with its deadline in `APP_TIMEZONE`, an urgency marker (🟢 Low, 🟡 Medium, 🔴 High) and an ⏰ *Overdue* flag when the deadline has passed. At most 20 are shown, followed by "and N more" when there are more. With nothing pending you get a friendly message. |

Anything else starting with `/` replies "not supported yet". Any other text or voice note is treated as a new task.

### Buttons

| Button | Where | Effect |
| --- | --- | --- |
| **Confirm** / **Cancel** | Task summary after you send a voice note or text | Save the task / discard the draft |
| **Done** | Reminders and `/tasks` | Marks the task `DONE` and stops all reminders |
| **Snooze 10 min** | Reminders | Moves the deadline 10 minutes ahead and restarts the reminder cycle |
| **Cancel** | `/tasks` | Marks the task `CANCELED` and stops all reminders |

Buttons are always answered. Pressing one twice, on an older message, or for a task that is already finished or no longer exists changes nothing and gets a polite reply. Done and Cancel race safely: if both are pressed for the same task, one wins.

## How it behaves

- **Long polling:** the bot needs outbound HTTPS to `api.telegram.org` and **no inbound port**.
- **Reminders:** every `APP_SCHEDULER_INTERVAL` (30 s) the bot looks at `PENDING` tasks due within 10 minutes (or already past) and sends at most one message per task per run, in the tone of its urgency. LOW: one reminder at 10 minutes before the deadline. MEDIUM: at 10 minutes and at the deadline. HIGH: at 10 minutes, 5 minutes and at the deadline. A stage is recorded only after Telegram accepted the message, so a failed send is retried on the next run and nothing is sent twice, including across restarts. If several stages are overdue (downtime, or a task created late) only the latest one is sent. `DONE` and `CANCELED` tasks get none. Texts are the `reminder.*` keys in the message files (`{0}` is the title, `{1}` the minutes left).
- **Snooze:** sets the deadline to the later of the current deadline and now, plus 10 minutes, resets the reminder cycle and edits the message to say so. Past the deadline that means the next run sends an immediate "10 minutes left" reminder, then the usual later stages: this is intended. A second Snooze is ignored until the restarted cycle has sent its first reminder.
- **HIGH overdue nagging:** after the reminder at the deadline, a HIGH task that is still `PENDING` gets an "overdue" reminder every `APP_REMINDER_REPEAT_INTERVAL` (2 minutes; in practice up to one scheduler interval more), more aggressive as the count grows (`reminder.HIGH.overdue.1` to `.3`, for repeats 1-3, 4-6 and 7 and up). After `APP_REMINDER_MAX_REPEATS` (10) overdue reminders one final "I'll stop reminding you" message is sent and the task is not messaged again. LOW and MEDIUM never repeat.
- **Cleanup:** every day at 03:00 (`APP_TIMEZONE`) the bot permanently deletes `DONE` and `CANCELED` tasks whose last change is older than `APP_RETENTION_DAYS` (30). `PENDING` tasks are never deleted. Set `APP_RETENTION_ENABLED=false` to keep everything. The number deleted is logged.
- **When a service fails:** if the speech-to-text or the LLM call fails (connection error, timeout, HTTP 429 or 5xx) it is retried **once** after `APP_RETRY_BACKOFF`. If it still fails you get a friendly "try again" message, nothing is saved, and the next message you send is handled normally; the failed one is not replayed, so just resend it. Other errors (for example a wrong API key) are not retried. Every external call has connect and read timeouts, so nothing waits forever.
- **Telegram errors:** if Telegram answers 429 the bot waits the requested `retry_after` (up to 30 s); network errors and 5xx are retried up to 3 attempts with backoff. After that the failure is logged and the bot carries on. An error while handling one update is logged and never stops polling.
- **Startup check:** right after starting, the bot asks Telegram which bot the token belongs to. A rejected token is logged as an `ERROR` naming `TELEGRAM_BOT_TOKEN`; an unreachable Telegram is only a `WARN`. The app keeps running either way. See [Troubleshooting](#troubleshooting).
- **Graceful stop:** on `docker stop` / `docker compose stop` the bot lets the reminder run in progress finish (up to `APP_SHUTDOWN_TIMEOUT`, 30 s) before exiting. Compose allows 45 s before killing the container.
- **Language:** the bot answers a voice note or text **in the language you used** (Portuguese or English): the confirmation, the buttons, and the saved/canceled messages. Anything not tied to a task message (`/start`, `/tasks`, reminders, errors before the message was understood) uses `APP_LANGUAGE`. Texts are in `messages.properties` (English) and `messages_pt_BR.properties`.
- **Privacy:** voice notes are sent to Groq for transcription and the transcribed text to the LLM provider. Audio, transcripts, task text and API keys are **never written to the logs** at any level; logs contain only ids, counts, durations, failure kinds and HTTP status codes. Every log line of one Telegram update or scheduled run carries the same short correlation id (the `[abc12345]` after the level), so one message can be followed through the log; lines outside an update show `[-]`.
- **Only health and info endpoints** are exposed (`/actuator/health` with liveness and readiness probes, `/actuator/info`).

## Configuration reference

Everything is configured with environment variables (a `.env` file next to `docker-compose.yml`). The app refuses to start, with an error naming the setting, if a required one is missing or any value is invalid. Any `app.*` setting can also be given as an environment variable by upper-casing it and replacing `.` and `-` with `_` (for example `APP_STT_READ_TIMEOUT=60s` sets `app.stt.read-timeout`).

### Required

| Variable | Description |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Bot token from BotFather |
| `TELEGRAM_ALLOWED_USER_ID` | Numeric Telegram user id allowed to use the bot |
| `GROQ_API_KEY` | Groq API key (speech-to-text, and the LLM unless you change it) |
| `GEMINI_API_KEY` | Gemini API key |
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://db:5432/reminder` |
| `DB_USER` / `DB_PASSWORD` | Database credentials (also used to create the compose database) |

### Optional

| Variable | Default | Description |
| --- | --- | --- |
| `APP_TIMEZONE` | `America/Fortaleza` | Timezone for every time shown to you and for the 03:00 cleanup. Internally everything is UTC. |
| `APP_LANGUAGE` | `en` | Default language of the bot's messages: `en` or `pt-BR`. It is used for everything that does not come from a task message (`/start`, `/tasks`, reminders, errors before the message was understood). Replies to a task message follow the language of that message. With Portuguese, dates are day first (`22/11/2026 18:00`); with English they are `2026-11-22 18:00`. |
| `APP_STT_LANGUAGE` | empty (auto-detect) | Language spoken in voice notes (Whisper language code such as `pt`). Empty lets Whisper detect it, so you can speak Portuguese or English; set a code to force one language if detection gets short notes wrong. |
| `LLM_BASE_URL` / `LLM_MODEL` / `LLM_API_KEY` | Groq, `openai/gpt-oss-20b`, `GROQ_API_KEY` | Any OpenAI-compatible chat-completions provider that parses tasks. For Gemini: `LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai`, a Gemini `LLM_MODEL` and `LLM_API_KEY`. |
| `APP_VOICE_MAX_SECONDS` | `60` | Longest accepted voice note |
| `APP_DRAFT_TTL` | `10m` | How long an unconfirmed task stays valid |
| `APP_SCHEDULER_ENABLED` | `true` | `false` turns the reminder job off |
| `APP_SCHEDULER_INTERVAL` | `30s` | Wait between reminder runs (greater than zero) |
| `APP_REMINDER_REPEAT_INTERVAL` | `2m` | Wait between overdue reminders of a HIGH task (greater than zero) |
| `APP_REMINDER_MAX_REPEATS` | `10` | Overdue reminders of a HIGH task before the final "I'll stop" message (at least 1) |
| `APP_RETENTION_ENABLED` | `true` | `false` never deletes finished tasks |
| `APP_RETENTION_DAYS` | `30` | Age after which `DONE`/`CANCELED` tasks are deleted (at least 1) |
| `APP_RETRY_BACKOFF` | `500ms` | Pause before the one retry of a transient STT/LLM failure (greater than zero) |
| `APP_TELEGRAM_CONNECT_TIMEOUT` | `5s` | Connect timeout of the Telegram client (greater than zero) |
| `APP_TELEGRAM_READ_TIMEOUT` | `30s` | Read timeout of the Telegram client (greater than zero) |
| `APP_SHUTDOWN_TIMEOUT` | `30s` | How long a stop waits for the run in progress (greater than zero) |
| `APP_TELEGRAM_ENABLED` | `true` | `false` starts the app without ever connecting to Telegram |
| `POSTGRES_DB` | `reminder` | Name of the compose database |
| `JAVA_OPTS` | 75% of RAM as max heap | JVM options. On a machine with 1 GB of RAM use `-Xmx256m -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC`. |
| `APP_IMAGE` | `reminder-bot:local` | Image to run, e.g. `ghcr.io/<owner>/<repo>:latest` |
| `COMPOSE_FILE` | | Set to `docker-compose.yml` on a server to skip the local-dev override file |

STT and LLM calls also have `app.stt.connect-timeout`/`read-timeout` and `app.llm.connect-timeout`/`read-timeout` (defaults `5s` and `30s`).

## Run on a home machine or a Raspberry Pi

The bot polls Telegram, so the machine needs only outbound internet: no port forwarding, no public IP. Any always-on Linux machine with Docker works. The published image is built for `linux/amd64` and `linux/arm64`, so on a Raspberry Pi use a **64-bit OS** (Raspberry Pi OS 64-bit or Ubuntu) on a Pi 4 or 5 (2 GB or more is comfortable). The JVM and PostgreSQL share the machine's RAM; with 1-2 GB set `JAVA_OPTS` as in the table above (and add swap on a 1 GB machine, see the Oracle section).

**1. Install Docker with the compose plugin** (Debian, Ubuntu, Raspberry Pi OS):

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # log out and back in
```

Docker is enabled at boot by that script (`sudo systemctl enable --now docker` if it is not).

**2. Copy the files** `docker-compose.yml` and `.env.example` (and `deploy/backup.sh` if you want backups) to the machine, for example into `~/bot`.

**3. Configure:**

```bash
cd ~/bot && cp .env.example .env && chmod 600 .env && nano .env
```

Fill in the required variables and add:

```
APP_IMAGE=ghcr.io/<owner>/<repo>:latest   # lowercase
COMPOSE_FILE=docker-compose.yml           # skips the local-dev override file
```

If the image is private, authenticate once with `docker login ghcr.io` (a GitHub token with `read:packages`). If you prefer to build on the machine from a checkout, skip `APP_IMAGE` and run `docker compose -f docker-compose.yml up -d --build` instead.

**4. Start:**

```bash
docker compose pull app
docker compose up -d
docker compose logs -f app
```

Both containers use **`restart: unless-stopped`** (set in `docker-compose.yml`), so they come back after a crash, a Docker restart or a reboot, and stay down only if you stop them yourself. The database lives in the `pgdata` volume and survives restarts and updates. Logs rotate (10 MB x 3 files). The first start applies the database migrations; the log shows `Telegram accepted the bot token: running as @your_bot` when the token is valid.

**Everyday commands** (run in `~/bot`):

| Task | Command |
| --- | --- |
| Follow the logs | `docker compose logs -f app` |
| Update to the newest image | `docker compose pull app && docker compose up -d` |
| Roll back | set `APP_IMAGE` to an older `sha` tag, then `docker compose up -d` |
| Stop (waits for a reminder run in progress) | `docker compose stop` |
| Start again | `docker compose up -d` |
| Back up the database | `./deploy/backup.sh` (keeps 7 days in `backups/`; see the cron line in the Oracle section) |

### Troubleshooting

- **`Telegram rejected TELEGRAM_BOT_TOKEN`** in the log: the token is wrong or was revoked in BotFather. Fix `TELEGRAM_BOT_TOKEN` in `.env` and run `docker compose up -d` (a changed `.env` needs the container recreated, not only restarted).
- **`Could not verify TELEGRAM_BOT_TOKEN: Telegram is not reachable`**: no internet or DNS from the machine at boot. The bot keeps trying to poll once the network is back.
- **The app exits immediately with a message naming a setting**: a required variable is missing or a value is invalid; fix `.env`.
- **The bot does not answer you**: check you messaged from the account in `TELEGRAM_ALLOWED_USER_ID`; other accounts only produce a `WARN` with their user id in the log.
- **"Sorry, I couldn't process that audio/message"**: the speech-to-text or LLM service failed or is slow; the bot already retried once. Check the log lines with `HTTP` status codes (401 means a wrong API key) and resend.

## Run locally with Docker Compose

```bash
cp .env.example .env   # then edit the values
docker compose up --build
```

`docker-compose.override.yml` is loaded automatically: it activates the `local` profile and binds the database (`127.0.0.1:5432`) and the app (`127.0.0.1:8080`) to localhost only. Check it with:

```bash
curl http://127.0.0.1:8080/actuator/health
```

Set `APP_TELEGRAM_ENABLED=false` to work on the app without Telegram.

## Develop and test

```bash
./mvnw verify
```

Tests start a real PostgreSQL with Testcontainers, so Docker must be running. They check that the context loads, Flyway applies the migrations, the `tasks` constraints hold, the repository queries, the retention and reminder jobs and the whole `/tasks`, Done and Cancel flow. The integration tests run with `app.telegram.enabled=false`, so they never contact Telegram; the Telegram sender and update handling are tested against a local fake HTTP server.

## CI and image publishing

- **`ci`** (`.github/workflows/ci.yml`): on pull requests and pushes to `main`, runs `./mvnw -B verify` on Java 21.
- **`docker`** (`.github/workflows/docker.yml`): after `ci` succeeds on a push to `main`, builds the image for `linux/amd64` and `linux/arm64` and pushes it to `ghcr.io/<owner>/<repo>` tagged with the commit SHA and `latest`, using `GITHUB_TOKEN`. The same workflow then runs a `deploy` job that updates the VM over SSH (see "Automatic deploy" below); it needs four repository secrets.

## Deploy on Oracle Cloud Free Tier

The bot uses long polling, so the VM needs **no open inbound port** besides SSH (22). Nothing to add to the VCN security list.

**1. Create the VM** (Compute → Instances), Ubuntu 22.04/24.04:

| Shape | Arch | RAM | Notes |
| --- | --- | --- | --- |
| `VM.Standard.A1.Flex` (1-2 OCPU, 6-12 GB) | arm64 | plenty | Recommended. Often "out of capacity": retry or try another availability domain. |
| `VM.Standard.E2.1.Micro` | amd64 | 1 GB | Works, but set `JAVA_OPTS` (step 4) and add swap (below). |

The CI image is published for both `amd64` and `arm64`, so either shape pulls the right one.

**2. Install Docker** (via SSH):

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker $USER   # log out and back in
```

**3. Copy the files** `docker-compose.yml`, `.env.example` and `deploy/backup.sh` to the VM (e.g. `~/bot`).

**4. Configure `.env`:**

```bash
cd ~/bot && cp .env.example .env && chmod 600 .env && nano .env
```

Fill in the secrets and add:

```
APP_IMAGE=ghcr.io/<owner>/<repo>:latest   # lowercase
COMPOSE_FILE=docker-compose.yml           # skips the local-dev override file
JAVA_OPTS=-Xmx256m -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC   # only on the 1 GB micro shape
```

If the GHCR package is private: `docker login ghcr.io` (GitHub token with `read:packages`).

**5. Start:**

```bash
docker compose pull app && docker compose up -d
docker compose logs -f app
```

Containers use `restart: unless-stopped` and Docker starts on boot, so the bot comes back after a reboot. Logs rotate (10 MB x 3).

**Swap on the 1 GB micro shape** (avoids OOM kills):

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**Backups:** `deploy/backup.sh` dumps the database to `~/bot/backups` and keeps 7 days. Schedule it with `crontab -e`:

```
0 4 * * * cd ~/bot && ./deploy/backup.sh >> backups/backup.log 2>&1
```

**Oracle reclaims idle free-tier instances** (CPU, network and memory all very low for 7 days). A bot that is polling and rarely used can trigger this; check the instance occasionally, or upgrade the account to Pay As You Go (still free within the Always Free limits).

Update with `docker compose pull app && docker compose up -d`.

### Automatic deploy (push to `main` updates the VM)

After the image is published, the `deploy` job in `docker.yml` connects to the VM and runs `docker compose pull app && docker compose up -d`. The bot is restarted with the new image (about a minute of downtime on the 1 GB shape).

**One-time setup**

1. On your PC, create a key used only for deploys:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/bot_deploy -N "" -C github-deploy
```

2. On the VM, authorize it so that it can run **only** the update command (nothing else, no shell):

```bash
echo "command=\"cd /home/ubuntu/bot && docker compose pull app && docker compose up -d && docker image prune -f\",restrict $(cat ~/bot_deploy.pub)" >> ~/.ssh/authorized_keys
```

(copy `~/.ssh/bot_deploy.pub` to the VM first, e.g. with `scp`).

3. In GitHub: **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `VM_HOST` | the VM public IP |
| `VM_USER` | `ubuntu` |
| `VM_SSH_KEY` | full contents of `~/.ssh/bot_deploy` (the private key) |
| `VM_KNOWN_HOSTS` | output of `ssh-keyscan -t ed25519 <VM_IP>` run on your PC |

The VM's public IP must stay the same. If you stop and recreate the instance, update `VM_HOST` and `VM_KNOWN_HOSTS`.
