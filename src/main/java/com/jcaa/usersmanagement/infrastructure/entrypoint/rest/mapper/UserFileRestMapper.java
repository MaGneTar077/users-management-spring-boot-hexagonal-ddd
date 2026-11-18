package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.mapper;

import com.jcaa.usersmanagement.application.port.out.dto.FileUploadData;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserFileRestResponse;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

@UtilityClass
public class UserFileRestMapper {

  private static final String ROLE_PREFIX = "ROLE_";

  public FileUploadData toFileUploadData(final MultipartFile file) {
    try {
      return new FileUploadData(
          file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getBytes());
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  /**
   * Arma el contexto de quien pide a partir del JWT ya validado por el filtro: el principal es el
   * id del usuario (claim {@code sub}) y la unica authority es {@code ROLE_<rol>}.
   *
   * <p>Se usa {@code getName()} y no {@code getPrincipal()} porque resuelve el identificador tanto
   * si el principal es un {@code String} (lo que hace el filtro JWT) como si es un
   * {@code UserDetails}.
   */
  public RequesterContext toRequesterContext(
      final Authentication authentication, final String authorizationHeader) {
    return new RequesterContext(
        authentication.getName(), resolveRole(authentication), authorizationHeader);
  }

  public UserFileRestResponse toResponse(final UserFileModel userFile) {
    return new UserFileRestResponse(
        userFile.id(),
        userFile.fileKey(),
        userFile.contentType(),
        userFile.sizeBytes(),
        userFile.createdAt().toString());
  }

  public List<UserFileRestResponse> toResponseList(final List<UserFileModel> userFiles) {
    return userFiles.stream().map(UserFileRestMapper::toResponse).toList();
  }

  private UserRole resolveRole(final Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(Objects::nonNull)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .map(UserRole::fromString)
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("La autenticacion no tiene un rol de dominio."));
  }
}
