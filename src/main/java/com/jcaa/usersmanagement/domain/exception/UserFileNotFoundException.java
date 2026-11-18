package com.jcaa.usersmanagement.domain.exception;

public final class UserFileNotFoundException extends DomainException {

  private static final String MESSAGE_BY_ID = "The user file with id '%s' was not found.";

  private UserFileNotFoundException(final String message) {
    super(message);
  }

  public static UserFileNotFoundException becauseIdWasNotFound(final String fileId) {
    return new UserFileNotFoundException(String.format(MESSAGE_BY_ID, fileId));
  }
}
