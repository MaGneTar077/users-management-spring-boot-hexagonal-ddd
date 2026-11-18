package com.jcaa.usersmanagement.domain.policy;

import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.exception.InvalidUserFileException;
import com.jcaa.usersmanagement.domain.exception.UserFileAccessDeniedException;
import com.jcaa.usersmanagement.domain.valueobject.UserId;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Reglas de negocio de los archivos de usuario: que se puede subir y quien puede manipularlo.
 *
 * <p>La validacion ocurre en users-management ANTES de llamar al file-service, para no gastar una
 * llamada remota en un archivo que de todos modos se rechazaria.
 */
public final class UserFileUploadPolicy {

  public static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;

  public static final String CONTENT_TYPE_PDF = "application/pdf";
  public static final String CONTENT_TYPE_PNG = "image/png";
  public static final String CONTENT_TYPE_JPEG = "image/jpeg";

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of(CONTENT_TYPE_PDF, CONTENT_TYPE_PNG, CONTENT_TYPE_JPEG);

  private static final String ALLOWED_CONTENT_TYPES_DESCRIPTION =
      CONTENT_TYPE_PDF + ", " + CONTENT_TYPE_PNG + ", " + CONTENT_TYPE_JPEG;

  private UserFileUploadPolicy() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** Valida tamaño y tipo de contenido del archivo a subir. */
  public static void validateUpload(final String contentType, final long sizeBytes) {
    if (sizeBytes <= 0L) {
      throw InvalidUserFileException.becauseContentIsEmpty();
    }
    if (sizeBytes > MAX_SIZE_BYTES) {
      throw InvalidUserFileException.becauseSizeExceedsLimit(MAX_SIZE_BYTES);
    }
    validateContentType(contentType);
  }

  /** Solo el dueño de los archivos o un ADMIN pueden operar sobre ellos. */
  public static void validateAccess(
      final UserId ownerId, final UserId requesterId, final UserRole requesterRole) {
    if (UserRole.ADMIN == requesterRole) {
      return;
    }
    if (!ownerId.equals(requesterId)) {
      throw UserFileAccessDeniedException.becauseRequesterIsNotOwnerNorAdmin();
    }
  }

  private static void validateContentType(final String contentType) {
    final String normalizedContentType =
        Objects.isNull(contentType) ? "" : stripParameters(contentType);
    if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
      throw InvalidUserFileException.becauseContentTypeIsNotAllowed(
          normalizedContentType, ALLOWED_CONTENT_TYPES_DESCRIPTION);
    }
  }

  /** Descarta parametros como "; charset=UTF-8" y normaliza a minusculas. */
  private static String stripParameters(final String contentType) {
    final int separatorIndex = contentType.indexOf(';');
    final String mediaType =
        separatorIndex < 0 ? contentType : contentType.substring(0, separatorIndex);
    return mediaType.trim().toLowerCase(Locale.ROOT);
  }
}
