package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.HealthRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El bean {@code BuildProperties} solo existe si {@code META-INF/build-info.properties} fue
 * generado por el goal {@code build-info} de spring-boot-maven-plugin (se ejecuta con el ciclo de
 * vida de Maven). Arrancar la app sin pasar por Maven (p. ej. desde el IDE) deja ese archivo sin
 * generar, por lo que aquí se inyecta como {@link ObjectProvider} y se usan valores por defecto si
 * no está disponible, en vez de forzar su presencia por constructor.
 */
@RestController
public class HealthRestController {

  private static final String DEFAULT_NAME = "users-management";
  private static final String DEFAULT_VERSION = "unknown";

  private final BuildProperties buildProperties;

  public HealthRestController(final ObjectProvider<BuildProperties> buildPropertiesProvider) {
    this.buildProperties = buildPropertiesProvider.getIfAvailable();
  }

  @Operation(summary = "Estado del servicio y metadatos del proyecto")
  @GetMapping("/health")
  public HealthRestResponse health() {
    if (buildProperties == null) {
      return new HealthRestResponse("UP", DEFAULT_NAME, DEFAULT_VERSION);
    }
    return new HealthRestResponse("UP", buildProperties.getName(), buildProperties.getVersion());
  }
}
