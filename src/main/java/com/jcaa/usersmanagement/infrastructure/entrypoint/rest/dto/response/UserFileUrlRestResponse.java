package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response;

/** URL firmada de descarga y su vigencia en segundos. */
public record UserFileUrlRestResponse(String url, int expiresInSeconds) {}
