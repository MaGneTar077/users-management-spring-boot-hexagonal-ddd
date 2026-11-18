package com.jcaa.usersmanagement.application.port.out.dto;

/**
 * Representacion neutral del archivo que llega por multipart.
 *
 * <p>Existe para que {@code application} no dependa de {@code MultipartFile} (Spring). El contenido
 * viaja en memoria porque el tamaño maximo admitido son 5 MB.
 */
public record FileUploadData(
    String originalFilename, String contentType, long sizeBytes, byte[] content) {}
