# ci-delivery Specification

## Purpose

Defines automated verification of every change and publication of the container image, without automatic deployment.

## Requirements

### Requirement: Continuous integration
A `ci` workflow SHALL run on every pull request and on every push to `main`, check out the code, set up Java 21 with dependency caching, and run the full build and test suite including tests that need a real PostgreSQL.

#### Scenario: Pull request
- **WHEN** a pull request is opened or updated
- **THEN** the `ci` workflow runs build and tests and reports pass or fail on the pull request

#### Scenario: Clean clone passes
- **WHEN** the workflow runs on a clean clone of a correct commit
- **THEN** it passes without any repository secrets configured

### Requirement: Image publication on main
A `docker` workflow SHALL, only after `ci` has succeeded on a push to `main`, build the image and push it to GitHub Container Registry tagged with the commit SHA and `latest`, authenticating with the built-in `GITHUB_TOKEN`.

#### Scenario: Successful main build
- **WHEN** `ci` succeeds for a push to `main`
- **THEN** an image tagged with that commit SHA and `latest` is available on `ghcr.io`

#### Scenario: Failed CI blocks publication
- **WHEN** `ci` fails for a push to `main`
- **THEN** no image is published

#### Scenario: Pull requests never publish
- **WHEN** a pull request triggers workflows
- **THEN** no image is pushed to the registry

### Requirement: No automatic deployment
The workflows MUST NOT deploy to any environment. The README SHALL explain how to pull the published image and run it with docker compose on a server, a home machine or a Raspberry Pi.

#### Scenario: Documented manual deployment
- **WHEN** a user follows the README section on running the published image
- **THEN** they can start the stack from the pulled image using their own `.env`
