package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response;

/**
 * Archivo de usuario expuesto al cliente.
 *
 * <p>Se devuelve la {@code key} del objeto, no una URL: la URL firmada se pide aparte porque
 * caduca.
 */
public record UserFileRestResponse(
    String id, String key, String contentType, long sizeBytes, String createdAt) {}
