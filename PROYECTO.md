# PROYECTO — users-management

Microservicio de gestión de usuarios (Spring Boot 3.3.5, Java 17, arquitectura hexagonal + DDD,
PostgreSQL con JDBC crudo). Es el **único punto de entrada del cliente**: también expone los
archivos de usuario, que físicamente guarda el file-service en Backblaze.

## Rutas

### Autenticación

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/auth/login` | público |

### Usuarios

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/users` | público |
| GET | `/api/users` | **ADMIN o REVIEWER** |
| GET | `/api/users/{id}` | ADMIN o REVIEWER |
| PUT | `/api/users/{id}` | ADMIN |
| DELETE | `/api/users/{id}` | ADMIN |

### Archivos de usuario (nuevo)

| Método | Ruta | Acceso | Acción |
|---|---|---|---|
| POST | `/api/users/{id}/files` | dueño o ADMIN | Recibe multipart (campo `file`), lo envía al file-service y guarda la fila en `user_files`. Responde `id`, `key`, `contentType`, `sizeBytes`, `createdAt`. |
| GET | `/api/users/{id}/files` | dueño o ADMIN | Lista las filas de `user_files` de ese usuario. |
| GET | `/api/users/{id}/files/{fileId}` | dueño o ADMIN | Devuelve una URL firmada de descarga, válida **300 s**. |
| DELETE | `/api/users/{id}/files/{fileId}` | dueño o ADMIN | Pide al file-service borrar la key y borra la fila. |

En `SecurityConfig` las rutas `/api/users/*/files` y `/api/users/*/files/**` se declaran **antes**
de la regla general `/api/users/**` y solo exigen estar autenticado. Si quedaran detrás, la regla
general (ADMIN o REVIEWER) las capturaría y un MEMBER no podría gestionar sus propios archivos.
La regla fina de "dueño o ADMIN" vive en los casos de uso (`UserFileUploadPolicy`), que es donde se
conoce el id del dueño.

### Otras

| Método | Ruta | Acceso |
|---|---|---|
| GET | `/health` | público |
| GET | `/swagger-ui.html`, `/v3/api-docs/**` | público |

## Flujo de archivos

1. El cliente envía el multipart a users-management con su `Authorization: Bearer <jwt>`.
2. users-management valida **antes de llamar al file-service**: archivo no vacío, **máximo 5 MB** y
   `contentType` en `application/pdf`, `image/png` o `image/jpeg`. Los límites de
   `spring.servlet.multipart.*` también están en `5MB`.
3. El adaptador `FileServiceRestAdapter` reenvía el archivo y el header `Authorization` **tal cual**
   a `FILE_SERVICE_URL`. El file-service valida el mismo JWT con el mismo `JWT_SECRET`.
4. Se guarda en `user_files` la **key** del objeto, nunca la URL firmada (caduca).
5. La descarga se hace pidiendo al file-service una URL firmada por 300 s en cada petición.

### Errores

| Situación | Respuesta |
|---|---|
| file-service inalcanzable, 5xx o `FILE_SERVICE_URL` vacío | **503** con mensaje claro (nunca un 500 genérico) |
| file-service rechaza el archivo (4xx) | el **mismo código** que devolvió el file-service |
| archivo mayor que el límite de multipart | 413 |
| archivo vacío o tipo no admitido | 400 |
| no es el dueño ni ADMIN | 403 |
| el archivo no existe o pertenece a otro usuario | 404 (no se confirma su existencia) |

### Borrado de usuario

`DELETE /api/users/{id}` borra el usuario de PostgreSQL y **después** pide al file-service borrar
todos sus archivos. Si el file-service falla, el borrado **no** se revierte: se registra el error y
se continúa. Los objetos quedan huérfanos en Backblaze — pendiente de una limpieza diferida. La
tabla `user_files` se vacía sola por `ON DELETE CASCADE`.

## Flujo de caché (Valkey)

Valkey habla el protocolo de Redis, así que se usa el cliente de Redis (Lettuce) sin cambios.

- **Qué se cachea:** solo `GET /api/users/{id}`.
- **Clave:** `user:{id}`.
- **Valor:** el `UserView` serializado en JSON con Jackson — `id`, `name`, `email`, `role`,
  `status`. **Nunca** la contraseña ni su hash, ni `UserModel`, ni entidades de persistencia.
- **TTL:** `CACHE_USER_TTL_SECONDS`, por defecto 300 s.
- **Lectura:** se intenta la caché; si hay valor se devuelve sin tocar PostgreSQL; si no, se lee de
  PostgreSQL, se guarda en caché y se devuelve.
- **Invalidación:** `PUT` y `DELETE /api/users/{id}` hacen `evict(id)` **después** de persistir.
- **Tolerancia a fallos:** no se usa `@Cacheable` a propósito. Con `@Cacheable`, un Valkey caído
  propaga la excepción y tumba el `GET`. En su lugar hay un puerto `UserCachePort` y dos
  adaptadores: `NoOpUserCacheAdapter` (`CACHE_TYPE=none`) y `ValkeyUserCacheAdapter`, que envuelve
  cada operación en `try/catch`, registra un aviso y **nunca lanza hacia afuera**. Con Valkey
  apagado la API sigue respondiendo desde PostgreSQL.
- **Apagado/encendido:** `CACHE_TYPE` (`none` o `valkey`), sin recompilar.

## Base de datos

Tabla nueva, se ejecuta a mano en Supabase (no hay Flyway). Está en
`src/main/resources/schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS user_files (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_key      VARCHAR(500) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_files_user_id ON user_files (user_id);
```

## Variables de entorno nuevas o cambiadas

| Variable | Ejemplo (sin valores reales) | Para qué |
|---|---|---|
| `FILE_SERVICE_URL` | `https://file-service-xxxx.onrender.com` | URL del file-service. Vacío ⇒ las rutas de archivos responden 503. |
| `VALKEY_URI` | `rediss://default:***@host:port` | Conexión a Valkey. La doble `s` es TLS. **Nunca** se escribe en el repositorio. |
| `CACHE_TYPE` | `none` (por defecto) o `valkey` | Activa o apaga la caché |
| `CACHE_USER_TTL_SECONDS` | `300` | TTL de la clave `user:{id}` |

`JWT_SECRET` **no cambia** y debe ser el mismo que usa el file-service.

## Pendiente

- **Kafka:** los tópicos actuales son tres (`kafka.topics.user-created|user-updated|user-deleted`,
  por defecto `users.created`, `users.updated`, `users.deleted`) y el mecanismo SASL por defecto es
  `PLAIN`. Falta unificarlos con Notification y search-service (tópico único `user-events`,
  `SCRAM-SHA-256`). El **payload de los eventos no cambia**.
- Limpieza diferida de los objetos huérfanos en Backblaze cuando falla `deleteAllOfUser`.
- Transactional Outbox para no perder eventos si el broker falla tras persistir.
