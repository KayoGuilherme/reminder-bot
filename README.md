# Telegram Reminder Bot

A Telegram bot that creates and sends task reminders. Java 21 + Spring Boot, PostgreSQL, Flyway, packaged with Docker. It is designed to run as a **single instance**.

The bot connects to Telegram, answers only its owner and turns a voice note (or plain text) into a task: you say what to do, how urgent it is and by when, the bot shows a summary and saves it only after you press **Confirm**. The reminder scheduler comes in a later change.

## Required environment variables

The app refuses to start (with an error naming the property) if a required one is missing or blank.

| Variable | Description |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Bot token from BotFather |
| `TELEGRAM_ALLOWED_USER_ID` | Numeric Telegram user id allowed to use the bot |
| `GROQ_API_KEY` | Groq API key |
| `GEMINI_API_KEY` | Gemini API key |
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://db:5432/reminder` |
| `DB_USER` / `DB_PASSWORD` | Database credentials (also used to create the compose database) |
| `APP_TIMEZONE` | Display timezone, default `America/Fortaleza`. Internally everything is UTC. |

Optional variables:

| Variable | Description |
| --- | --- |
| `APP_LANGUAGE` | Language of the bot's messages: `en` (default) or `pt-BR`. Any other value stops startup. |
| `APP_TELEGRAM_ENABLED` | `true` (default) or `false`. When `false` the app starts normally but never connects to Telegram. |
| `APP_STT_LANGUAGE` | Language of voice notes (Whisper language code), default `pt`. |
| `LLM_BASE_URL` / `LLM_MODEL` / `LLM_API_KEY` | OpenAI-compatible chat-completions provider that parses tasks. Defaults to Groq (`https://api.groq.com/openai/v1`, `openai/gpt-oss-20b`) with `GROQ_API_KEY`. For Gemini set `LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai`, a Gemini `LLM_MODEL` and `LLM_API_KEY`. |
| `APP_VOICE_MAX_SECONDS` | Longest accepted voice note, default `60`. |
| `APP_DRAFT_TTL` | How long an unconfirmed task stays valid, default `10m`. |

Privacy: voice notes are sent to Groq for transcription and the transcribed text to the LLM provider. Audio, transcripts, task text and API keys are never written to the logs.

## How the bot behaves

- It receives updates by **long polling**, so it needs outbound HTTPS to `api.telegram.org` and no inbound port.
- **Only `TELEGRAM_ALLOWED_USER_ID` is served.** Updates from anyone else get no reply; the app logs one `WARN` with that user's id and never their message content.
- `/start` and `/help` reply with a short usage message. An unknown `/command` replies "not supported yet".
- **Creating a task**: send a voice note (up to `APP_VOICE_MAX_SECONDS`) or a text such as "preciso enviar o relatório até as 18h, é urgente". The bot replies with the title, urgency and deadline (in `APP_TIMEZONE`) and **Confirm / Cancel** buttons. Confirm saves one `PENDING` task; Cancel discards it. If you give no time, or a time that has already passed, the bot asks you to resend with a time. If you give no urgency it assumes Medium and says so. Drafts live in memory for `APP_DRAFT_TTL` (lost on restart); pressing a button twice, or after that time, never creates a duplicate.
- If transcription or the LLM fails or times out (one retry for transient errors), you get a friendly error and nothing is saved.
- Messages come from `messages.properties` (English) and `messages_pt_BR.properties`, chosen by `APP_LANGUAGE`.
- If Telegram answers 429 the bot waits the requested `retry_after` (up to 30 s); network errors and 5xx are retried up to 3 attempts with backoff. After that the failure is logged and the bot carries on. An error while handling one update is logged (update id only) and never stops polling.
- To find your numeric user id, message a bot such as `@userinfobot`.

Check it with a real token: send `/start` from the allowed account (you get a reply) and from any other account (nothing comes back, one `WARN` appears in the logs). Set `APP_TELEGRAM_ENABLED=false` to work on the app without Telegram.

## Run locally with Docker Compose

```bash
cp .env.example .env   # then edit the values
docker compose up --build
```

`docker-compose.override.yml` is loaded automatically: it activates the `local` profile and binds the database (`127.0.0.1:5432`) and the app (`127.0.0.1:8080`) to localhost only. Check it with:

```bash
curl http://127.0.0.1:8080/actuator/health
```

Only `health` (with liveness/readiness probes) and `info` are exposed.

## Develop and test

```bash
./mvnw verify
```

Tests start a real PostgreSQL with Testcontainers, so Docker must be running. They check that the context loads, Flyway applies the migrations, and the `tasks` constraints hold. The integration tests run with `app.telegram.enabled=false`, so they never contact Telegram; the Telegram sender and update handling are tested against a local fake HTTP server.

## CI and image publishing

- **`ci`** (`.github/workflows/ci.yml`): on pull requests and pushes to `main`, runs `./mvnw -B verify` on Java 21.
- **`docker`** (`.github/workflows/docker.yml`): after `ci` succeeds on a push to `main`, builds the image and pushes it to `ghcr.io/<owner>/<repo>` tagged with the commit SHA and `latest`, using `GITHUB_TOKEN`. The same workflow then runs a `deploy` job that updates the VM over SSH (see "Automatic deploy" below); it needs four repository secrets.

## Run the published image (server, home machine, Raspberry Pi)

1. Install Docker with the compose plugin. Copy `docker-compose.yml` and `.env.example` to the machine.
2. `cp .env.example .env` and fill in the values. Add `APP_IMAGE=ghcr.io/<owner>/<repo>:latest` (lowercase) to `.env`.
3. If the package is private, authenticate once: `docker login ghcr.io` with a GitHub token that has `read:packages`.
4. Start it, skipping the local override file:

```bash
docker compose -f docker-compose.yml pull app
docker compose -f docker-compose.yml up -d
```

To update, repeat the `pull` and `up -d` commands. To roll back, point `APP_IMAGE` at an older `sha` tag.

The published image is built for `linux/amd64`. On a Raspberry Pi (arm64), build locally instead: `docker compose -f docker-compose.yml up -d --build`.

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
