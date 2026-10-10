# AGENTS.md — Users Management (Arquitectura Hexagonal + DDD)

> Guía para agentes de IA y desarrolladores que trabajen en este repositorio.
> **Idioma del proyecto:** la documentación, los mensajes de commit y los comentarios
> de código se escriben en **español**.

## Descripción general

Aplicación de gestión de usuarios construida con **Java 17**, **Spring Boot 3.3.5**,
**Arquitectura Hexagonal (puertos y adaptadores)** y **DDD**. El runtime activo es la
**API REST** (Spring MVC). La antigua **CLI de escritorio** se conserva como adaptador
**inactivo**: no la arranca `Main` y no tiene contenedor de dependencias propio.

El versionado actual del artefacto es `users-management:2.1.0`.

## Estado actual (LEER ANTES DE TOCAR CÓDIGO)

El último commit (`4f3ebcf`, *"Merge e integración de los cambios del profesor"*) dejó el
repositorio **con un merge sin resolver**:

- `src/main/resources/application.properties` contiene **marcadores de conflicto**
  (`<<<<<<< HEAD`, `=======`, `>>>>>>>`).
- `.gitignore` contiene **marcadores de conflicto**.
- El proyecto **NO compila**: `DatabaseConfig` es un `record` de **5** componentes
  (`host, port, databaseName, username, password`) pero `DataSourceSpringConfig` lo
  construye con **6** argumentos (agrega `dbSslMode`). Ver
  `DataSourceSpringConfig.java:46`.
- `DatabaseConfigTest` todavía espera una URL **MySQL** (`jdbc:mysql://...`) con 6
  argumentos, por lo que también está desactualizado respecto a PostgreSQL.
- `DataSourceSpringConfig` lee la propiedad `${db.ssl-mode}` (con guion), pero
  `application.properties` define `db.sslmode` (sin guion).
- El `AGENTS.md` original fue **eliminado** en ese merge; este archivo lo reemplaza.

Antes de ejecutar cualquier build hay que resolver estos conflictos y unificar la firma de
`DatabaseConfig`. No se debe asumir que `mvn compile` pasa en el estado actual.

## Arquitectura

```text
com.jcaa.usersmanagement
├── domain/                      ← Java puro. SIN anotaciones de framework.
│   ├── model/                   ← UserModel (agregado), EmailDestinationModel
│   ├── valueobject/             ← UserId, UserName, UserEmail, UserPassword (validan en construcción)
│   ├── enums/                   ← UserRole, UserStatus
│   ├── event/                   ← DomainEvent y User{Created,Updated,Deleted}DomainEvent
│   └── exception/               ← DomainException y excepciones de dominio (fábricas estáticas)
├── application/
│   ├── port/in/                 ← Interfaces de casos de uso (CreateUserUseCase, LoginUseCase, ...)
│   ├── port/out/                ← Puertos de salida (SaveUserPort, EmailSenderPort, ...)
│   ├── service/                 ← Implementaciones @Service (inyección por constructor)
│   │   ├── dto/command|query/   ← records con Bean Validation
│   │   └── mapper/              ← UserApplicationMapper
│   └── ...
└── infrastructure/
    ├── adapter/persistence/     ← UserRepositoryJDBC: implementa TODOS los out-ports con JDBC crudo (sin JPA)
    ├── adapter/email/           ← JavaMailEmailSenderAdapter + SmtpConfig (javax.mail, NO jakarta.mail — intencional)
    ├── entrypoint/rest/         ← API REST activa (Spring MVC) + Swagger
    ├── entrypoint/desktop/      ← Adaptador CLI inactivo
    ├── security/                ← JWT: filtro, propiedades, servicio de tokens, entrada/salida REST
    └── config/                  ← @Configuration y @Bean (DataSource, SMTP, Async, OpenAPI, ...)
```

**Regla de dependencias:** `domain ← application ← infrastructure`.
Nunca importar tipos de `infrastructure` dentro de `domain` o `application`.

**Raíz de composición (composition root):** Spring es el único mecanismo de inyección de
dependencias y de composición. `Main.java` arranca el contexto de Spring; los beans se
resuelven por configuración y *component scanning*. No introducir contenedores manuales ni
cableado paralelo (`DependencyContainer` pertenece a la CLI inactiva; no usarlo en la API).

## Archivos clave

