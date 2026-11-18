package com.jcaa.usersmanagement.infrastructure.adapter.filestorage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/** Configuracion del file-service. La URL viene de {@code FILE_SERVICE_URL}. */
@ConfigurationProperties(prefix = "file-service")
public record FileServiceProperties(String url) {

  public boolean isConfigured() {
    return Objects.nonNull(url) && !url.isBlank();
  }
}
