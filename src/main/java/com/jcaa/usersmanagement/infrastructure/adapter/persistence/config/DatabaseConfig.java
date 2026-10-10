package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

import java.util.Objects;

/**
 * Configuración de conexión a PostgreSQL.
 *
 * <p>Construye la URL JDBC con el modo SSL indicado. Si {@code sslMode} es nulo o
 * vacío se usa {@code "require"} como valor por defecto (Supabase exige SSL).
 */
public record DatabaseConfig(
        String host,
        int port,
        String databaseName,
        String username,
        String password,
        String sslMode) {

  private static final String URL_TEMPLATE = "jdbc:postgresql://%s:%d/%s?sslmode=%s";
  private static final String DEFAULT_SSL_MODE = "require";

  public DatabaseConfig {
    if (Objects.isNull(sslMode) || sslMode.isBlank()) {
      sslMode = DEFAULT_SSL_MODE;
    }
  }

  public String buildJdbcUrl() {
    return String.format(URL_TEMPLATE, host, port, databaseName, sslMode);
  }
}
