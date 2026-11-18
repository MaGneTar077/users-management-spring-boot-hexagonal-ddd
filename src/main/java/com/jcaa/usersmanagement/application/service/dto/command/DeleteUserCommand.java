package com.jcaa.usersmanagement.application.service.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * Borrado de usuario.
 *
 * <p>El {@code authorizationHeader} se arrastra hasta aqui porque tras borrar al usuario hay que
 * pedirle al file-service que borre sus archivos, y ese servicio valida el mismo JWT. Puede venir
 * nulo en adaptadores sin HTTP (la CLI inactiva): en ese caso no se intenta la limpieza remota.
 */
public record DeleteUserCommand(
    @NotBlank(message = "id must not be blank") String id, String authorizationHeader) {

  public DeleteUserCommand(final String id) {
    this(id, null);
  }
}
