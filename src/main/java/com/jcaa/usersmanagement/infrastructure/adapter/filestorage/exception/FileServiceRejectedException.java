package com.jcaa.usersmanagement.infrastructure.adapter.filestorage.exception;

/**
 * El file-service rechazo la peticion con un 4xx (tipo o tamaño no admitido, token invalido, etc.).
 *
 * <p>Conserva el codigo recibido para devolverlo tal cual al cliente.
 */
public final class FileServiceRejectedException extends RuntimeException {

  private static final String DEFAULT_MESSAGE = "El servicio de archivos rechazo la peticion.";

  private final int statusCode;

  private FileServiceRejectedException(final int statusCode, final String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public static FileServiceRejectedException withStatus(
      final int statusCode, final String message) {
    final String resolvedMessage =
        (message == null || message.isBlank()) ? DEFAULT_MESSAGE : message;
    return new FileServiceRejectedException(statusCode, resolvedMessage);
  }

  public int statusCode() {
    return statusCode;
  }
}
