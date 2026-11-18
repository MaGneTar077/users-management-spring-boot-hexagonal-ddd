package com.jcaa.usersmanagement.domain.policy;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.exception.InvalidUserFileException;
import com.jcaa.usersmanagement.domain.exception.UserFileAccessDeniedException;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests de las reglas de subida y de acceso a los archivos de usuario. */
@DisplayName("UserFileUploadPolicy")
class UserFileUploadPolicyTest {

  private static final String OWNER_ID = "owner-001";
  private static final String OTHER_ID = "other-002";

  // ── validacion de la subida

  @Test
  @DisplayName("validateUpload() acepta los tres tipos permitidos dentro del limite")
  void shouldAcceptAllowedContentTypes() {
    // Arrange
    final long size = 1024L;

    // Act & Assert
    assertAll(
        () ->
            assertDoesNotThrow(
                () -> UserFileUploadPolicy.validateUpload("application/pdf", size)),
        () -> assertDoesNotThrow(() -> UserFileUploadPolicy.validateUpload("image/png", size)),
        () -> assertDoesNotThrow(() -> UserFileUploadPolicy.validateUpload("image/jpeg", size)),
        () ->
            assertDoesNotThrow(
                () -> UserFileUploadPolicy.validateUpload("APPLICATION/PDF; charset=UTF-8", size)));
  }

  @Test
  @DisplayName("validateUpload() rechaza archivo vacio, demasiado grande o de tipo no admitido")
  void shouldRejectInvalidUploads() {
    // Act & Assert
    assertAll(
        () ->
            assertThrows(
                InvalidUserFileException.class,
                () -> UserFileUploadPolicy.validateUpload("application/pdf", 0L)),
        () ->
            assertThrows(
                InvalidUserFileException.class,
                () ->
                    UserFileUploadPolicy.validateUpload(
                        "application/pdf", UserFileUploadPolicy.MAX_SIZE_BYTES + 1L)),
        () ->
            assertThrows(
                InvalidUserFileException.class,
                () -> UserFileUploadPolicy.validateUpload("text/plain", 1024L)),
        () ->
            assertThrows(
                InvalidUserFileException.class,
                () -> UserFileUploadPolicy.validateUpload(null, 1024L)));
  }

  @Test
  @DisplayName("validateUpload() acepta exactamente el limite de 5 MB")
  void shouldAcceptExactlyTheLimit() {
    // Act & Assert
    assertDoesNotThrow(
        () ->
            UserFileUploadPolicy.validateUpload(
                "application/pdf", UserFileUploadPolicy.MAX_SIZE_BYTES));
  }

  // ── validacion del acceso

  @Test
  @DisplayName("validateAccess() permite al dueño y a cualquier ADMIN")
  void shouldAllowOwnerAndAdmin() {
    // Arrange
    final UserId owner = new UserId(OWNER_ID);

    // Act & Assert
    assertAll(
        () ->
            assertDoesNotThrow(
                () -> UserFileUploadPolicy.validateAccess(owner, owner, UserRole.MEMBER)),
        () ->
            assertDoesNotThrow(
                () ->
                    UserFileUploadPolicy.validateAccess(
                        owner, new UserId(OTHER_ID), UserRole.ADMIN)));
  }

  @Test
  @DisplayName("validateAccess() rechaza a un tercero que no es ADMIN, incluso si es REVIEWER")
  void shouldRejectNonOwnerNonAdmin() {
    // Arrange
    final UserId owner = new UserId(OWNER_ID);
    final UserId other = new UserId(OTHER_ID);

    // Act & Assert
    assertAll(
        () ->
            assertThrows(
                UserFileAccessDeniedException.class,
                () -> UserFileUploadPolicy.validateAccess(owner, other, UserRole.MEMBER)),
        () ->
            assertThrows(
                UserFileAccessDeniedException.class,
                () -> UserFileUploadPolicy.validateAccess(owner, other, UserRole.REVIEWER)));
  }
}
