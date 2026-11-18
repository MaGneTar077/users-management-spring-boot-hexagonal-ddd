package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.ListUserFilesUseCase;
import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.query.ListUserFilesQuery;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.policy.UserFileUploadPolicy;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListUserFilesService implements ListUserFilesUseCase {

  private final UserFileRepositoryPort userFileRepositoryPort;
  private final Validator validator;

  @Override
  public List<UserFileModel> execute(final ListUserFilesQuery query) {
    validateQuery(query);

    final UserId ownerId = new UserId(query.userId());
    final RequesterContext requester = query.requester();
    UserFileUploadPolicy.validateAccess(
        ownerId, new UserId(requester.requesterId()), requester.requesterRole());

    return userFileRepositoryPort.findByUserId(ownerId);
  }

  private void validateQuery(final ListUserFilesQuery query) {
    final Set<ConstraintViolation<ListUserFilesQuery>> violations = validator.validate(query);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
