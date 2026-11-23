# Spec Delta

## REMOVED Requirements

### Requirement: No persistence model yet
**Reason**: The foundation deliberately shipped the schema ahead of the code that uses it. The `voice-to-task` change introduces the task model and its persistence, so the rule that entity and repository types must not exist no longer applies. The table structure, constraints and index requirements are unchanged.
**Migration**: None. The persistence behavior is specified in `task-persistence`; schema validation at startup continues to apply and now also verifies that the mapping matches the V1 schema.
