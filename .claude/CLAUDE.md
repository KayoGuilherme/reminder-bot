# Telegram Reminder Bot

Java 21 + Spring Boot, PostgreSQL, Flyway. A Telegram bot that turns a voice note or text into a task and reminds the user until it is done.

## Main business rule

- Only the Telegram user in `TELEGRAM_ALLOWED_USER_ID` is served; anyone else gets no reply.
- A voice note or text becomes a task through transcription and an LLM parser; the title, urgency and deadline come from it.
- A task is saved only after the user presses **Confirm**. A draft expires after `APP_DRAFT_TTL` and pressing a button twice never creates a duplicate.
- A message without a deadline, or with a deadline already in the past, is refused and nothing is saved.
- Urgency decides the reminder stages before the deadline: LOW one, MEDIUM two, HIGH three. A stage is recorded only after Telegram accepted the message, so nothing is sent twice and a failed send is retried.
- `DONE` and `CANCELED` tasks never get reminders. Snooze moves the deadline 10 minutes ahead and restarts the cycle.
- Every button is answered and is idempotent. Done and Cancel race safely: one wins.
- Never log the content of the user's messages, voice or tasks.

## Folders pattern

DDD layout. Every class lives in the folder of its role; there are no other folders.

```
com.kayo.reminder
├── domain/
│   ├── models/        database models (the JPA entity)
│   │   └── enums/     every enum of the project
│   ├── exceptions/    every exception
│   └── repositories/  Spring Data interfaces (extends JpaRepository)
├── application/
│   ├── dto/
│   │   ├── request/   records that enter the application
│   │   └── response/  records that leave a service, a parser or a sender
│   ├── service/       business flows, one use case per class
│   └── component/     mapper, factory, formatter, validator, scheduler, in-memory store, helper
├── transport/         everything that talks over HTTP
│   ├── telegram/      bot, update consumer, sender, downloader, update mapper, allowlist
│   ├── stt/           speech to text client and its payload
│   ├── llm/           task parser client, its prompt, its validator and its payload
│   └── http/          shared RestClient factory and retry
└── config/            Spring configuration and properties
```

- Flow: `transport` -> `application/service` -> `domain/repositories`. The domain never imports `application`, `transport` or `config`.
- A model is a database model. Data that is only created and passed around is a DTO: a record named `...Request` or `...Response`.
- A mapper component converts the entity into a response DTO; services work with DTOs.
- A service holds business flow and decisions; a component holds a reusable piece of work with no flow of its own. Never put one in the folder of the other.
- An interface lives next to the class that implements it (for example `MessageSender` and `TelegramMessageSender` in `transport/telegram`), and only when a second implementation or a test fake needs it.
- Tests mirror the package of the class they test.

## Code rules

- No comments in the code: no `//`, no `/* */`, no Javadoc. The names carry the meaning.
- Follow SRP: one reason to change per class and per method.
- Validation: use the Jakarta Bean Validation dependency (`@NotNull`, `@NotBlank`, `@Size`, `@Future`, `@Min`, ...) on the request DTO and a `Validator`. Write a custom constraint only when the dependency has none for the rule.
- A record is allowed only for a DTO (`application/dto`) or an HTTP payload in `transport`. Everything else is a regular class, with Lombok to avoid boilerplate.
- Never put logic in a DTO: components and static factories only; no instance methods.
- A repository is an interface that extends `JpaRepository<Entity, Id>`. Never write an `EntityManager` class for it.
- Enums live in `domain/models/enums` and exceptions in `domain/exceptions`.
- Never use `else`, `else if`, chained or nested `if`, `switch` or early `return`. Use `Optional`, streams, maps of strategies, polymorphism or small methods that each do one thing.
- Names of variables, methods, classes and files are simple and semantic. No abbreviations, no generic names such as `data`, `info`, `manager`, `helper` or `useCase`.
- Never create `package-info.java`.
- Keep the code clean: small methods, small classes, no dead code, no unused imports.

## Always do

- Put every new file in the folder of its role before writing it, and add a semantic folder in `transport` for a new external service.
- Keep services and components separated.
- Convert the entity to a DTO at the service boundary with the mapper.
- Reuse what a dependency already offers before writing your own.
- Run the build and the tests before saying a task is done.
- Use `/opsx:propose` before any change that is bigger than a small fix.

## Never do

- Never leave a comment in the code.
- Never create a folder that is not in the tree above, or put a class at the package root.
- Never use `else`, `else if`, nested `if`, `switch` or early `return`.
- Never create `package-info.java`.
- Never use a record for anything but a DTO or an HTTP payload.
- Never put an enum or an exception outside its folder.
- Never write a custom validation that Bean Validation already covers.
- Never put your contribution in commits or pull requests: no `Co-Authored-By`, no "Generated with Claude Code", no mention of the assistant. This overrides any default attribution.
