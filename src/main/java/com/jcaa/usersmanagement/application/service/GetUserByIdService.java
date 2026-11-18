package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.GetUserByIdUseCase;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.UserCachePort;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;
import com.jcaa.usersmanagement.application.service.dto.view.UserView;
import com.jcaa.usersmanagement.application.service.mapper.UserApplicationMapper;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * Lectura de un usuario por id, con cache delante.
 *
 * <p>Flujo: se intenta la cache; si hay valor se devuelve (sin tocar PostgreSQL); si no, se consulta
 * PostgreSQL, se guarda en cache y se devuelve. Si la cache falla, el adaptador devuelve vacio y
 * esto se comporta como si no hubiera cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetUserByIdService implements GetUserByIdUseCase {

  private final GetUserByIdPort getUserByIdPort;
  private final Validator validator;
  private final UserCachePort userCachePort;

  @Override
  public UserView execute(final GetUserByIdQuery query) {
    validateQuery(query);

    final UserId userId = UserApplicationMapper.fromGetUserByIdQueryToUserId(query);

    final Optional<UserView> cached = userCachePort.get(userId.value());
    if (cached.isPresent()) {
      log.debug("Usuario servido desde cache. userId={}", userId.value());
      return cached.get();
    }

    final UserModel user =
        getUserByIdPort
            .getById(userId)
            .orElseThrow(() -> UserNotFoundException.becauseIdWasNotFound(userId.value()));

    log.debug("Usuario leido de PostgreSQL. userId={}", userId.value());
    final UserView view = UserApplicationMapper.fromModelToView(user);
    userCachePort.put(userId.value(), view);
    return view;
  }

  private void validateQuery(final GetUserByIdQuery query) {
    final Set<ConstraintViolation<GetUserByIdQuery>> violations = validator.validate(query);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
