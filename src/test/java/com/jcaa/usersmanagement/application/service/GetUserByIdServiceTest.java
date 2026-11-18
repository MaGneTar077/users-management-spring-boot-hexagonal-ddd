package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.UserCachePort;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;
import com.jcaa.usersmanagement.application.service.dto.view.UserView;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests para GetUserByIdService.
 *
 * <p>Cubre: la proyeccion devuelta, el camino de cache (hit, miss y relleno), UserNotFoundException
 * y la validación del query.
 */
@DisplayName("GetUserByIdService")
@ExtendWith(MockitoExtension.class)
class GetUserByIdServiceTest {

  private static final String USER_ID = "u-001";

  @Mock private GetUserByIdPort getUserByIdPort;
  @Mock private UserCachePort userCachePort;

  private GetUserByIdService service;

  @BeforeEach
  void setUp() {
    try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
      service =
          new GetUserByIdService(getUserByIdPort, validatorFactory.getValidator(), userCachePort);
    }
  }

  // ── flujo feliz (cache vacia)

  @Test
  @DisplayName("execute() lee de PostgreSQL y devuelve la proyeccion sin contraseña")
  void shouldReturnViewWhenFound() {
    // Arrange
    final GetUserByIdQuery query = new GetUserByIdQuery(USER_ID);
    when(userCachePort.get(USER_ID)).thenReturn(Optional.empty());
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingUser()));

    // Act
    final UserView result = service.execute(query);

    // Assert
    assertAll(
        () -> assertEquals(USER_ID, result.id()),
        () -> assertEquals("John Arrieta", result.name()),
        () -> assertEquals("john@example.com", result.email()),
        () -> assertEquals("ADMIN", result.role()),
        () -> assertEquals("ACTIVE", result.status()));
  }

  // ── cache

  @Test
  @DisplayName("execute() guarda en cache lo que acaba de leer de PostgreSQL")
  void shouldPopulateCacheOnMiss() {
    // Arrange
    final GetUserByIdQuery query = new GetUserByIdQuery(USER_ID);
    when(userCachePort.get(USER_ID)).thenReturn(Optional.empty());
    when(getUserByIdPort.getById(any())).thenReturn(Optional.of(existingUser()));

    // Act
    final UserView result = service.execute(query);

    // Assert
    verify(userCachePort).put(USER_ID, result);
  }

  @Test
  @DisplayName("execute() no consulta PostgreSQL cuando la cache tiene el usuario")
  void shouldNotHitDatabaseOnCacheHit() {
    // Arrange
    final GetUserByIdQuery query = new GetUserByIdQuery(USER_ID);
    final UserView cached =
        new UserView(USER_ID, "John Arrieta", "john@example.com", "ADMIN", "ACTIVE");
    when(userCachePort.get(USER_ID)).thenReturn(Optional.of(cached));

    // Act
    final UserView result = service.execute(query);

    // Assert
    assertSame(cached, result);
    verifyNoInteractions(getUserByIdPort);
    verify(userCachePort, never()).put(any(), any());
  }

  // ── usuario no encontrado

  @Test
  @DisplayName("execute() lanza UserNotFoundException cuando el id no existe")
  void shouldThrowWhenUserNotFound() {
    // Arrange
    final GetUserByIdQuery query = new GetUserByIdQuery("no-existe");
    when(userCachePort.get("no-existe")).thenReturn(Optional.empty());
    when(getUserByIdPort.getById(any())).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(UserNotFoundException.class, () -> service.execute(query));
    verify(userCachePort, never()).put(any(), any());
  }

  // ── validación del query

  @Test
  @DisplayName("execute() lanza ConstraintViolationException cuando el id está en blanco")
  void shouldThrowWhenQueryIsInvalid() {
    // Arrange
    final GetUserByIdQuery query = new GetUserByIdQuery("");

    // Act & Assert
    assertThrows(ConstraintViolationException.class, () -> service.execute(query));
    verifyNoInteractions(getUserByIdPort, userCachePort);
  }

  private static UserModel existingUser() {
    return new UserModel(
        new UserId(USER_ID),
        new UserName("John Arrieta"),
        new UserEmail("john@example.com"),
        UserPassword.fromHash("$2a$12$abcdefghijklmnopqrstuO"),
        UserRole.ADMIN,
        UserStatus.ACTIVE);
  }
}
