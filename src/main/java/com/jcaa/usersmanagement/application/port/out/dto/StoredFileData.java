package com.jcaa.usersmanagement.application.port.out.dto;

/** Resultado de almacenar un archivo en el file-service: su key y sus metadatos. */
public record StoredFileData(String fileKey, String contentType, long sizeBytes) {}
