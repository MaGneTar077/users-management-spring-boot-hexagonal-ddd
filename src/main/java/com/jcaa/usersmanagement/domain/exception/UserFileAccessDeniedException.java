package com.jcaa.usersmanagement.domain.exception;

/**
 * Se lanza cuando quien hace la peticion no es el dueño de los archivos ni tiene rol ADMIN.
 *
 * <p>El mensaje es deliberadamente generico: no revela identificadores de otros usuarios.
 */
public final class UserFileAccessDeniedException extends DomainException {

  private static final String MESSAGE_NOT_OWNER_NOR_ADMIN =
      "You are not allowed to manage the files of this user.";

  private UserFileAccessDeniedException(final String message) {
    super(message);
  }

  public static UserFileAccessDeniedException becauseRequesterIsNotOwnerNorAdmin() {
    return new UserFileAccessDeniedException(MESSAGE_NOT_OWNER_NOR_ADMIN);
  }
}
