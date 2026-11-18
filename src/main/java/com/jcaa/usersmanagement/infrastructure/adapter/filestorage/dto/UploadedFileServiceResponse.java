package com.jcaa.usersmanagement.infrastructure.adapter.filestorage.dto;

/** Respuesta del file-service al subir un archivo. */
public record UploadedFileServiceResponse(String key, String contentType, Long sizeBytes) {}
