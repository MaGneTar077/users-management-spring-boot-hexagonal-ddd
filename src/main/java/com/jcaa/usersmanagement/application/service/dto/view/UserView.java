package com.jcaa.usersmanagement.application.service.dto.view;

/**
 * Proyeccion de lectura de un usuario: exactamente lo que se expone al cliente.
 *
 * <p>NO lleva contraseña ni hash, y por eso es el unico valor que se guarda en cache. Nunca se
 * cachea {@code UserModel} ni una entidad de persistencia.
 */
public record UserView(String id, String name, String email, String role, String status) {}
