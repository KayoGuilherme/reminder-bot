# reminder-dispatch Specification

## Purpose

Defines how due reminders are found, sent and recorded on a recurring schedule so each owed reminder is delivered exactly once, retried when delivery fails, and never blocked by one bad task.

## Requirements

### Requirement: Candidates are pending tasks near or past their deadline
Each run SHALL consider only PENDING tasks whose deadline is no later than 10 minutes after the current time and that still have an unsent reminder stage for their urgency, which for HIGH includes the overdue and final stages. Tasks that are DONE or CANCELED, whose deadline is further away, or whose reminders are all sent SHALL NOT be considered or messaged. Whether a considered task is actually due now, such as an overdue repeat that must wait for the repeat interval, is decided per task by the reminder policy.

#### Scenario: Task outside the window
- **WHEN** a run happens 11 minutes before a pending task's deadline
- **THEN** no message is sent for that task

#### Scenario: Done and canceled tasks
- **WHEN** a run happens inside the reminder window of a DONE task and of a CANCELED task
- **THEN** no message is sent for either

#### Scenario: Reminders exhausted
- **WHEN** a run happens after the final stop message of a pending HIGH task was sent
- **THEN** the task is not messaged again

#### Scenario: Overdue HIGH task stays a candidate
- **WHEN** a run happens 3 minutes after the deadline of a pending HIGH task whose stage 3 was sent
- **THEN** the task is considered and its overdue reminder is sent

#### Scenario: Exhausted MEDIUM task
- **WHEN** a run happens after a pending MEDIUM task's stage 2 was sent
- **THEN** the task is not messaged again

### Requirement: One message per task per run, to the task's chat
For each candidate with a due stage, a run SHALL send exactly one message, containing the reminder text for that task's urgency and stage and carrying the Done and Snooze buttons, to the chat the task belongs to. It MUST NOT send more than one message for a task in the same run.

#### Scenario: Due reminder sent
- **WHEN** a run finds a pending MEDIUM task due in exactly 10 minutes with no reminder sent
- **THEN** one stage-1 MEDIUM message with the Done and Snooze buttons is sent to the task's chat

#### Scenario: Catch-up is one message
- **WHEN** a run finds a pending HIGH task that is already past its deadline with no reminder sent
- **THEN** exactly one message, for stage 3, is sent to the task's chat

#### Scenario: Overdue repeat is one message
- **WHEN** a run finds a pending HIGH task whose last reminder was sent more than the repeat interval ago, after its deadline
- **THEN** exactly one overdue message is sent, not one per missed interval

### Requirement: The stage is recorded only after a successful send
After a send succeeds the system SHALL record the sent stage as the task's last reminder stage and the current time as its last reminded time, in a single atomic update per task. If the send fails, the recorded stage and time MUST remain unchanged so a later run retries the same stage.

#### Scenario: Successful send
- **WHEN** the stage-1 message for a task is sent successfully
- **THEN** the task's last reminder stage becomes 1 and its last reminded time is the current time

#### Scenario: Failed send is retried
- **WHEN** sending a task's stage-1 message fails and a later run happens while the stage is still due
- **THEN** the task's stored stage is still 0 after the failed run, and the later run sends the stage again

#### Scenario: No duplicates across runs and restarts
- **WHEN** a stage was sent and recorded and the job runs again, including after an application restart
- **THEN** that stage is not sent again

### Requirement: The record is not applied to a task that is no longer eligible
Recording a sent stage SHALL succeed only if the task is still PENDING, its stored stage is lower than the sent stage, and its deadline is unchanged since the decision to send was made; otherwise the stored values MUST be left untouched.

#### Scenario: Task completed during sending
- **WHEN** a task becomes DONE between the eligibility check and the recording of a sent stage
- **THEN** the task's stored stage and last reminded time are not changed

#### Scenario: Task snoozed during sending
- **WHEN** a task is snoozed, so its deadline moves and its stage resets to 0, between the eligibility check and the recording of a sent overdue stage
- **THEN** the stage is not recorded and the task keeps stage 0 with its new deadline

### Requirement: One failing task does not stop the others
A failure while handling one task, whether the send is rejected, an exception is raised, or recording fails, SHALL NOT prevent the remaining candidates in the same run from being processed.

