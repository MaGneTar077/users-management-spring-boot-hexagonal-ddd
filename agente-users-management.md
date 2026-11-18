# Agente 1: users-management (Spring Boot). Archivos y caché Valkey

> **Para Claude Code:** tu tarea es modificar el microservicio **users-management** (Spring Boot 3.3.5, Java 17, arquitectura hexagonal + DDD, PostgreSQL con JDBC crudo, sin JPA). Lee primero este documento completo. Si algo está marcado **[VERIFICAR]**, mira el código antes de asumir.

## 0. Reglas de trabajo (obligatorias)

- Antes de empezar, confirma la rama con `git branch --show-current`. Trabaja en la rama acordada con el equipo, no en `main`.
- Respuestas **cortas** en el chat. Sin resúmenes largos de lo que hiciste.
- Ejecuta **solo los tests de lo que cambiaste**. La suite completa solo una vez, al final.
- **Nunca** escribas secretos en código, en `application.properties` ni en commits. Usa variables de entorno. No subas `.env` ni `application-secrets.properties`.
- No cambies el **payload** de los eventos `user.created`, `user.updated` ni `user.deleted`. Notification y search-service dependen de ellos.
- Si una decisión no está en este documento, pregunta antes de inventarla.

## 1. Contexto

users-management ya existe y está desplegado en Render. Hoy hace:

- Login con JWT (`POST /api/auth/login`).
- Crear usuario con cuenta SAVINGS en una sola transacción (`POST /api/users`, público).
- Listar (`GET /api/users`, ADMIN o REVIEWER), consultar (`GET /api/users/{id}`, autenticado), actualizar (`PUT`, ADMIN) y borrar (`DELETE`, ADMIN).
- Publicar eventos en Kafka (Aiven).

Lo que falta y debes construir:

1. **Archivos:** subir, listar, obtener URL de descarga y borrar archivos. Los guarda el file-service en Backblaze. users-management es el **único punto de entrada** para el cliente.
2. **Caché con Valkey** para `GET /api/users/{id}`.
3. **Ajustes de Kafka** para que coincidan con los otros servicios.

## 2. Qué NO cambiar

- El modelo de dominio de usuarios (`UserModel` y sus reglas).
- El flujo de creación de usuario con su cuenta.
- El formato de los eventos Kafka.
- La regla de que las contraseñas nunca salen en respuestas ni en caché.

## 3. Paso 0: revisar el código existente

Antes de escribir nada, lista la estructura y reporta en el chat, en pocas líneas:

- El puerto de salida que usa `GET /api/users/{id}`.
- El caso de uso `DeleteUserUseCase` y dónde se publica `user.deleted`.
- `SecurityConfig` y cómo se extrae el id del usuario desde el JWT.
- `JwtTokenService` y cómo decodifica `JWT_SECRET` (Base64).
- `schema.sql` y el `pom.xml`.
- Si ya existe algo de `FILE_SERVICE_URL`, `VALKEY_URI` o `user_files` (la rama `feature/kafka-events` lo pudo haber empezado).

## 4. Parte A: archivos

### A1. Base de datos (`schema.sql`)

Añade esta tabla. Se ejecuta a mano en Supabase; no hay Flyway.