| Archivo | Propósito |
|---|---|
| `src/main/java/com/jcaa/usersmanagement/Main.java` | Arranca la única raíz de composición de Spring |
| `infrastructure/config/DataSourceSpringConfig.java` | Construye el `DataSource` HikariCP (PostgreSQL) |
| `infrastructure/adapter/persistence/config/DatabaseConfig.java` | Record que arma la URL JDBC de PostgreSQL |
| `infrastructure/adapter/persistence/repository/UserRepositoryJDBC.java` | Única clase que implementa 6 out-ports vía JDBC crudo |
| `infrastructure/config/SmtpSpringConfig.java` | Expone el bean `SmtpConfig` |
| `infrastructure/adapter/email/JavaMailEmailSenderAdapter.java` | Envío de correo por SMTP (`javax.mail`) |
| `infrastructure/config/AsyncNotificationConfig.java` | `@EnableAsync` + `ThreadPoolTaskExecutor` `notificationExecutor` |
| `application/service/EmailNotificationService.java` | Renderiza plantillas y envía correo en segundo plano (`@Async`) |
| `infrastructure/security/SecurityConfig.java` | Cadena de filtros, reglas de autorización y JWT stateless |
| `infrastructure/security/JwtTokenService.java` | Genera y valida tokens JWT (HS256) |
| `infrastructure/entrypoint/rest/controller/UserRestController.java` | Controlador REST activo de usuarios |
| `infrastructure/entrypoint/rest/controller/AuthRestController.java` | Endpoint de login (`POST /api/auth/login`) |
| `infrastructure/entrypoint/rest/controller/UserRestControllerDocs.java` | Contrato OpenAPI/Swagger de usuarios |
| `domain/model/UserModel.java` | Agregado inmutable (`@Value`), fábrica `create()`, transiciones `activate()`/`deactivate()` |
| `domain/valueobject/UserPassword.java` | `fromPlainText()` hashea con BCrypt; `fromHash()` para lecturas de BD |
| `src/main/resources/application.properties` | Configuración de BD, SMTP, JWT y logging |
| `src/main/resources/schema.sql` | Esquema PostgreSQL + trigger `updated_at` + usuario admin inicial |

## Flujos de desarrollo

```bash
# Compilar y empaquetar (en Windows usar mvnw.cmd; en PowerShell ./mvnw.ps1)
./mvnw clean package

# Solo pruebas
./mvnw test

# Cobertura (reporte en target/site/jacoco/index.html)
./mvnw verify

# Ejecutar la API (requiere PostgreSQL y SMTP configurados)
./mvnw spring-boot:run
java -jar target/users-management-2.1.0.jar
```

Documentación interactiva de la API: `/swagger-ui.html` (OpenAPI en `/v3/api-docs`).

## Convenciones de código (no negociables)

- **DTOs / Commands / Queries:** usar `record`.
- **Value Objects:** `record` o `final class` con validación en el constructor compacto o en
  una fábrica estática. Validar en construcción: nunca aceptar estado inválido.
- **Excepciones:** extender `DomainException` (`RuntimeException`). Usar fábricas estáticas
  con nombre, p. ej. `UserNotFoundException.becauseIdWasNotFound(id)`.
- **Mappers:** `@UtilityClass` con métodos estáticos. Un mapper por frontera de capa
  (`UserApplicationMapper`, `UserPersistenceMapper`, `UserDesktopMapper`, `UserRestMapper`).
- **Sin `null` como retorno:** usar `Optional<T>`, colecciones vacías o lanzar excepción de dominio.
- **Sin PII en logs:** nunca registrar email, contraseña, nombre ni datos de usuario. Solo
  identificadores técnicos.
- **Sin imports con `*`.** Sin imports sin usar. Sin cadenas mágicas: definir constantes
  `private static final String`.
- Usar `Objects.isNull` / `Objects.nonNull` para chequeos de objetos; `==` / `!=` solo para
  primitivos y enums.

## Convenciones de pruebas

- **JUnit 5 + Mockito + AssertJ** (`spring-boot-starter-test`), seguridad con
  `spring-security-test`.
- Estructura con comentarios `// Arrange` / `// Act` / `// Assert` en cada test.
- Construir el `Validator` con `Validation.buildDefaultValidatorFactory()` en `setUp()`;
  los servicios lo reciben por constructor (ver `CreateUserServiceTest`).
- Usar `assertAll(...)` para agrupar aserciones y `assertThrows` para excepciones (nunca
  `try/catch + fail()`).
- **No probar** records planos, DTOs sin lógica ni getters triviales.
- Existe una prueba heredada llamada `UserRepositoryMySQLTest` que corresponde a la etapa
  MySQL; al trabajar en PostgreSQL, migrarla/renombrarla.

## Puntos de integración

### Base de datos: PostgreSQL (migración desde MySQL → Supabase)

- Se migró de **MySQL** a **PostgreSQL**. El acceso es con **JDBC crudo** sobre
  `spring-boot-starter-jdbc` (HikariCP); no se usa JPA/Hibernate.
