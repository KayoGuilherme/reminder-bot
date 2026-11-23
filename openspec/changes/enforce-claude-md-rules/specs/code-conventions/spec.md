# Spec Delta

## Purpose

Defines the rules every source file of the project follows (comments, folders, types, control flow, layering, validation) and the automated check that fails the build when one is broken, so the rules in `.claude/CLAUDE.md` stay true.

## ADDED Requirements

### Requirement: Source files contain no comments
Every Java file under `src/main/java` and `src/test/java` SHALL contain no line comment, block comment or Javadoc. Meaning MUST be carried by the names of classes, methods and variables.

#### Scenario: Comment is rejected
- **WHEN** a Java file in main or test code contains a `//`, `/* */` or `/** */` comment
- **THEN** the conventions check fails and names the file and the line

#### Scenario: Comment markers inside text are accepted
- **WHEN** a string literal or text block contains the characters `//` or `/*`
- **THEN** the conventions check does not report it

### Requirement: No package-info files
The project SHALL NOT contain any `package-info.java` file.

#### Scenario: package-info is rejected
- **WHEN** a `package-info.java` exists anywhere under `src`
- **THEN** the conventions check fails and names the file

### Requirement: Records are only for DTOs
A `record` SHALL be declared only in `application/dto` (request and response DTOs) or in `transport` (HTTP payloads). Every other type, and every test type, MUST be a regular class.

#### Scenario: Record outside a DTO folder is rejected
- **WHEN** a Java file outside `application/dto` and `transport` declares a `record`
- **THEN** the conventions check fails and names the file and the line

#### Scenario: Record in a DTO folder is accepted
- **WHEN** a Java file in `application/dto/response` declares a `record`
- **THEN** the conventions check does not report it

### Requirement: Restricted control flow
Methods, constructors and lambdas SHALL NOT use `else`, `else if`, `switch` statements or expressions, an `if` nested inside another `if`, two consecutive `if` statements in the same block, or a `return` outside tail position. Tail position is the last statement of the body, or the last statement of a `try` or `catch` block that is itself in tail position. Throwing an exception is not a `return` and stays allowed.

#### Scenario: else is rejected
- **WHEN** a method contains an `else` or `else if` branch
- **THEN** the conventions check fails and names the file and the line

#### Scenario: switch is rejected
- **WHEN** a method contains a `switch` statement or expression
- **THEN** the conventions check fails and names the file and the line

#### Scenario: Early return is rejected
- **WHEN** a `return` statement appears before the last statement of a method body
- **THEN** the conventions check fails and names the file and the line

#### Scenario: Return at the end of a try block is accepted
- **WHEN** a `return` is the last statement of a `try` block that is the last statement of the method
- **THEN** the conventions check does not report it

#### Scenario: Nested if is rejected
- **WHEN** an `if` statement contains another `if` statement
- **THEN** the conventions check fails and names the file and the line

#### Scenario: Consecutive ifs are rejected
- **WHEN** two `if` statements follow each other in the same block
- **THEN** the conventions check fails and names the file and the line

### Requirement: Every class lives in the folder of its role
Every main class SHALL live directly in one of `domain/models`, `domain/models/enums`, `domain/exceptions`, `domain/repositories`, `application/dto/request`, `application/dto/response`, `application/service`, `application/component`, `transport/telegram`, `transport/stt`, `transport/llm`, `transport/http` or `config` below the base package, except the application entry point. A class annotated `@Entity` MUST be in `domain/models`, `@Service` in `application/service`, `@Component` in `application/component` and `@Configuration` in `config`. Every enum MUST be in `domain/models/enums` and every exception in `domain/exceptions`.

#### Scenario: Class in the wrong folder
- **WHEN** a `@Service` class sits in `application/component`, or an `@Entity` class sits outside `domain/models`
- **THEN** the conventions check fails and names the class

#### Scenario: Unknown folder is rejected
- **WHEN** a main class sits in a folder that is not in the list, or in a subfolder of one that is
- **THEN** the conventions check fails and names the class

#### Scenario: Enum outside the enums folder
- **WHEN** a main class declares an enum outside `domain/models/enums`
- **THEN** the conventions check fails and names the enum

#### Scenario: Exception outside its folder
- **WHEN** a class whose name ends with `Exception` sits outside `domain/exceptions`
- **THEN** the conventions check fails and names the class

#### Scenario: Tests mirror the main folders
- **WHEN** a test class targets a main class
- **THEN** it sits in the same package under `src/test/java`

### Requirement: The domain sits below the application
Code in `domain` SHALL NOT depend on `application`, `transport` or `config`. Persistence repositories MUST be interfaces that extend `JpaRepository` and MUST NOT use `EntityManager`.

#### Scenario: Domain depends on the application
- **WHEN** a class in `domain` imports a class from `application`, `transport` or `config`
- **THEN** the conventions check fails and names the class and the import

#### Scenario: EntityManager in a repository
- **WHEN** a class in `domain/repositories` imports `EntityManager`
- **THEN** the conventions check fails and names the class

### Requirement: DTOs are plain data
Classes in `application/dto` SHALL carry fields and validation annotations only: no persistence annotation and no instance method, only static factories. The database model (entity) and the DTOs MUST be separate types converted by a mapper component.

#### Scenario: DTO carries persistence annotations
- **WHEN** a class in `application/dto` imports a persistence annotation
- **THEN** the conventions check fails and names the class

#### Scenario: Instance method in a DTO
- **WHEN** a class in `application/dto` declares a non-static method
- **THEN** the conventions check fails and names the method

#### Scenario: Static factory in a DTO
- **WHEN** a class in `application/dto` declares a static factory method
- **THEN** the conventions check does not report it

### Requirement: Task rules use the validation dependency
A task creation request MUST be validated with Bean Validation constraints and the application clock, and a violated rule SHALL be reported as the same rule as before: title required, title too long, urgency required, deadline required, deadline not in the future, start after deadline. A custom constraint is allowed only for a rule the dependency does not offer.

#### Scenario: Invalid task is refused with its rule
- **WHEN** a task is created with a blank title, a 501-character title, no urgency, no deadline, a deadline not after now, or a start after the deadline
- **THEN** creation is refused with the matching rule and the message never contains the title

#### Scenario: Valid task is accepted
- **WHEN** a task is created with a title of 500 characters, a deadline after now and a start equal to the deadline
- **THEN** it is created as a pending task with the title trimmed

### Requirement: The conventions are checked on every build
The automated conventions check SHALL run as part of the normal test run, so a violation fails `./mvnw verify` and the continuous integration pipeline.

#### Scenario: Clean tree passes
- **WHEN** the test suite runs on a tree with no violation
- **THEN** the conventions check passes

#### Scenario: Violation fails the build
- **WHEN** a violation is introduced and `./mvnw verify` runs
- **THEN** the build fails and the report lists every violation with its file and line

### Requirement: Reorganizing preserves behavior
Applying the conventions SHALL NOT change what the bot does. Every behavior scenario of the existing capabilities MUST still pass, and configuration keys, message keys and the database schema MUST be unchanged.

#### Scenario: Existing suite stays green
- **WHEN** the existing tests run after the reorganization, with only their package and type declarations adapted
- **THEN** every behavior test passes

#### Scenario: Configuration is unchanged
- **WHEN** the application starts with the same `application.yaml` and environment as before
- **THEN** it starts and binds every `app.*` property exactly as before
