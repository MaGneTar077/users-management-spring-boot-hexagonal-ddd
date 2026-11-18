package com.jcaa.usersmanagement.infrastructure.adapter.persistence.entity;

import java.time.Instant;

/** Fila de la tabla {@code user_files} tal como la devuelve el {@code ResultSet}. */
public record UserFileEntity(
    String id,
    String userId,
    String fileKey,
    String contentType,
    long sizeBytes,
    Instant createdAt) {}
