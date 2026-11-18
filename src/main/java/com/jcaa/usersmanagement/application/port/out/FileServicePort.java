package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.application.port.out.dto.FileUploadData;
import com.jcaa.usersmanagement.application.port.out.dto.StoredFileData;

/**
 * Puerto de salida hacia el file-service, que es quien habla con Backblaze.
 *
 * <p>El {@code authorizationHeader} es el header {@code Authorization} del cliente, que se reenvia
 * tal cual: el file-service valida el mismo JWT con el mismo secreto.
 */
public interface FileServicePort {

  StoredFileData upload(String userId, FileUploadData file, String authorizationHeader);

  String getDownloadUrl(String fileKey, String authorizationHeader);

  void delete(String fileKey, String authorizationHeader);

  void deleteAllOfUser(String userId, String authorizationHeader);
}
