package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.query.ListUserFilesQuery;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.exception.UserFileAccessDeniedException;
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
import java.util.List;

/** Tests de ListUserFilesService. */
@DisplayName("ListUserFilesService")
@ExtendWith(MockitoExtension.class)
class ListUserFilesServiceTest {

  private static final String OWNER_ID = "owner-001";
  private static final String OTHER_ID = "other-002";
  private static final String AUTH_HEADER = "Bearer token-123";

  @Mock private UserFileRepositoryPort userFileRepositoryPort;

  private ListUserFilesService service;

  @BeforeEach
  void setUp() {
    try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
      service = new ListUserFilesService(userFileRepositoryPort, validatorFactory.getValidator());
    }
  }

  @Test
  @DisplayName("execute() devuelve los archivos del usuario cuando lo pide su dueño")
  void shouldReturnOwnFiles() {
    // Arrange
    final ListUserFilesQuery query = queryOf(OWNER_ID, OWNER_ID, UserRole.MEMBER);
    when(userFileRepositoryPort.findByUserId(new UserId(OWNER_ID)))
        .thenReturn(List.of(fileOf("file-001")));

    // Act
    final List<UserFileModel> files = service.execute(query);

    // Assert
    assertEquals(1, files.size());
  }

  @Test
  @DisplayName("execute() lanza UserFileAccessDeniedException si no es el dueño ni ADMIN")
  void shouldRejectForeignRequester() {
    // Arrange
    final ListUserFilesQuery query = queryOf(OWNER_ID, OTHER_ID, UserRole.REVIEWER);

    // Act & Assert
    assertThrows(UserFileAccessDeniedException.class, () -> service.execute(query));
    verifyNoInteractions(userFileRepositoryPort);
  }

  private static ListUserFilesQuery queryOf(
      final String userId, final String requesterId, final UserRole role) {
    return new ListUserFilesQuery(userId, new RequesterContext(requesterId, role, AUTH_HEADER));
  }

  private static UserFileModel fileOf(final String fileId) {
    return new UserFileModel(
        fileId,
        new UserId(OWNER_ID),
        "users/owner-001/doc.pdf",
        UserFileUploadPolicy.CONTENT_TYPE_PDF,
        2048L,
        Instant.parse("2026-11-17T10:00:00Z"));
  }
}
