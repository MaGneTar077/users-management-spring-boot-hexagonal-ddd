package com.jcaa.usersmanagement.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de configuración de la conexión a Kafka (broker, seguridad y tópicos).
 *
 * <p>Lombok no soporta {@code @ToString} sobre {@code record}, por lo que el {@code toString} se
 * define de forma explícita omitiendo {@code password} y {@code sslCaPem} para no filtrar
 * credenciales en logs.
 */
@Validated
@ConfigurationProperties(prefix = "kafka")
public record KafkaProperties(
    @NotBlank String bootstrapServers,
    @NotBlank String securityProtocol,
    @NotBlank String saslMechanism,
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank String sslCaPem,
    @NotBlank String groupId,
    Topics topics) {

  private static final String REDACTED = "***";

  /** Tópicos de Kafka donde se publican los eventos de dominio de usuarios. */
  public record Topics(String userCreated, String userUpdated, String userDeleted) {

    private static final String DEFAULT_USER_CREATED = "users.created";
    private static final String DEFAULT_USER_UPDATED = "users.updated";
    private static final String DEFAULT_USER_DELETED = "users.deleted";

    public Topics {
      userCreated = defaultIfBlank(userCreated, DEFAULT_USER_CREATED);
      userUpdated = defaultIfBlank(userUpdated, DEFAULT_USER_UPDATED);
      userDeleted = defaultIfBlank(userDeleted, DEFAULT_USER_DELETED);
    }

    private static String defaultIfBlank(final String value, final String fallback) {
      return (value == null || value.isBlank()) ? fallback : value;
    }
  }

  @Override
  public String toString() {
    return "KafkaProperties[bootstrapServers="
        + bootstrapServers
        + ", securityProtocol="
        + securityProtocol
        + ", saslMechanism="
        + saslMechanism
        + ", username="
        + username
        + ", password="
        + REDACTED
        + ", sslCaPem="
        + REDACTED
        + ", groupId="
        + groupId
        + ", topics="
        + topics
        + ']';
  }
}
