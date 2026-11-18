package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.DeleteUserUseCase;
import com.jcaa.usersmanagement.application.port.out.DeleteUserPort;
import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.UserCachePort;
import com.jcaa.usersmanagement.application.port.out.UserEventPublisherPort;
import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserCommand;
import com.jcaa.usersmanagement.application.service.mapper.UserApplicationMapper;
import com.jcaa.usersmanagement.domain.event.UserDeletedDomainEvent;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteUserService implements DeleteUserUseCase {

  private final DeleteUserPort deleteUserPort;
  private final GetUserByIdPort getUserByIdPort;
  private final Validator validator;
  private final UserEventPublisherPort userEventPublisherPort;
  private final FileServicePort fileServicePort;
  private final UserCachePort userCachePort;

  @Override
  public void execute(final DeleteUserCommand command) {
    validateCommand(command);

    final UserId userId = UserApplicationMapper.fromDeleteCommandToUserId(command);
    ensureUserExists(userId);
    deleteUserPort.delete(userId);

    userCachePort.evict(userId.value());
    deleteRemoteFilesSafely(userId, command.authorizationHeader());
    publishUserDeletedSafely(userId);
  }

  /**
   * La tabla {@code user_files} se vacia sola por {@code ON DELETE CASCADE}; esto borra los objetos
   * en el almacenamiento remoto. Si el file-service falla NO se revierte el borrado del usuario: se
   * registra el error y se continua, quedando objetos huerfanos pendientes de limpieza.
   */
  private void deleteRemoteFilesSafely(final UserId userId, final String authorizationHeader) {
    if (Objects.isNull(authorizationHeader) || authorizationHeader.isBlank()) {
      log.warn(
          "Sin header Authorization: no se solicita al file-service borrar los archivos. userId={}",
          userId.value());
      return;
    }
    try {
      fileServicePort.deleteAllOfUser(userId.value(), authorizationHeader);
    } catch (final RuntimeException ex) {
      log.error(
          "No se pudieron borrar los archivos del usuario en el file-service. userId={}",
          userId.value(),
          ex);
      // TODO Limpieza diferida: los objetos quedan huerfanos en Backblaze. Pendiente para
      // el informe (reconciliacion por lote o reintento con cola).
    }
  }

  private void publishUserDeletedSafely(final UserId userId) {
    try {
      userEventPublisherPort.publishUserDeleted(new UserDeletedDomainEvent(userId));
    } catch (RuntimeException ex) {
      log.warn(
          "No se pudo publicar el evento de eliminacion de usuario. userId={}", userId.value(), ex);
      // TODO Transactional Outbox: si el publish falla tras el delete, hoy se pierde
      // el evento. Migrar a Outbox en iteración posterior.
    }
  }

  private void validateCommand(final DeleteUserCommand command) {
    final Set<ConstraintViolation<DeleteUserCommand>> violations = validator.validate(command);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  private void ensureUserExists(final UserId userId) {
    getUserByIdPort
        .getById(userId)
        .orElseThrow(() -> UserNotFoundException.becauseIdWasNotFound(userId.value()));
  }
}