#### Scenario: First task fails
- **WHEN** a run has two due tasks and sending for the first fails
- **THEN** the second task's reminder is still sent and recorded, and the first keeps its previous stage

### Requirement: Late-created tasks are reminded on the next run
A task created when its deadline is already within 10 minutes SHALL receive its due reminder on the next run, without any extra step at creation.

#### Scenario: Task created 7 minutes before its deadline
- **WHEN** a HIGH task is created 7 minutes before its deadline and the next run happens
- **THEN** one stage-1 message is sent

### Requirement: Full reminder sequence
Across successive runs, a HIGH task due in 12 minutes SHALL be reminded exactly once at about T-10 minutes, exactly once at about T-5 minutes and exactly once at about T, "about" meaning at the first run at or after that moment, followed by the overdue sequence while it stays pending. DONE or CANCELED tasks SHALL receive none.

#### Scenario: HIGH task due in 12 minutes
- **WHEN** runs happen every 30 seconds from 12 minutes before the deadline until 1 minute after it
- **THEN** exactly three messages are sent, for stages 1, 2 and 3, the first at the first run at or after T-10 minutes, the second at or after T-5 minutes and the third at or after T

#### Scenario: Task marked done mid-sequence
- **WHEN** a HIGH task becomes DONE after its stage-1 reminder
- **THEN** no further reminders are sent for it

### Requirement: Scheduler runs on a configurable interval
The reminder job SHALL run repeatedly, waiting `app.scheduler.interval` (default 30 seconds) after each run finishes before starting the next, so runs never overlap. It SHALL not run at all when `app.scheduler.enabled` is false.

#### Scenario: Default interval
- **WHEN** the application starts with no scheduler settings
- **THEN** the job is active and runs 30 seconds apart

#### Scenario: Disabled scheduler
- **WHEN** the application starts with `app.scheduler.enabled=false`
- **THEN** the job never runs and no reminder is sent by it

#### Scenario: A run that throws does not end the schedule
- **WHEN** a run fails unexpectedly
- **THEN** the failure is logged and the next run still happens after the interval

### Requirement: An ignored HIGH task is nagged up to the cap
After stage 3, runs SHALL send one overdue message per `app.reminder.repeat-interval` while the HIGH task stays PENDING, at most `app.reminder.max-repeats` of them, then exactly one final stop message, then nothing. Spacing is from the previous message's actual send time and is rounded up to the next run, since runs happen every `app.scheduler.interval`.

#### Scenario: Ignored for the whole sequence
- **WHEN** runs happen every 30 seconds from T until 40 minutes after T on a pending HIGH task whose stage 3 was sent at T, with the defaults
- **THEN** ten overdue messages are sent, each at the first run at least 2 minutes after the previous message, followed by one final stop message at the first run at least 2 minutes after the tenth, and no message after that

#### Scenario: Done stops it immediately
- **WHEN** the owner presses Done after the third overdue message
- **THEN** no message is sent at any later run

#### Scenario: A failed overdue send is retried
- **WHEN** sending an overdue message fails and the next run happens
- **THEN** the same stage is attempted again and the stored stage and last reminded time stay unchanged

### Requirement: Snooze followed by the scheduler restarts the cycle
After a task is snoozed, the next run SHALL treat it like a task whose deadline is 10 minutes (or less) ahead with no reminder sent: it sends stage 1 immediately, stage 2 at the new T-5 minutes (HIGH) and stage 3 at the new deadline, then, for HIGH, the overdue sequence with a fresh repeat counter.

#### Scenario: Snooze of an overdue HIGH task
- **WHEN** a HIGH task is 15 minutes overdue with 4 overdue messages sent, the owner presses Snooze, and a run happens
- **THEN** one stage-1 message is sent at that run, and nothing is sent for the earlier overdue counter

#### Scenario: Snoozed task completes the cycle
- **WHEN** runs continue every 30 seconds after the snooze for 12 minutes without further presses
- **THEN** stage 1, stage 2 and stage 3 are sent once each at the first run at or after the snooze, the new T-5 minutes and the new T, followed by the first overdue message 2 minutes later

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
