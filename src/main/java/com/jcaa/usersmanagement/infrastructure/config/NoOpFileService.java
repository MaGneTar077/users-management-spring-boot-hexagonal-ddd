package com.jcaa.usersmanagement.infrastructure.config;

import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.dto.FileUploadData;
import com.jcaa.usersmanagement.application.port.out.dto.StoredFileData;

/**
 * Implementacion vacia de {@link FileServicePort} para la CLI inactiva, que no gestiona archivos.
 *
 * <p>No es un bean de Spring: solo lo usa {@link DependencyContainer}.
 */
final class NoOpFileService implements FileServicePort {

  private static final String MESSAGE_UNSUPPORTED =
      "La CLI no gestiona archivos de usuario.";

  @Override
  public StoredFileData upload(
      final String userId, final FileUploadData file, final String authorizationHeader) {
    throw new UnsupportedOperationException(MESSAGE_UNSUPPORTED);
  }

  @Override
  public String getDownloadUrl(final String fileKey, final String authorizationHeader) {
    throw new UnsupportedOperationException(MESSAGE_UNSUPPORTED);
  }

  @Override
  public void delete(final String fileKey, final String authorizationHeader) {
    throw new UnsupportedOperationException(MESSAGE_UNSUPPORTED);
  }

  @Override
  public void deleteAllOfUser(final String userId, final String authorizationHeader) {
    // Sin archivos que borrar: la CLI nunca sube ninguno.
  }
}
