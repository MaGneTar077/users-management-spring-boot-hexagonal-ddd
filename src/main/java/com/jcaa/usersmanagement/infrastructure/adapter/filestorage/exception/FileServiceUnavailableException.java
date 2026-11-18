package com.jcaa.usersmanagement.infrastructure.adapter.filestorage.exception;

/**
 * El file-service no respondio o devolvio un error propio (5xx).
 *
 * <p>Se traduce a 503 en la capa REST: el fallo no es del cliente y la gestion de usuarios sigue
 * funcionando sin archivos.
 */
public final class FileServiceUnavailableException extends RuntimeException {

  private static final String MESSAGE_NOT_CONFIGURED =
      "El servicio de archivos no esta configurado (FILE_SERVICE_URL vacio).";
  private static final String MESSAGE_UNREACHABLE =
      "El servicio de archivos no esta disponible en este momento.";
  private static final String MESSAGE_SERVER_ERROR =
      "El servicio de archivos respondio con un error (%d).";

  private FileServiceUnavailableException(final String message) {
    super(message);
  }

  private FileServiceUnavailableException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static FileServiceUnavailableException becauseItIsNotConfigured() {
    return new FileServiceUnavailableException(MESSAGE_NOT_CONFIGURED);
  }

  public static FileServiceUnavailableException becauseItIsUnreachable(final Throwable cause) {
    return new FileServiceUnavailableException(MESSAGE_UNREACHABLE, cause);
  }

  public static FileServiceUnavailableException becauseItReturnedServerError(final int statusCode) {
    return new FileServiceUnavailableException(String.format(MESSAGE_SERVER_ERROR, statusCode));
  }
}
