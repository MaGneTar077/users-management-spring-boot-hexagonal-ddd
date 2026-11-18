package com.jcaa.usersmanagement.domain.exception;

public final class InvalidUserFileException extends DomainException {

  private static final String MESSAGE_BLANK_FIELD = "The user file field '%s' must not be blank.";
  private static final String MESSAGE_EMPTY_CONTENT = "The uploaded file must not be empty.";
  private static final String MESSAGE_SIZE_EXCEEDED =
      "The uploaded file exceeds the maximum allowed size of %d bytes.";
  private static final String MESSAGE_CONTENT_TYPE_NOT_ALLOWED =
      "The content type '%s' is not allowed. Allowed content types: %s.";
  private static final String MESSAGE_NEGATIVE_SIZE = "The user file size must not be negative.";

  private InvalidUserFileException(final String message) {
    super(message);
  }

  public static InvalidUserFileException becauseFieldIsBlank(final String fieldName) {
    return new InvalidUserFileException(String.format(MESSAGE_BLANK_FIELD, fieldName));
  }

  public static InvalidUserFileException becauseContentIsEmpty() {
    return new InvalidUserFileException(MESSAGE_EMPTY_CONTENT);
  }

  public static InvalidUserFileException becauseSizeExceedsLimit(final long maxSizeBytes) {
    return new InvalidUserFileException(String.format(MESSAGE_SIZE_EXCEEDED, maxSizeBytes));
  }

  public static InvalidUserFileException becauseContentTypeIsNotAllowed(
      final String contentType, final String allowedContentTypes) {
    return new InvalidUserFileException(
        String.format(MESSAGE_CONTENT_TYPE_NOT_ALLOWED, contentType, allowedContentTypes));
  }

  public static InvalidUserFileException becauseSizeIsNegative() {
    return new InvalidUserFileException(MESSAGE_NEGATIVE_SIZE);
  }
}
