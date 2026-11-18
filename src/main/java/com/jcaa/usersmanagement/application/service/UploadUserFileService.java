package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.UploadUserFileUseCase;
import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.application.port.out.dto.FileUploadData;
import com.jcaa.usersmanagement.application.port.out.dto.StoredFileData;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.command.UploadUserFileCommand;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.policy.UserFileUploadPolicy;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadUserFileService implements UploadUserFileUseCase {

  private final FileServicePort fileServicePort;
  private final UserFileRepositoryPort userFileRepositoryPort;
  private final GetUserByIdPort getUserByIdPort;
  private final Validator validator;

  @Override
  public UserFileModel execute(final UploadUserFileCommand command) {
    validateCommand(command);

    final UserId ownerId = new UserId(command.userId());
    final RequesterContext requester = command.requester();
    UserFileUploadPolicy.validateAccess(
        ownerId, new UserId(requester.requesterId()), requester.requesterRole());

    ensureUserExists(ownerId);

    final FileUploadData file = command.file();
    UserFileUploadPolicy.validateUpload(file.contentType(), file.sizeBytes());

    final StoredFileData stored =
        fileServicePort.upload(ownerId.value(), file, requester.authorizationHeader());

    final UserFileModel userFile =
        UserFileModel.create(
            ownerId, stored.fileKey(), stored.contentType(), stored.sizeBytes(), Instant.now());

    final UserFileModel saved = userFileRepositoryPort.save(userFile);
    log.info("Archivo de usuario registrado. userId={} fileId={}", ownerId.value(), saved.id());
    return saved;
  }

  private void validateCommand(final UploadUserFileCommand command) {
    final Set<ConstraintViolation<UploadUserFileCommand>> violations = validator.validate(command);
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