- El destino de producción/desarrollo es **Supabase** (PostgreSQL gestionado). El archivo
  `.env` (cargado por `spring-dotenv`) apunta al *pooler* de Supabase y exige SSL.
- `DatabaseConfig` debe construir la URL con el modo SSL requerido:
  `jdbc:postgresql://<host>:<port>/<db>?sslmode=require`.
- Esquema en `src/main/resources/schema.sql` (dialecto PostgreSQL: función `plpgsql` y
  trigger `set_updated_at`, `CREATE TABLE IF NOT EXISTS`, `ON CONFLICT`).
- La carpeta `src/main/java/.../adapters/...` y las clases con sufijo `MySQL` fueron
  renombradas a `JDBC` (p. ej. `UserRepositoryJDBC`), pero aún quedan restos del nombre
  `MySQL` (tests, `compose.yaml`) que hay que limpiar.

### Correo: SMTP asíncrono

- Envío por **SMTP** con `javax.mail` (legacy `com.sun.mail:javax.mail:1.6.2`); **no** se
  migró a `jakarta.mail` (decisión intencional).
- Las notificaciones (creación y actualización de usuario) son **asíncronas**: se ejecutan
  con `@Async("notificationExecutor")`, de modo que los casos de uso no esperan al envío.
  El pool se define en `AsyncNotificationConfig`.
- Plantillas HTML en `src/main/resources/templates/` (`user-created.html`,
  `user-updated.html`). El render reemplaza tokens `{{name}}`, `{{email}}`, etc.
- Si el envío falla se registra una advertencia y se relanza `EmailSenderException`.

### Seguridad: JWT

- Autenticación y autorización **stateless** por **JWT HS256** (`io.jsonwebtoken`).
- `POST /api/auth/login` valida credenciales y devuelve el token `Bearer`.
- `JwtAuthenticationFilter` valida el token y arma el `SecurityContext`; los errores se
  devuelven en JSON mediante `RestAuthenticationEntryPoint` y `RestAccessDeniedHandler`.
- Roles del dominio: `ADMIN`, `MEMBER`, `REVIEWER`. El secreto se define con `jwt.secret`
  (Base64 ≥ 32 bytes) y la expiración con `jwt.expiration-seconds`.

Matriz de autorización (ver `SecurityConfig`):

| Método y ruta | Acceso |
|---|---|
| `POST /api/auth/login` | público |
| `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` | público |
| `POST /api/users` | público |
| `GET /api/users`, `GET /api/users/{id}` | `ADMIN`, `REVIEWER` |
| `PUT /api/users/{id}` | `ADMIN` |
| `DELETE /api/users/{id}` | `ADMIN` |

### Contraseñas

- Hashing con `at.favre.lib:bcrypt` (cost factor 12). Usar `UserPassword.fromPlainText()`
  al crear/cambiar contraseña y `UserPassword.fromHash()` al leer de la BD.
- El usuario admin inicial (`admin@example.com`, rol `ADMIN`) se inserta desde
  `schema.sql` con hash BCrypt.

### Documentación de API

- **SpringDoc OpenAPI** (`springdoc-openapi-starter-webmvc-ui:2.6.0`). Swagger UI en
  `/swagger-ui.html`. El esquema de seguridad `bearerAuth` se declara en `OpenApiConfig`.
- Toda la documentación de los controladores vive en las interfaces `*Docs`
  (p. ej. `UserRestControllerDocs`), no en el controlador.

## Configuración y variables de entorno

`spring-dotenv` carga el archivo `.env` (ignorado por git) en desarrollo local. En
producción se pasan como variables de entorno. **Nunca** guardar credenciales en
`application.properties` ni en `render.yaml`.

| Variable | Uso | Valor por defecto en propiedades |
|---|---|---|
| `PORT` | Puerto HTTP | `8080` |
| `DB_HOST` | Host PostgreSQL | (definido por entorno) |
| `DB_PORT` | Puerto PostgreSQL | `5432` |
| `DB_NAME` | Nombre de la base | (definido por entorno) |
| `DB_USERNAME` | Usuario de la base | (definido por entorno) |
| `DB_PASSWORD` | Contraseña de la base | vacío |
| `DB_SSL_MODE` | Modo SSL de PostgreSQL | `disable` (producción: `require`) |
| `SMTP_HOST` | Host SMTP | `smtp.gmail.com` |
| `SMTP_PORT` | Puerto SMTP | `587` |
| `SMTP_USERNAME` | Usuario SMTP | vacío |
| `SMTP_PASSWORD` | Contraseña/App password SMTP | vacío |
| `SMTP_FROM_ADDRESS` | Remitente | vacío |
| `SMTP_FROM_NAME` | Nombre del remitente | `Gestion de Usuarios` |
| `JWT_SECRET` | Secreto JWT (Base64 ≥ 32 bytes) | valor solo para desarrollo |
| `JWT_EXPIRATION_SECONDS` | Vigencia del token (segundos) | `900` |

