# Users Management — Spring Boot, Arquitectura Hexagonal y DDD

Aplicación de gestión de usuarios construida con Java 17 y Spring Boot. La API REST es el punto de entrada activo. El código de la antigua CLI se conserva como adaptador inactivo y no posee un contenedor de dependencias independiente.

Spring es el único *composition root*: `Main` inicia el contexto y las dependencias se resuelven mediante configuración y component scanning de Spring.

## Verificación

```bash
./mvnw clean test
./mvnw clean package
```

En Windows se puede utilizar `mvnw.cmd`.

## Eventos Kafka (envelope publicado)

Al crear, actualizar o eliminar un usuario se publica un evento de dominio en Apache Kafka (Aiven).
La clave del mensaje es el `userId`. El contrato del envelope es:

```json
{
  "eventId": "id unico del evento (idempotencia)",
  "eventType": "USER_CREATED | USER_UPDATED | USER_DELETED",
  "occurredAt": "2026-01-01T12:00:00",
  "aggregateId": "id del usuario",
  "payload": {
    "id": "id del usuario",
    "name": "nombre",
    "email": "correo@ejemplo.com",
    "role": "ADMIN",
    "status": "ACTIVE"
  }
}
```

El evento de eliminación (`USER_DELETED`) solo incluye `id` en el `payload`. El `email` se publica
porque el microservicio de notificaciones lo necesita para enviar los correos (**excepción
consciente** a la regla de sanitización); el `password` **nunca** se publica.

## Despliegue en Render

El archivo `render.yaml` define el servicio web, el build con Docker y el despliegue
automático de cada commit que llegue a la rama `main`.

1. Crear un Blueprint en Render y seleccionar este repositorio.
2. Completar en el panel los secretos marcados como requeridos: `DB_HOST`,
   `DB_USERNAME`, `DB_PASSWORD`, `SMTP_USERNAME`, `SMTP_PASSWORD`,
   `SMTP_FROM_ADDRESS`, `JWT_SECRET` y las variables de Kafka
   (`KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_USERNAME`, `KAFKA_PASSWORD`).
3. Usar una instancia PostgreSQL (Supabase) accesible desde Internet o desde la red privada de
   Render y ejecutar `src/main/resources/schema.sql` una vez para crear el esquema.
4. Para el certificado CA de Kafka (Aiven), crear un Secret File llamado `ca.pem` y definir
   `KAFKA_SSL_CA_LOCATION=/etc/secrets/ca.pem`. Render monta el secret como un archivo real; **no**
   se usa `KAFKA_SSL_CA_PEM` (Render no preserva los saltos de línea del PEM).
5. Desplegar el Blueprint. La API quedará disponible en el subdominio
   `onrender.com` asignado por Render y Swagger UI en `/swagger-ui.html`.

En local, exporta la ruta del archivo CA antes de arrancar:

```
$env:KAFKA_SSL_CA_LOCATION = "./ca.pem"
```

Las credenciales nunca deben guardarse en `application.properties` ni en
`render.yaml`. Para desarrollo local, deben proporcionarse como variables de entorno.
