package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jcaa.usersmanagement.application.port.out.DeleteUserPort;
import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.UserCachePort;
import com.jcaa.usersmanagement.application.port.out.UserEventPublisherPort;
import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserCommand;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.event.UserDeletedDomainEvent;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests para DeleteUserService.
 *
 * <p>Cubre: flujo feliz (delete invocado), usuario no encontrado y validación del command.
 */
@DisplayName("DeleteUserService")
@ExtendWith(MockitoExtension.class)
class DeleteUserServiceTest {

  @Mock private DeleteUserPort deleteUserPort;
  @Mock private GetUserByIdPort getUserByIdPort;
  @Mock private UserEventPublisherPort userEventPublisherPort;
  @Mock private FileServicePort fileServicePort;
  @Mock private UserCachePort userCachePort;

  private DeleteUserService service;

  @BeforeEach
  void setUp() {
    try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
      service =
          new DeleteUserService(
              deleteUserPort,
              getUserByIdPort,
              validatorFactory.getValidator(),
              userEventPublisherPort,
              fileServicePort,
              userCachePort);
    }
  }

  // ── flujo feliz

  @Test
  @DisplayName("execute() invoca deleteUserPort cuando el usuario existe")
  void shouldDeleteWhenUserExists() {
    // Arrange
    final DeleteUserCommand command = new DeleteUserCommand("u-001");

    final UserModel existing =
        new UserModel(
            new UserId("u-001"),
            new UserName("John Arrieta"),
            new UserEmail("john@example.com"),
            UserPassword.fromHash("$2a$12$abcdefghijklmnopqrstuO"),
            UserRole.ADMIN,
            UserStatus.ACTIVE);

    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existing));

    // Act
    service.execute(command);

    // Assert
    verify(deleteUserPort).delete(any(UserId.class));
    verify(userEventPublisherPort).publishUserDeleted(any(UserDeletedDomainEvent.class));

    final InOrder inOrder = inOrder(deleteUserPort, userEventPublisherPort);
    inOrder.verify(deleteUserPort).delete(any(UserId.class));
    inOrder.verify(userEventPublisherPort).publishUserDeleted(any(UserDeletedDomainEvent.class));
  }

  // ── limpieza de archivos en el file-service

  @Test
  @DisplayName("execute() pide al file-service borrar los archivos despues de borrar el usuario")
  void shouldDeleteRemoteFilesAfterDeletingUser() {
    // Arrange
    final DeleteUserCommand command = new DeleteUserCommand("u-001", "Bearer token-123");
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingUser()));

    // Act
    service.execute(command);

    // Assert
    final InOrder inOrder = inOrder(deleteUserPort, fileServicePort);
    inOrder.verify(deleteUserPort).delete(any(UserId.class));
    inOrder.verify(fileServicePort).deleteAllOfUser("u-001", "Bearer token-123");
  }

  @Test
  @DisplayName("execute() no revierte el borrado cuando el file-service falla")
  void shouldNotRollbackWhenFileServiceFails() {
    // Arrange
    final DeleteUserCommand command = new DeleteUserCommand("u-001", "Bearer token-123");
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingUser()));
    doThrow(new IllegalStateException("file-service caido"))
        .when(fileServicePort)
        .deleteAllOfUser(any(), any());

    // Act
    service.execute(command);

    // Assert
    verify(deleteUserPort).delete(any(UserId.class));
    verify(userEventPublisherPort).publishUserDeleted(any(UserDeletedDomainEvent.class));
  }

  @Test
  @DisplayName("execute() no llama al file-service cuando no hay header Authorization")
  void shouldSkipRemoteCleanupWithoutAuthorizationHeader() {
    // Arrange
    final DeleteUserCommand command = new DeleteUserCommand("u-001");
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingUser()));

    // Act
    service.execute(command);

    // Assert
    verify(deleteUserPort).delete(any(UserId.class));
    verifyNoInteractions(fileServicePort);
  }

  // ── usuario no encontrado

  @Test
  @DisplayName("execute() lanza UserNotFoundException cuando el id no existe")
  void shouldThrowWhenUserNotFound() {
    // Arrange
    final DeleteUserCommand command = new DeleteUserCommand("no-existe");

    when(getUserByIdPort.getById(any())).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(UserNotFoundException.class, () -> service.execute(command));
    verify(deleteUserPort, never()).delete(any());
    verify(userEventPublisherPort, never()).publishUserDeleted(any(UserDeletedDomainEvent.class));
  }

  // ── validación del command

  @Test
  @DisplayName("execute() lanza ConstraintViolationException cuando el id está en blanco")
  void shouldThrowWhenCommandIsInvalid() {
    // Arrange
    final DeleteUserCommand command = new DeleteUserCommand("  ");

    // Act & Assert
    assertThrows(ConstraintViolationException.class, () -> service.execute(command));
    verifyNoInteractions(
        deleteUserPort, getUserByIdPort, userEventPublisherPort, fileServicePort, userCachePort);
  }

  @Test
  @DisplayName("execute() invalida la cache del usuario tras borrarlo")
  void shouldEvictCacheAfterDeletingUser() {
    // Arrange
    final DeleteUserCommand command = new DeleteUserCommand("u-001");
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingUser()));

    // Act
    service.execute(command);

    // Assert
    final InOrder inOrder = inOrder(deleteUserPort, userCachePort);
    inOrder.verify(deleteUserPort).delete(any(UserId.class));
    inOrder.verify(userCachePort).evict("u-001");
  }

  private static UserModel existingUser() {
    return new UserModel(
        new UserId("u-001"),
        new UserName("John Arrieta"),
        new UserEmail("john@example.com"),
        UserPassword.fromHash("$2a$12$abcdefghijklmnopqrstuO"),
        UserRole.ADMIN,
        UserStatus.ACTIVE);
  }
}