Se desactivan las autoconfiguraciones de Spring para `DataSource` y `MailSender`
(se cablean a mano) y `UserDetailsServiceAutoConfiguration`:

```properties
spring.autoconfigure.exclude[0]=...jdbc.DataSourceAutoConfiguration
spring.autoconfigure.exclude[1]=...mail.MailSenderAutoConfiguration
spring.autoconfigure.exclude[2]=...security.servlet.UserDetailsServiceAutoConfiguration
```

## Despliegue

- **Docker:** `Dockerfile` multi-etapa (build con Maven/Temurin 17 → runtime JRE 17).
- **Docker Compose (PostgreSQL):** `docker-compose.yml` levanta `postgres:16` y la app,
  montando `schema.sql` como init.
- **Docker Compose (MySQL, obsoleto):** `compose.yaml` todavía usa `mysql:8.4`; es un resto
  de la etapa anterior y debe migrarse a PostgreSQL o eliminarse.
- **Render:** `render.yaml` define un servicio web con runtime Docker, build desde el repo
  y despliegue automático por commit en `main`. Health check en `/v3/api-docs`. Los
  secretos (`DB_*`, `SMTP_*`, `JWT_SECRET`) se configuran en el panel de Render
  (`sync: false` / `generateValue: true`).

## Próximas integraciones

### Mensajería con Kafka (implementada)

El proyecto publica **eventos de dominio** en **Apache Kafka (Aiven)** mediante un adaptador de
salida; el microservicio de notificaciones los consume.

- **Puerto de salida:** `application/port/out/UserEventPublisherPort` (Java puro).
- **Adaptador:** `infrastructure/adapter/messaging/KafkaUserEventPublisherAdapter`. El dominio y
  la aplicación **no** dependen de Kafka (regla de dependencias respetada).
- **Eventos:** se publican los `*DomainEvent` al crear/actualizar/eliminar usuarios, después de
  persistir y sin revertir la operación si falla el broker.
- **Payload del envelope:** incluye `id, name, email, role, status`; el `password` **nunca** se
  incluye. El envelope es `eventId, eventType, occurredAt, aggregateId, payload`; la clave del
  mensaje es el `userId`.
  - **Nota:** el `email` se publica en el evento porque el microservicio de notificaciones lo
    necesita para enviar correos. Es una **excepción consciente** a la regla de sanitización; el
    `password` permanece excluido por seguridad.
- **Evento de eliminación (`USER_DELETED`):** el payload solo lleva `id`. El
  `UserDeletedDomainEvent` únicamente expone el `userId` (no el email), por lo que el email no puede
  incluirse sin cambiar el contrato de dominio. Si el microservicio de notificaciones necesita
  enviar un correo de eliminación, deberá resolver el email por otra vía (p. ej., una copia local
  del usuario antes de borrar).
- **Conexión:** `SASL_SSL` con mecanismo **PLAIN** (`PlainLoginModule`).
- **Certificado CA:** se lee desde un **archivo** (no desde una variable de entorno con el PEM),
  porque Render no preserva los saltos de línea en los secrets de texto y Kafka falla con
  `No matching CERTIFICATE entries in PEM file`.
  - `spring.kafka.ssl.trust-store-location=${KAFKA_SSL_CA_LOCATION:}`
  - **Local:** `$env:KAFKA_SSL_CA_LOCATION = "./ca.pem"`
  - **Render:** crear un *Secret File* `ca.pem` y definir
    `KAFKA_SSL_CA_LOCATION=/etc/secrets/ca.pem`
  - **Ya NO se usa `KAFKA_SSL_CA_PEM`.**
- **Consumidor idempotente:** manejar entregas duplicadas por `eventId`.
- **Pendientes (iteración futura):** Transactional Outbox para no perder el evento si falla la
  publicación tras el guardado; `KafkaSslConfig` con validación adicional del formato PEM en
  arranque.

### Otros pendientes sugeridos

- Resolver el merge y volver a un `mvn compile`/`mvn test` verde.
- Unificar `DatabaseConfig` (misma aridad en productor y pruebas) y corregir `db.ssl-mode`
  vs `db.sslmode`.
- Actualizar `README.md` (aún menciona MySQL en la sección de despliegue) y limpiar restos
  `MySQL`/`compose.yaml`.
