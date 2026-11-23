# Spec Delta

## ADDED Requirements

### Requirement: Shutdown waits for the run in progress
When the application is asked to stop (for example by `docker stop`), the reminder job SHALL NOT start another run, and a run already in progress SHALL be allowed to finish, for at most a bounded grace period (30 seconds by default), before the application exits. A reminder whose message was sent MUST have its stage recorded before the application stops whenever the run finishes within the grace period; a run that exceeds it is abandoned, and anything unsent or unrecorded is simply retried by the next run after the next start. The same applies to the retention job.

#### Scenario: Run in progress at shutdown
- **WHEN** a stop is requested while a run is sending reminders
- **THEN** the run completes its sends and records them, and only then does the application stop

#### Scenario: No new run after the request
- **WHEN** a stop is requested between two runs
- **THEN** no further run starts

#### Scenario: Run takes longer than the grace period
- **WHEN** a run is still going when the grace period ends
- **THEN** the application stops anyway, and on the next start tasks whose stage was not recorded are reminded again

#### Scenario: Idle at shutdown
- **WHEN** a stop is requested while no run is in progress
- **THEN** the application stops without waiting
