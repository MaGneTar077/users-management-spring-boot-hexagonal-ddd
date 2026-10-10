package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class DatabaseConfigTest {

  private static final String HOST = "aws-0-us-east-1.pooler.supabase.com";
  private static final int PORT = 5432;
  private static final String DATABASE = "postgres";
  private static final String USERNAME = "postgres.example";
  private static final String PASSWORD = "secret";

  @Test
  void shouldBuildJdbcUrlWithConfiguredSslMode() {
    // Arrange
    final DatabaseConfig config =
        new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, "require");

    // Act
    final String jdbcUrl = config.buildJdbcUrl();

    // Assert
    assertThat(jdbcUrl)
        .isEqualTo("jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require");
  }

  @Test
  void shouldBuildJdbcUrlWithDisableSslMode() {
    // Arrange
    final DatabaseConfig config =
        new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, "disable");

    // Act
    final String jdbcUrl = config.buildJdbcUrl();

    // Assert
    assertThat(jdbcUrl).endsWith("?sslmode=disable");
  }

  @Test
  void shouldBuildJdbcUrlWithPreferSslMode() {
    // Arrange
    final DatabaseConfig config =
        new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, "prefer");

    // Act
    final String jdbcUrl = config.buildJdbcUrl();

    // Assert
    assertThat(jdbcUrl).endsWith("?sslmode=prefer");
  }

  @Test
  void shouldUseRequireAsDefaultWhenSslModeIsNull() {
    // Arrange
    final DatabaseConfig config =
        new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, null);

    // Act
    final String jdbcUrl = config.buildJdbcUrl();

    // Assert
    assertAll(
        () -> assertThat(config.sslMode()).isEqualTo("require"),
        () -> assertThat(jdbcUrl).endsWith("?sslmode=require"));
  }

  @Test
  void shouldUseRequireAsDefaultWhenSslModeIsBlank() {
    // Arrange
    final DatabaseConfig config =
        new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, "  ");

    // Act
    final String jdbcUrl = config.buildJdbcUrl();

    // Assert
    assertAll(
        () -> assertThat(config.sslMode()).isEqualTo("require"),
        () -> assertThat(jdbcUrl).endsWith("?sslmode=require"));
  }
}
