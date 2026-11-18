package com.jcaa.usersmanagement.application.service.dto;

import com.jcaa.usersmanagement.domain.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Quien hace la peticion: su id y rol (sacados del JWT) y el header {@code Authorization} que hay
 * que reenviar al file-service.
 */
public record RequesterContext(
    @NotBlank(message = "requesterId must not be blank") String requesterId,
    @NotNull(message = "requesterRole must not be null") UserRole requesterRole,
    @NotBlank(message = "authorizationHeader must not be blank") String authorizationHeader) {}
