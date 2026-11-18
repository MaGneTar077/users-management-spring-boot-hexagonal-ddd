package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.GetUserFileUrlUseCase;
import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserFileUrlQuery;
import com.jcaa.usersmanagement.domain.exception.UserFileNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.policy.UserFileUploadPolicy;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetUserFileUrlService implements GetUserFileUrlUseCase {

  private final FileServicePort fileServicePort;
  private final UserFileRepositoryPort userFileRepositoryPort;
  private final Validator validator;

  @Override
  public String execute(final GetUserFileUrlQuery query) {
    validateQuery(query);

    final UserId ownerId = new UserId(query.userId());
    final RequesterContext requester = query.requester();
    UserFileUploadPolicy.validateAccess(
        ownerId, new UserId(requester.requesterId()), requester.requesterRole());

    final UserFileModel userFile = findFileOfOwnerOrFail(query.fileId(), ownerId);
    return fileServicePort.getDownloadUrl(
        userFile.fileKey(), requester.authorizationHeader());
  }

  /**
   * Un archivo que existe pero pertenece a otro usuario se reporta como "no encontrado": responder
   * 403 confirmaria su existencia a quien no deberia conocerla.
   */
  private UserFileModel findFileOfOwnerOrFail(final String fileId, final UserId ownerId) {
    return userFileRepositoryPort
        .findById(fileId)
        .filter(file -> file.belongsTo(ownerId))
        .orElseThrow(() -> UserFileNotFoundException.becauseIdWasNotFound(fileId));
  }

  private void validateQuery(final GetUserFileUrlQuery query) {
    final Set<ConstraintViolation<GetUserFileUrlQuery>> violations = validator.validate(query);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
