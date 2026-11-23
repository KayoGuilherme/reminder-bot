# Spec Delta

## ADDED Requirements

### Requirement: Compose allows a graceful stop
The compose definition SHALL give the `app` service a stop grace period longer than the application's shutdown timeout (at least 45 seconds with the default 30 seconds), so that stopping or restarting the container lets the application finish its current reminder run instead of being killed.

#### Scenario: Stop during a run
- **WHEN** `docker compose stop app` runs while a reminder run is in progress
- **THEN** the application finishes the run and exits on its own, and Docker does not kill it before the grace period ends

#### Scenario: Grace period outlasts the shutdown timeout
- **WHEN** the compose file and the default `app.shutdown.timeout` are compared
- **THEN** the compose stop grace period is longer

### Requirement: Operator documentation
The README SHALL document how to use and run the bot without reading the code: a usage guide with example voice or text phrases in Portuguese and in English (covering urgency, relative times and the "no time given" case), the list of commands (`/start`, `/help`, `/tasks`) and buttons (Confirm, Cancel, Done, Snooze, and Done/Cancel on `/tasks`), a reference of every environment variable with its default and effect (including the retention, retry and timeout settings), and instructions for running the published or locally built image on a home machine or a Raspberry Pi with `docker compose` and `restart: unless-stopped`. Statements that no longer match the bot's behavior MUST be removed or corrected.

#### Scenario: Example phrases
- **WHEN** a new user reads the usage guide
- **THEN** they find at least three example phrases in Portuguese and three in English, each with the title, urgency and deadline the bot is expected to extract

#### Scenario: Commands listed
- **WHEN** the README's command list is compared with the commands the bot answers
- **THEN** every command appears with a one-line description and none is missing

#### Scenario: Configuration reference complete
- **WHEN** the README's configuration reference is compared with `.env.example` and the `app.*` settings
- **THEN** every variable appears with its default value and meaning

#### Scenario: Raspberry Pi instructions
- **WHEN** a user follows the home-machine / Raspberry Pi section on a fresh arm64 machine with Docker
- **THEN** the documented commands start the bot with `restart: unless-stopped` so that it comes back after a reboot, and the section states how to update and how to read the logs
