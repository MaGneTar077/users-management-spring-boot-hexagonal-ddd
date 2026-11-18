package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.DeleteUserFileUseCase;
import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserFileCommand;
import com.jcaa.usersmanagement.domain.exception.UserFileNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.policy.UserFileUploadPolicy;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteUserFileService implements DeleteUserFileUseCase {

  private final FileServicePort fileServicePort;
  private final UserFileRepositoryPort userFileRepositoryPort;
  private final Validator validator;

  @Override
  public void execute(final DeleteUserFileCommand command) {
    validateCommand(command);

    final UserId ownerId = new UserId(command.userId());
    final RequesterContext requester = command.requester();
    UserFileUploadPolicy.validateAccess(
        ownerId, new UserId(requester.requesterId()), requester.requesterRole());

    final UserFileModel userFile = findFileOfOwnerOrFail(command.fileId(), ownerId);

    // Primero el almacenamiento remoto: si falla, la fila sigue ahi y se puede reintentar.
    // Borrar la fila primero dejaria el objeto huerfano en Backblaze sin forma de localizarlo.
    fileServicePort.delete(userFile.fileKey(), requester.authorizationHeader());
    userFileRepositoryPort.deleteById(userFile.id());

    log.info("Archivo de usuario eliminado. userId={} fileId={}", ownerId.value(), userFile.id());
  }

  /** Igual que en la consulta de URL: un archivo de otro usuario se reporta como no encontrado. */
  private UserFileModel findFileOfOwnerOrFail(final String fileId, final UserId ownerId) {
    return userFileRepositoryPort
        .findById(fileId)
        .filter(file -> file.belongsTo(ownerId))
        .orElseThrow(() -> UserFileNotFoundException.becauseIdWasNotFound(fileId));
  }

  private void validateCommand(final DeleteUserFileCommand command) {
    final Set<ConstraintViolation<DeleteUserFileCommand>> violations =
        validator.validate(command);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
