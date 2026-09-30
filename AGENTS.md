# AGENTS.md — Users Management (Hexagonal + DDD)

## Project Overview

User management application built with **Java 17**, **Spring Boot 3.3.5**, **Hexagonal Architecture** and **DDD**. The active runtime is the REST API. The desktop CLI remains as an inactive adapter and is not launched by `Main`.

## Architecture

```
domain/          ← Pure Java. No framework annotations. Value Objects as records/final classes.
application/
  port/in/       ← Use case interfaces (e.g. CreateUserUseCase)
  port/out/      ← Output port interfaces (e.g. SaveUserPort)
  service/       ← Service implementations — @Service, injected through constructors
  service/dto/   ← command/ and query/ records with Bean Validation constraints
infrastructure/
  adapter/persistence/   ← UserRepositoryMySQL: implements ALL out-ports via raw JDBC (no JPA)
  adapter/email/         ← JavaMailEmailSenderAdapter (javax.mail, NOT jakarta.mail — intentional)
  entrypoint/rest/       ← Active Spring MVC REST API
  entrypoint/desktop/    ← Inactive CLI adapter; it has no independent composition root
  config/                ← Spring @Configuration and @Bean infrastructure wiring
```

**Dependency rule:** domain ← application ← infrastructure. Never import infrastructure types in domain or application layers.

**Composition root:** Spring is the only dependency injection and application composition mechanism. `Main.java` starts the Spring application context. Infrastructure is provided through Spring configuration and component scanning. Do not introduce manual containers or parallel wiring paths.

## Key Files

| File | Purpose |
|---|---|
| `Main.java` | Starts the single Spring composition root |
| `infrastructure/config/DataSourceSpringConfig.java` | Builds the HikariCP `DataSource` bean |
| `infrastructure/config/SmtpSpringConfig.java` | Builds the SMTP configuration bean |
| `infrastructure/adapter/persistence/repository/UserRepositoryMySQL.java` | Single class implements 6 out-ports via raw JDBC |
| `domain/model/UserModel.java` | Core aggregate — immutable (`@Value`), factory method `create()`, state transitions `activate()`/`deactivate()` |
| `domain/valueobject/UserPassword.java` | `fromPlainText()` hashes with BCrypt; `fromHash()` for DB reads |
| `infrastructure/entrypoint/rest/controller/UserRestController.java` | Active REST controller |
| `infrastructure/entrypoint/desktop/controller/UserController.java` | Inactive desktop adapter (plain class) |
| `src/main/resources/application.properties` | DB and SMTP config (no Spring datasource auto-config) |
| `spec/reglas-de-codificacion-java.md` | **Authoritative coding rules** — read before making any changes |

## Developer Workflows

```bash
# Build & test
mvn clean install

# Tests only
mvn test

# Coverage report (generated at target/site/jacoco/index.html)
mvn verify

# Run the application (requires MySQL + SMTP configured in application.properties)
mvn spring-boot:run
# or
java -jar target/users-management-2.0.0-SNAPSHOT.jar
```

## Coding Conventions (non-negotiable — from `spec/reglas-de-codificacion-java.md`)

- **DTOs/Commands/Queries:** use `record`.  
- **Value Objects:** `record` or `final class` with validation in compact constructor or static factory. Validate on construction — never accept invalid state.  
- **Exceptions:** extend `DomainException` (extends `RuntimeException`). Use named static factories: `UserNotFoundException.becauseIdWasNotFound(id)`.  
- **Mappers:** `@UtilityClass` with static methods. One mapper per layer boundary (e.g. `UserApplicationMapper`, `UserPersistenceMapper`, `UserDesktopMapper`).  
- **No `null` returns:** use `Optional<T>`, empty collections, or throw domain exceptions.  
- **No PII in logs:** never log email, password, name, or any user data. Log technical identifiers only.  
- **No `*` imports.** No unused imports. No magic strings — define as `private static final String` constants.  
- **`Objects.isNull` / `Objects.nonNull`** for object null checks; `==` / `!=` only for primitives and enums.

## Test Conventions

- Framework: **JUnit 5 + Mockito + AssertJ** (`spring-boot-starter-test`).  
- Structure: `// Arrange` / `// Act` / `// Assert` comments in every test.  
- Build `Validator` from `Validation.buildDefaultValidatorFactory()` in `setUp()` — services receive it via constructor (see `CreateUserServiceTest`).  
- Use `assertAll(...)` for grouping related assertions.  
- Use `assertThrows` for exceptions, never `try/catch + fail()`.  
- **Do not test** plain records, DTOs without logic, or trivial getters.

## External Dependencies & Integration Points

- **Database:** MySQL via raw JDBC (`spring-boot-starter-jdbc` provides `HikariCP`). Schema in `src/main/resources/schema.sql`. Config keys: `db.host`, `db.port`, `db.name`, `db.username`, `db.password`.  
- **Email:** SMTP via `javax.mail` (legacy `com.sun.mail:javax.mail:1.6.2`). **Not** migrated to `jakarta.mail`. Config keys: `smtp.host`, `smtp.port`, `smtp.username`, `smtp.password`, `smtp.from.address`, `smtp.from.name`.  
- **Password hashing:** `at.favre.lib:bcrypt` — cost factor 12. Always use `UserPassword.fromPlainText()` for new passwords and `UserPassword.fromHash()` when reading from DB.

