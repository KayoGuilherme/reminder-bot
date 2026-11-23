# Spec Delta

## ADDED Requirements

### Requirement: MCP settings come from environment variables
The application SHALL read whether MCP is enabled and its bearer token from the environment variables `MCP_ENABLED` (default `false`) and `MCP_TOKEN` (default empty), exposed as the `app.mcp.*` settings. The token is a secret: it MUST NOT be committed to the repository, and `.env.example` MUST hold only an empty placeholder for it. The token MUST be required, validated and kept out of logs as described in the `mcp-access` capability.

#### Scenario: Defaults
- **WHEN** the application starts without `MCP_ENABLED` and `MCP_TOKEN`
- **THEN** `app.mcp.enabled` is false and startup completes

#### Scenario: No token in the repository
- **WHEN** the committed configuration files and `.env.example` are inspected
- **THEN** they contain no MCP token value

#### Scenario: Token never logged
- **WHEN** the application starts with MCP enabled
- **THEN** no startup log line, including the printed settings, contains the token value

### Requirement: The MCP endpoint is not a management endpoint
The `/mcp` path SHALL be an application endpoint protected by its own token. Enabling it MUST NOT expose any additional management endpoint: only `health` and `info` remain reachable under the management paths.

#### Scenario: Management surface unchanged
- **WHEN** MCP is enabled and a client requests `/actuator/env` or `/actuator/beans`
- **THEN** the response is 404

#### Scenario: Health stays public to the probe
- **WHEN** MCP is enabled and a client requests `/actuator/health` without any token
- **THEN** the response reports status `UP`
