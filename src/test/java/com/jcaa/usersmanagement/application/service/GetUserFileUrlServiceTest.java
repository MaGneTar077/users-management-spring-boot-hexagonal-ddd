package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserFileUrlQuery;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

/**
 * Tests de GetUserFileUrlService.
 *
 * <p>El caso interesante es el archivo que existe pero pertenece a otro usuario: debe responder
 * "no encontrado", no 403, para no confirmar su existencia.
 */
@DisplayName("GetUserFileUrlService")
@ExtendWith(MockitoExtension.class)
class GetUserFileUrlServiceTest {

  private static final String OWNER_ID = "owner-001";
  private static final String OTHER_ID = "other-002";
  private static final String FILE_ID = "file-001";
  private static final String FILE_KEY = "users/owner-001/doc.pdf";
  private static final String AUTH_HEADER = "Bearer token-123";
  private static final String SIGNED_URL = "https://backblaze.example/doc.pdf?sig=abc";

  @Mock private FileServicePort fileServicePort;
  @Mock private UserFileRepositoryPort userFileRepositoryPort;

  private GetUserFileUrlService service;

  @BeforeEach
  void setUp() {
    try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
      service =
          new GetUserFileUrlService(
              fileServicePort, userFileRepositoryPort, validatorFactory.getValidator());
    }
  }

  @Test
  @DisplayName("execute() devuelve la URL firmada que entrega el file-service")
  void shouldReturnSignedUrl() {
    // Arrange
    final GetUserFileUrlQuery query = queryOf(OWNER_ID, OWNER_ID, UserRole.MEMBER);
    when(userFileRepositoryPort.findById(FILE_ID))
        .thenReturn(Optional.of(fileOf(new UserId(OWNER_ID))));
    when(fileServicePort.getDownloadUrl(FILE_KEY, AUTH_HEADER)).thenReturn(SIGNED_URL);

    // Act
    final String url = service.execute(query);

    // Assert
    assertEquals(SIGNED_URL, url);
    verify(fileServicePort).getDownloadUrl(FILE_KEY, AUTH_HEADER);
  }

  @Test
  @DisplayName("execute() lanza UserFileAccessDeniedException si no es el dueño ni ADMIN")
  void shouldRejectForeignRequester() {
    // Arrange
    final GetUserFileUrlQuery query = queryOf(OWNER_ID, OTHER_ID, UserRole.MEMBER);

    // Act & Assert
    assertThrows(UserFileAccessDeniedException.class, () -> service.execute(query));
    verifyNoInteractions(userFileRepositoryPort, fileServicePort);
  }

  @Test
  @DisplayName("execute() reporta no encontrado cuando el archivo pertenece a otro usuario")
  void shouldReportNotFoundWhenFileBelongsToAnotherUser() {
    // Arrange
    final GetUserFileUrlQuery query = queryOf(OWNER_ID, OWNER_ID, UserRole.MEMBER);
    when(userFileRepositoryPort.findById(FILE_ID))
        .thenReturn(Optional.of(fileOf(new UserId(OTHER_ID))));

    // Act & Assert
    assertThrows(UserFileNotFoundException.class, () -> service.execute(query));
    verifyNoInteractions(fileServicePort);
  }

  @Test
  @DisplayName("execute() lanza UserFileNotFoundException cuando el archivo no existe")
  void shouldThrowWhenFileDoesNotExist() {
    // Arrange
    final GetUserFileUrlQuery query = queryOf(OWNER_ID, OWNER_ID, UserRole.MEMBER);
    when(userFileRepositoryPort.findById(FILE_ID)).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(UserFileNotFoundException.class, () -> service.execute(query));
    verifyNoInteractions(fileServicePort);
  }

  private static GetUserFileUrlQuery queryOf(
      final String userId, final String requesterId, final UserRole role) {
    return new GetUserFileUrlQuery(
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