```sql
CREATE TABLE user_files (
  id            VARCHAR(36) PRIMARY KEY,
  user_id       VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  file_key      VARCHAR(500) NOT NULL,
  content_type  VARCHAR(100) NOT NULL,
  size_bytes    BIGINT NOT NULL,
  created_at    TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Guarda la **key** de Backblaze, nunca la URL firmada (caduca).

### A2. Hexágono

Sigue la estructura que ya use el proyecto. Si no hay una convención clara, usa esta:

- `application/port/out/FileServicePort`: `upload(userId, file, authHeader)`, `getDownloadUrl(key, authHeader)`, `deleteAllOfUser(userId, authHeader)`.
- `application/port/out/UserFileRepositoryPort`: guardar, listar por usuario, buscar por id, borrar.
- `application/port/in/`: `UploadUserFileUseCase`, `ListUserFilesUseCase`, `GetUserFileUrlUseCase`, `DeleteUserFileUseCase`.
- `application/service/`: implementación de los casos de uso. Aquí van las reglas de dueño o ADMIN.
- `infrastructure/adapter/out/filestorage/`: adaptador `RestClient` hacia `FILE_SERVICE_URL`. **Reenvía el header `Authorization` tal cual.**
- `infrastructure/adapter/out/persistence/`: repositorio JDBC para `user_files`.
- `entrypoint/rest/UserFileController`: rutas de archivos. DTOs en su propio paquete.

**Regla de dominio:** nada de Spring, JDBC ni `RestClient` dentro de `domain` ni `application`.

### A3. Endpoints (los expone users-management)

| Método | Ruta | Acceso | Acción |
|---|---|---|---|
| POST | `/api/users/{id}/files` | Dueño o ADMIN | Recibe multipart (campo `file`), lo envía al file-service y guarda la fila en `user_files`. Responde `id`, `key`, `contentType`, `sizeBytes`. |
| GET | `/api/users/{id}/files` | Dueño o ADMIN | Lista las filas de `user_files` de ese usuario. |
| GET | `/api/users/{id}/files/{fileId}` | Dueño o ADMIN | Busca la key en `user_files`, pide la URL firmada al file-service y la devuelve (válida 5 minutos). |
| DELETE | `/api/users/{id}/files/{fileId}` | Dueño o ADMIN | Pide al file-service borrar la key y borra la fila. |

**Orden en `SecurityConfig`:** las rutas `/api/users/*/files/**` deben quedar **antes** de la regla general `/api/users/**`. Si no, la regla general las captura.

**Validación en users-management** (antes de llamar al file-service):
- El archivo no está vacío y pesa **máximo 5 MB**.
- El `contentType` es `application/pdf`, `image/png` o `image/jpeg`.
- `spring.servlet.multipart.max-file-size` y `max-request-size` en `5MB`.

### A4. Borrado de usuario

En `DeleteUserUseCase`, **después** de borrar el usuario de PostgreSQL, llama a `deleteAllOfUser`. Si falla el file-service, **no** reviertas el borrado: registra el error y sigue. Ese caso queda como pendiente para el informe. (La tabla `user_files` se borra sola por `ON DELETE CASCADE`.)

### A5. Errores

- El file-service caído devuelve **503** con un mensaje claro. No un 500 genérico.
- Un archivo rechazado por el file-service (tipo o tamaño) se devuelve con el mismo código que recibió.

## 5. Parte B: caché con Valkey

### B1. Dependencias y configuración

En `pom.xml`: `spring-boot-starter-data-redis` y `spring-boot-starter-cache` (Valkey es compatible con el protocolo de Redis).

En `application.properties`:

```properties
spring.data.redis.url=${VALKEY_URI:}
spring.data.redis.timeout=5s
cache.user.ttl-seconds=${CACHE_USER_TTL_SECONDS:300}
cache.type=${CACHE_TYPE:none}
```

`VALKEY_URI` tiene formato `rediss://default:<PASSWORD>@<HOST>:<PUERTO>`. La `s` doble es TLS. **No** pongas la URI real en el repositorio.

### B2. Diseño (hexagonal, tolerante a fallos)

**No uses `@Cacheable` de Spring** en el caso de uso. Si Valkey falla, `@Cacheable` lanza error y tumba `GET`. Usa un puerto:

- `application/port/out/UserCachePort`: `Optional<UserResponse> get(String id)`, `void put(String id, UserResponse dto)`, `void evict(String id)`.
- `infrastructure/adapter/out/cache/NoOpUserCacheAdapter`: se usa cuando `CACHE_TYPE=none`. Devuelve vacío y no hace nada.
- `infrastructure/adapter/out/cache/ValkeyUserCacheAdapter`: usa `RedisTemplate` o `StringRedisTemplate` con JSON. Cada operación va en `try/catch`. Si falla, registra un aviso y sigue. **Nunca lanza excepción hacia afuera.**
- `config/CacheConfig`: crea el bean según `cache.type`.

### B3. Qué se cachea

- **Clave:** `user:{id}`.
- **Valor:** el **mismo DTO que responde** `GET /api/users/{id}`. Debe ser un DTO **sin contraseña ni hash**. Verifica esto en el código antes de cachear.
- **Nunca** cachees `UserModel`, entidades ni el hash de BCrypt.
- **TTL:** `CACHE_USER_TTL_SECONDS` (por defecto 300).

### B4. Lectura y escritura

1. `GET /api/users/{id}`: intenta `get`. Si hay valor, devuélvelo. Si no, consulta PostgreSQL, guarda con `put` y devuelve.
2. `PUT /api/users/{id}`: actualiza en PostgreSQL y después `evict(id)`.
3. `DELETE /api/users/{id}`: borra en PostgreSQL y después `evict(id)`.

### B5. Serialización

Serializa con JSON (Jackson), no con serialización de Java. Así Redis Insight muestra valores legibles y no hay problemas al cambiar la clase.

## 6. Parte C: ajustes de Kafka

- `KAFKA_ENABLED=true`.
- `KAFKA_TOPIC_USER_EVENTS=user-events`. El valor por defecto `users.events` **no** coincide con Notification ni con search-service. Cambia el valor por defecto en `application.properties` o deja la variable obligatoria.
- `KAFKA_SECURITY_PROTOCOL=SASL_SSL`, `KAFKA_SASL_MECHANISM=SCRAM-SHA-256`, `KAFKA_USERNAME`, `KAFKA_PASSWORD`, `KAFKA_CA_CERT`.
- `KAFKA_BOOTSTRAP_SERVERS` con el host **y el puerto** de `kafka-1653cf19`: `kafka-1653cf19-wallet-service.j.aivencloud.com:13099`.
- **[VERIFICAR]** cómo se lee `KAFKA_CA_CERT` cuando el certificado tiene saltos de línea. Si la conexión falla con error de certificado, este es el primer sitio a revisar.

## 7. Variables de entorno nuevas o cambiadas

| Variable | Ejemplo (sin valores reales) | Para qué |
|---|---|---|
| `FILE_SERVICE_URL` | `https://file-service-xxxx.onrender.com` | URL del file-service |
| `VALKEY_URI` | `rediss://default:***@host:port` | Conexión a Valkey |
| `CACHE_TYPE` | `none` o `valkey` | Activa o apaga la caché |
| `CACHE_USER_TTL_SECONDS` | `300` | Tiempo de vida de la caché |
| `KAFKA_TOPIC_USER_EVENTS` | `user-events` | Tópico de eventos de usuario |
| `KAFKA_ENABLED` | `true` | Publicar eventos |

El secreto `JWT_SECRET` **no cambia**. Debe ser el mismo que usa el file-service.

## 8. Documentación

Actualiza `PROYECTO.md` con: las rutas nuevas, las variables nuevas, que `GET /api/users` requiere ADMIN o REVIEWER, y el flujo de caché y de archivos.

## 9. Criterios de aceptación

- [ ] `POST /api/users/{id}/files` sube un PDF y devuelve `key`. La fila aparece en `user_files`.
- [ ] `GET /api/users/{id}/files/{fileId}` devuelve una URL firmada que abre el archivo.
- [ ] Un usuario no puede ver ni subir archivos de otro (403). ADMIN sí.
- [ ] Con `CACHE_TYPE=valkey`, la segunda `GET /api/users/{id}` no consulta PostgreSQL (verificable con log).
- [ ] La clave `user:{id}` aparece en Redis Insight con TTL de 300 s.
- [ ] `PUT` y `DELETE` borran la clave de caché.
- [ ] Si Valkey está apagado, `GET /api/users/{id}` sigue respondiendo desde PostgreSQL.
- [ ] Si el file-service está apagado, subir archivo responde 503, pero crear y consultar usuarios funciona.
- [ ] `user.created` aparece en `user-events` con el mismo payload de antes.
- [ ] Ningún secreto está en el código ni en el repositorio.
