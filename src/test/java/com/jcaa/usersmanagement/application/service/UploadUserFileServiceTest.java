package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.application.port.out.dto.FileUploadData;
import com.jcaa.usersmanagement.application.port.out.dto.StoredFileData;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.command.UploadUserFileCommand;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.exception.InvalidUserFileException;
import com.jcaa.usersmanagement.domain.exception.UserFileAccessDeniedException;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.policy.UserFileUploadPolicy;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

/**
 * Tests de UploadUserFileService.
 *
 * <p>Cubre: flujo feliz, regla de dueño o ADMIN, validacion previa a la llamada remota y usuario
 * inexistente.
 */
@DisplayName("UploadUserFileService")
@ExtendWith(MockitoExtension.class)
class UploadUserFileServiceTest {

  private static final String OWNER_ID = "owner-001";
  private static final String OTHER_ID = "other-002";
  private static final String AUTH_HEADER = "Bearer token-123";
  private static final String FILE_KEY = "users/owner-001/doc.pdf";

  @Mock private FileServicePort fileServicePort;
  @Mock private UserFileRepositoryPort userFileRepositoryPort;
  @Mock private GetUserByIdPort getUserByIdPort;

  private UploadUserFileService service;

  @BeforeEach
  void setUp() {
    try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
      service =
          new UploadUserFileService(
              fileServicePort,
              userFileRepositoryPort,
              getUserByIdPort,
              validatorFactory.getValidator());
    }
  }

  // ── flujo feliz

  @Test
  @DisplayName("execute() sube el archivo y guarda la key que devuelve el file-service")
  void shouldUploadAndPersistTheReturnedKey() {
    // Arrange
    final UploadUserFileCommand command =
        commandOf(OWNER_ID, requester(OWNER_ID, UserRole.MEMBER), pdfOf(2048L));
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingOwner()));
    when(fileServicePort.upload(eq(OWNER_ID), any(FileUploadData.class), eq(AUTH_HEADER)))
        .thenReturn(new StoredFileData(FILE_KEY, UserFileUploadPolicy.CONTENT_TYPE_PDF, 2048L));
    when(userFileRepositoryPort.save(any(UserFileModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    final UserFileModel saved = service.execute(command);

    // Assert
    assertEquals(FILE_KEY, saved.fileKey());
    verify(fileServicePort).upload(eq(OWNER_ID), any(FileUploadData.class), eq(AUTH_HEADER));
    verify(userFileRepositoryPort).save(any(UserFileModel.class));
  }

  @Test
  @DisplayName("execute() permite a un ADMIN subir archivos de otro usuario")
  void shouldAllowAdminToUploadForAnotherUser() {
    // Arrange
    final UploadUserFileCommand command =
        commandOf(OWNER_ID, requester(OTHER_ID, UserRole.ADMIN), pdfOf(2048L));
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingOwner()));
    when(fileServicePort.upload(eq(OWNER_ID), any(FileUploadData.class), eq(AUTH_HEADER)))
        .thenReturn(new StoredFileData(FILE_KEY, UserFileUploadPolicy.CONTENT_TYPE_PDF, 2048L));
    when(userFileRepositoryPort.save(any(UserFileModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    final UserFileModel saved = service.execute(command);

    // Assert
    assertEquals(FILE_KEY, saved.fileKey());
  }

  // ── acceso

  @Test
  @DisplayName("execute() lanza UserFileAccessDeniedException si no es el dueño ni ADMIN")
  void shouldRejectForeignUpload() {
    // Arrange
    final UploadUserFileCommand command =
        commandOf(OWNER_ID, requester(OTHER_ID, UserRole.MEMBER), pdfOf(2048L));

    // Act & Assert
    assertThrows(UserFileAccessDeniedException.class, () -> service.execute(command));
    verifyNoInteractions(fileServicePort, userFileRepositoryPort);
  }

  // ── validacion previa a la llamada remota

  @Test
  @DisplayName("execute() no llama al file-service cuando el tipo no esta permitido")
  void shouldNotCallFileServiceWhenContentTypeIsNotAllowed() {
    // Arrange
    final FileUploadData file = new FileUploadData("virus.exe", "application/octet-stream", 10L,
        new byte[] {1});
    final UploadUserFileCommand command =
        commandOf(OWNER_ID, requester(OWNER_ID, UserRole.MEMBER), file);
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingOwner()));

    // Act & Assert
    assertThrows(InvalidUserFileException.class, () -> service.execute(command));
    verifyNoInteractions(fileServicePort);
    verify(userFileRepositoryPort, never()).save(any());
  }

  @Test
  @DisplayName("execute() no llama al file-service cuando el archivo pasa de 5 MB")
  void shouldNotCallFileServiceWhenFileIsTooLarge() {
    // Arrange
    final UploadUserFileCommand command =
        commandOf(
            OWNER_ID,
            requester(OWNER_ID, UserRole.MEMBER),
            pdfOf(UserFileUploadPolicy.MAX_SIZE_BYTES + 1L));
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingOwner()));

    // Act & Assert
    assertThrows(InvalidUserFileException.class, () -> service.execute(command));
    verifyNoInteractions(fileServicePort);
  }

  // ── usuario inexistente

  @Test
  @DisplayName("execute() lanza UserNotFoundException cuando el usuario no existe")
  void shouldThrowWhenUserDoesNotExist() {
    // Arrange
    final UploadUserFileCommand command =
        commandOf(OWNER_ID, requester(OWNER_ID, UserRole.MEMBER), pdfOf(2048L));
    when(getUserByIdPort.getById(any())).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(UserNotFoundException.class, () -> service.execute(command));
    verifyNoInteractions(fileServicePort, userFileRepositoryPort);
  }

  // ── helpers

  private static UploadUserFileCommand commandOf(
      final String userId, final RequesterContext requester, final FileUploadData file) {
    return new UploadUserFileCommand(userId, file, requester);
  }

  private static RequesterContext requester(final String requesterId, final UserRole role) {
    return new RequesterContext(requesterId, role, AUTH_HEADER);
  }

  private static FileUploadData pdfOf(final long sizeBytes) {
    return new FileUploadData(
        "doc.pdf", UserFileUploadPolicy.CONTENT_TYPE_PDF, sizeBytes, new byte[] {1, 2, 3});
  }

  private static UserModel existingOwner() {
    return new UserModel(
        new UserId(OWNER_ID),
        new UserName("Ada Lovelace"),
        new UserEmail("ada@example.com"),
        UserPassword.fromHash("$2a$12$abcdefghijklmnopqrstuO"),
        UserRole.MEMBER,
        UserStatus.ACTIVE);
  }
}
