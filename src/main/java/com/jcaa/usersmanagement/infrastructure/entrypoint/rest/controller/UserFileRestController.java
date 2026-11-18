package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import com.jcaa.usersmanagement.application.port.in.DeleteUserFileUseCase;
import com.jcaa.usersmanagement.application.port.in.GetUserFileUrlUseCase;
import com.jcaa.usersmanagement.application.port.in.ListUserFilesUseCase;
import com.jcaa.usersmanagement.application.port.in.UploadUserFileUseCase;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserFileCommand;
import com.jcaa.usersmanagement.application.service.dto.command.UploadUserFileCommand;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserFileUrlQuery;
import com.jcaa.usersmanagement.application.service.dto.query.ListUserFilesQuery;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserFileRestResponse;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserFileUrlRestResponse;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.mapper.UserFileRestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Rutas de archivos de usuario. users-management es el unico punto de entrada del cliente.
 *
 * <p>El header {@code Authorization} se recibe y se reenvia al file-service tal cual; la regla de
 * "dueño o ADMIN" vive en los casos de uso, no aqui.
 */
@RestController
@RequestMapping("/api/users/{userId}/files")
@RequiredArgsConstructor
public class UserFileRestController implements UserFileRestControllerDocs {

  /** Debe coincidir con la vigencia que el adaptador pide al file-service. */
  private static final int DOWNLOAD_URL_TTL_SECONDS = 300;

  private final UploadUserFileUseCase uploadUserFileUseCase;
  private final ListUserFilesUseCase listUserFilesUseCase;
  private final GetUserFileUrlUseCase getUserFileUrlUseCase;
  private final DeleteUserFileUseCase deleteUserFileUseCase;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Override
  public UserFileRestResponse upload(
      @PathVariable final String userId,
      @RequestPart("file") final MultipartFile file,
      final Authentication authentication,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authorizationHeader) {
    final UploadUserFileCommand command =
        new UploadUserFileCommand(
            userId,
            UserFileRestMapper.toFileUploadData(file),
            requesterOf(authentication, authorizationHeader));
    final UserFileModel saved = uploadUserFileUseCase.execute(command);
    return UserFileRestMapper.toResponse(saved);
  }

  @GetMapping
  @Override
  public List<UserFileRestResponse> list(
      @PathVariable final String userId,
      final Authentication authentication,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authorizationHeader) {
    final ListUserFilesQuery query =
        new ListUserFilesQuery(userId, requesterOf(authentication, authorizationHeader));
    return UserFileRestMapper.toResponseList(listUserFilesUseCase.execute(query));
  }

  @GetMapping("/{fileId}")
  @Override
  public UserFileUrlRestResponse getDownloadUrl(
      @PathVariable final String userId,
      @PathVariable final String fileId,
      final Authentication authentication,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authorizationHeader) {
    final GetUserFileUrlQuery query =
        new GetUserFileUrlQuery(userId, fileId, requesterOf(authentication, authorizationHeader));
    return new UserFileUrlRestResponse(
        getUserFileUrlUseCase.execute(query), DOWNLOAD_URL_TTL_SECONDS);
  }

  @DeleteMapping("/{fileId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Override
  public void delete(
      @PathVariable final String userId,
      @PathVariable final String fileId,
      final Authentication authentication,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authorizationHeader) {
    final DeleteUserFileCommand command =
        new DeleteUserFileCommand(userId, fileId, requesterOf(authentication, authorizationHeader));
    deleteUserFileUseCase.execute(command);
  }

  private static RequesterContext requesterOf(
      final Authentication authentication, final String authorizationHeader) {
    return UserFileRestMapper.toRequesterContext(authentication, authorizationHeader);
  }
}
