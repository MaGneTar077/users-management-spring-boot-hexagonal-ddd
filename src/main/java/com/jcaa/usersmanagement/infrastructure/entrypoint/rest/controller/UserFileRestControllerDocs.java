package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import com.jcaa.usersmanagement.infrastructure.config.OpenApiConfig;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.ApiErrorResponse;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserFileRestResponse;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserFileUrlRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Contrato OpenAPI / Swagger para {@link UserFileRestController}.
 *
 * <p>users-management es el unico punto de entrada del cliente: nunca se expone el file-service ni
 * las credenciales de Backblaze.
 */
@Tag(
    name = "User files",
    description =
        "Archivos de usuario. Los guarda el file-service en Backblaze; aqui solo se guarda la key. "
            + "Acceso: el dueño del recurso o un ADMIN.")
public interface UserFileRestControllerDocs {

  // ─────────────────────────────────────────────────────────────────────────────
  // POST /api/users/{userId}/files
  // ─────────────────────────────────────────────────────────────────────────────

  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  @Operation(
      summary = "Subir un archivo",
      description =
          "Recibe un multipart con el campo 'file', lo envia al file-service y guarda la fila en "
              + "user_files. Maximo 5 MB. Tipos admitidos: application/pdf, image/png, image/jpeg.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Archivo subido y registrado.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserFileRestResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Archivo vacio, demasiado grande o de un tipo no admitido.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "No es el dueño del recurso ni tiene rol ADMIN.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "No existe ningun usuario con el ID proporcionado.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "503",
        description = "El file-service no esta disponible.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  UserFileRestResponse upload(
      @Parameter(description = "ID del usuario dueño del archivo.", required = true) @PathVariable
          String userId,
      MultipartFile file,
      Authentication authentication,
      String authorizationHeader);

  // ─────────────────────────────────────────────────────────────────────────────
  // GET /api/users/{userId}/files
  // ─────────────────────────────────────────────────────────────────────────────

  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  @Operation(
      summary = "Listar los archivos de un usuario",
      description = "Devuelve las filas de user_files de ese usuario. Puede ser vacia.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Listado obtenido.",
        content =
            @Content(
                mediaType = "application/json",
                array =
                    @ArraySchema(schema = @Schema(implementation = UserFileRestResponse.class)))),
    @ApiResponse(
        responseCode = "403",
        description = "No es el dueño del recurso ni tiene rol ADMIN.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  List<UserFileRestResponse> list(
      @Parameter(description = "ID del usuario dueño de los archivos.", required = true)
          @PathVariable
          String userId,
      Authentication authentication,
      String authorizationHeader);

  // ─────────────────────────────────────────────────────────────────────────────
  // GET /api/users/{userId}/files/{fileId}
  // ─────────────────────────────────────────────────────────────────────────────

  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  @Operation(
      summary = "Obtener la URL de descarga",
      description =
          "Busca la key en user_files y pide al file-service una URL firmada valida 5 minutos.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "URL firmada generada.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserFileUrlRestResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "No es el dueño del recurso ni tiene rol ADMIN.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "El archivo no existe o no pertenece a ese usuario.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "503",
        description = "El file-service no esta disponible.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  UserFileUrlRestResponse getDownloadUrl(
      @Parameter(description = "ID del usuario dueño del archivo.", required = true) @PathVariable
          String userId,
      @Parameter(description = "ID de la fila en user_files.", required = true) @PathVariable
          String fileId,
      Authentication authentication,
      String authorizationHeader);

  // ─────────────────────────────────────────────────────────────────────────────
  // DELETE /api/users/{userId}/files/{fileId}
  // ─────────────────────────────────────────────────────────────────────────────

  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  @Operation(
      summary = "Eliminar un archivo",
      description = "Pide al file-service borrar la key y despues borra la fila de user_files.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Archivo eliminado."),
    @ApiResponse(
        responseCode = "403",
        description = "No es el dueño del recurso ni tiene rol ADMIN.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "El archivo no existe o no pertenece a ese usuario.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "503",
        description = "El file-service no esta disponible.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  void delete(
      @Parameter(description = "ID del usuario dueño del archivo.", required = true) @PathVariable
          String userId,
      @Parameter(description = "ID de la fila en user_files.", required = true) @PathVariable
          String fileId,
      Authentication authentication,
      String authorizationHeader);
}
