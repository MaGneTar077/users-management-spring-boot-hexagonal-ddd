package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jcaa.usersmanagement.application.port.in.DeleteUserFileUseCase;
import com.jcaa.usersmanagement.application.port.in.GetUserFileUrlUseCase;
import com.jcaa.usersmanagement.application.port.in.ListUserFilesUseCase;
import com.jcaa.usersmanagement.application.port.in.UploadUserFileUseCase;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.exception.UserFileAccessDeniedException;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.policy.UserFileUploadPolicy;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.infrastructure.adapter.filestorage.exception.FileServiceUnavailableException;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.advice.GlobalExceptionHandler;
import com.jcaa.usersmanagement.infrastructure.security.JwtAuthenticationFilter;
import com.jcaa.usersmanagement.infrastructure.security.JwtPrincipal;
import com.jcaa.usersmanagement.infrastructure.security.JwtTokenService;
import com.jcaa.usersmanagement.infrastructure.security.RestAccessDeniedHandler;
import com.jcaa.usersmanagement.infrastructure.security.RestAuthenticationEntryPoint;
import com.jcaa.usersmanagement.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

/**
 * Tests de UserFileRestController, atravesando la cadena de seguridad real.
 *
 * <p>El caso critico es el de un MEMBER: si las rutas de archivos quedaran detrás de la regla
 * general {@code /api/users/**} (ADMIN o REVIEWER), estas peticiones responderian 403 sin llegar
 * nunca al controlador.
 */
@WebMvcTest(
    controllers = UserFileRestController.class,
    properties = "spring.main.web-application-type=servlet")
@Import({
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  RestAuthenticationEntryPoint.class,
  RestAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
@DisplayName("UserFileRestController")
class UserFileRestControllerTest {

  private static final String USER_ID = "8f684dc4-0c67-4813-8d58-7b5e7f7937b8";
  private static final String FILE_ID = "file-001";
  private static final String TOKEN = "token-123";
  private static final String AUTH_HEADER = "Bearer " + TOKEN;
  private static final String FILE_KEY = "users/owner/doc.pdf";

  @Autowired private MockMvc mockMvc;

  @MockBean private UploadUserFileUseCase uploadUserFileUseCase;
  @MockBean private ListUserFilesUseCase listUserFilesUseCase;
  @MockBean private GetUserFileUrlUseCase getUserFileUrlUseCase;
  @MockBean private DeleteUserFileUseCase deleteUserFileUseCase;
  @MockBean private JwtTokenService jwtTokenService;

  @Test
  @DisplayName("GET /api/users/{id}/files deja pasar a un MEMBER (orden de reglas correcto)")
  void shouldLetMemberReachTheFilesRoute() throws Exception {
    // Arrange
    authenticateAs(USER_ID, UserRole.MEMBER);
    when(listUserFilesUseCase.execute(any())).thenReturn(List.of(fileOf()));

    // Act & Assert
    mockMvc
        .perform(
            get("/api/users/{id}/files", USER_ID).header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value(FILE_KEY));
  }

  @Test
  @DisplayName("POST /api/users/{id}/files sube el archivo y devuelve la key")
  void shouldUploadFile() throws Exception {
    // Arrange
    authenticateAs(USER_ID, UserRole.MEMBER);
    final MockMultipartFile file =
        new MockMultipartFile(
            "file", "doc.pdf", UserFileUploadPolicy.CONTENT_TYPE_PDF, new byte[] {1, 2, 3});
    when(uploadUserFileUseCase.execute(any())).thenReturn(fileOf());

    // Act & Assert
    mockMvc
        .perform(
            multipart("/api/users/{id}/files", USER_ID)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(FILE_ID))
        .andExpect(jsonPath("$.key").value(FILE_KEY))
        .andExpect(jsonPath("$.sizeBytes").value(2048));
  }

  @Test
  @DisplayName("GET /api/users/{id}/files responde 403 cuando no es el dueño")
  void shouldReturnForbiddenForForeignUser() throws Exception {
    // Arrange
    authenticateAs("otro-usuario", UserRole.MEMBER);
    when(listUserFilesUseCase.execute(any()))
        .thenThrow(UserFileAccessDeniedException.becauseRequesterIsNotOwnerNorAdmin());

    // Act & Assert
    mockMvc
        .perform(
            get("/api/users/{id}/files", USER_ID).header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /api/users/{id}/files sin token responde 401")
  void shouldReturnUnauthorizedWithoutToken() throws Exception {
    // Act & Assert
    mockMvc
        .perform(get("/api/users/{id}/files", USER_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /api/users/{id}/files/{fileId} devuelve la URL firmada y su vigencia")
  void shouldReturnSignedUrl() throws Exception {
    // Arrange
    authenticateAs(USER_ID, UserRole.MEMBER);
    when(getUserFileUrlUseCase.execute(any())).thenReturn("https://backblaze.example/doc.pdf");

    // Act & Assert
    mockMvc
        .perform(
            get("/api/users/{id}/files/{fileId}", USER_ID, FILE_ID)
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://backblaze.example/doc.pdf"))
        .andExpect(jsonPath("$.expiresInSeconds").value(300));
  }

  @Test
  @DisplayName("Responde 503 (no 500) cuando el file-service no esta disponible")
  void shouldReturnServiceUnavailableWhenFileServiceIsDown() throws Exception {
    // Arrange
    authenticateAs(USER_ID, UserRole.MEMBER);
    when(getUserFileUrlUseCase.execute(any()))
        .thenThrow(FileServiceUnavailableException.becauseItIsUnreachable(new RuntimeException()));

    // Act & Assert
    mockMvc
        .perform(
            get("/api/users/{id}/files/{fileId}", USER_ID, FILE_ID)
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  @DisplayName("DELETE /api/users/{id}/files/{fileId} responde 204")
  void shouldDeleteFile() throws Exception {
    // Arrange
    authenticateAs(USER_ID, UserRole.MEMBER);

    // Act & Assert
    mockMvc
        .perform(
            delete("/api/users/{id}/files/{fileId}", USER_ID, FILE_ID)
                .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
        .andExpect(status().isNoContent());
  }

  /** Hace que el filtro JWT real autentique la peticion con ese id y rol. */
  private void authenticateAs(final String userId, final UserRole role) {
    when(jwtTokenService.parse(TOKEN)).thenReturn(new JwtPrincipal(userId, role));
  }

  private static UserFileModel fileOf() {
    return new UserFileModel(
        FILE_ID,
        new UserId(USER_ID),
        FILE_KEY,
        UserFileUploadPolicy.CONTENT_TYPE_PDF,
        2048L,
        Instant.parse("2026-11-17T10:00:00Z"));
  }
}
