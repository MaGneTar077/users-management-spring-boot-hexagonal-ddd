package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserFileCommand;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.exception.UserFileAccessDeniedException;
import com.jcaa.usersmanagement.domain.exception.UserFileNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.policy.UserFileUploadPolicy;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

/** Tests de DeleteUserFileService. */
@DisplayName("DeleteUserFileService")
@ExtendWith(MockitoExtension.class)
class DeleteUserFileServiceTest {

  private static final String OWNER_ID = "owner-001";
  private static final String OTHER_ID = "other-002";
  private static final String FILE_ID = "file-001";
  private static final String FILE_KEY = "users/owner-001/doc.pdf";
  private static final String AUTH_HEADER = "Bearer token-123";

  @Mock private FileServicePort fileServicePort;
  @Mock private UserFileRepositoryPort userFileRepositoryPort;

  private DeleteUserFileService service;

  @BeforeEach
  void setUp() {
    try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
      service =
          new DeleteUserFileService(
              fileServicePort, userFileRepositoryPort, validatorFactory.getValidator());
    }
  }

  @Test
  @DisplayName("execute() borra primero en el file-service y despues la fila")
  void shouldDeleteRemoteBeforeRow() {
    // Arrange
    final DeleteUserFileCommand command = commandOf(OWNER_ID, OWNER_ID, UserRole.MEMBER);
    when(userFileRepositoryPort.findById(FILE_ID))
        .thenReturn(Optional.of(fileOf(new UserId(OWNER_ID))));

    // Act
    service.execute(command);

    // Assert
    final InOrder inOrder = inOrder(fileServicePort, userFileRepositoryPort);
    inOrder.verify(fileServicePort).delete(FILE_KEY, AUTH_HEADER);
    inOrder.verify(userFileRepositoryPort).deleteById(FILE_ID);
  }

  @Test
  @DisplayName("execute() conserva la fila cuando el file-service falla")
  void shouldKeepRowWhenFileServiceFails() {
    // Arrange
    final DeleteUserFileCommand command = commandOf(OWNER_ID, OWNER_ID, UserRole.MEMBER);
    when(userFileRepositoryPort.findById(FILE_ID))
        .thenReturn(Optional.of(fileOf(new UserId(OWNER_ID))));
    doThrow(new IllegalStateException("file-service caido"))
        .when(fileServicePort)
        .delete(any(), any());

    // Act & Assert
    assertThrows(IllegalStateException.class, () -> service.execute(command));
    verify(userFileRepositoryPort, never()).deleteById(any());
  }

  @Test
  @DisplayName("execute() permite a un ADMIN borrar archivos de otro usuario")
  void shouldAllowAdmin() {
    // Arrange
    final DeleteUserFileCommand command = commandOf(OWNER_ID, OTHER_ID, UserRole.ADMIN);
    when(userFileRepositoryPort.findById(FILE_ID))
        .thenReturn(Optional.of(fileOf(new UserId(OWNER_ID))));

    // Act
    service.execute(command);

    // Assert
    verify(userFileRepositoryPort).deleteById(FILE_ID);
  }

  @Test
  @DisplayName("execute() lanza UserFileAccessDeniedException si no es el dueño ni ADMIN")
  void shouldRejectForeignRequester() {
    // Arrange
    final DeleteUserFileCommand command = commandOf(OWNER_ID, OTHER_ID, UserRole.MEMBER);

    // Act & Assert
    assertThrows(UserFileAccessDeniedException.class, () -> service.execute(command));
    verifyNoInteractions(userFileRepositoryPort, fileServicePort);
  }

  @Test
  @DisplayName("execute() reporta no encontrado cuando el archivo pertenece a otro usuario")
  void shouldReportNotFoundWhenFileBelongsToAnotherUser() {
    // Arrange
    final DeleteUserFileCommand command = commandOf(OWNER_ID, OWNER_ID, UserRole.MEMBER);
    when(userFileRepositoryPort.findById(FILE_ID))
        .thenReturn(Optional.of(fileOf(new UserId(OTHER_ID))));

    // Act & Assert
    assertThrows(UserFileNotFoundException.class, () -> service.execute(command));
    verifyNoInteractions(fileServicePort);
  }

  private static DeleteUserFileCommand commandOf(
      final String userId, final String requesterId, final UserRole role) {
    return new DeleteUserFileCommand(
        userId, FILE_ID, new RequesterContext(requesterId, role, AUTH_HEADER));
  }

  private static UserFileModel fileOf(final UserId ownerId) {
    return new UserFileModel(
        FILE_ID,
        ownerId,
        FILE_KEY,
        UserFileUploadPolicy.CONTENT_TYPE_PDF,
        2048L,
        Instant.parse("2026-11-17T10:00:00Z"));
  }
}
