package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

public record DatabaseConfig(
        String host, int port, String databaseName, String username, String password) {
  private static final String URL_TEMPLATE =
          "jdbc:postgresql://%s:%d/%s?sslmode=require";

  public String buildJdbcUrl() {
    return String.format(URL_TEMPLATE, host, port, databaseName);
  }
}