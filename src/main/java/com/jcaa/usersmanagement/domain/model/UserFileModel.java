package com.jcaa.usersmanagement.domain.model;

import com.jcaa.usersmanagement.domain.exception.InvalidUserFileException;
import com.jcaa.usersmanagement.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Archivo asociado a un usuario.
 *
 * <p>Se guarda la {@code fileKey} del objeto en el almacenamiento remoto, nunca la URL firmada: las
 * URLs firmadas caducan y no sirven como dato persistente.
 */
public record UserFileModel(
    String id, UserId userId, String fileKey, String contentType, long sizeBytes,
    Instant createdAt) {

  private static final String FIELD_ID = "id";
  private static final String FIELD_FILE_KEY = "fileKey";
  private static final String FIELD_CONTENT_TYPE = "contentType";

  public UserFileModel {
    id = requireNotBlank(id, FIELD_ID);
    Objects.requireNonNull(userId, "UserFileModel.userId cannot be null");
    fileKey = requireNotBlank(fileKey, FIELD_FILE_KEY);
    contentType = requireNotBlank(contentType, FIELD_CONTENT_TYPE);
    if (sizeBytes < 0L) {
      throw InvalidUserFileException.becauseSizeIsNegative();
    }
    Objects.requireNonNull(createdAt, "UserFileModel.createdAt cannot be null");
  }

  /** Crea un archivo nuevo generando su identificador y la marca temporal de creacion. */
  public static UserFileModel create(
      final UserId userId,
      final String fileKey,
      final String contentType,
      final long sizeBytes,
      final Instant createdAt) {
    return new UserFileModel(
        UUID.randomUUID().toString(), userId, fileKey, contentType, sizeBytes, createdAt);
  }

  public boolean belongsTo(final UserId candidate) {
    return userId.equals(candidate);
  }

  private static String requireNotBlank(final String value, final String fieldName) {
    if (Objects.isNull(value) || value.trim().isEmpty()) {
      throw InvalidUserFileException.becauseFieldIsBlank(fieldName);
    }
    return value.trim();
  }
}
