package com.jcaa.usersmanagement.infrastructure.adapter.filestorage;

import com.jcaa.usersmanagement.application.port.out.FileServicePort;
import com.jcaa.usersmanagement.application.port.out.dto.FileUploadData;
import com.jcaa.usersmanagement.application.port.out.dto.StoredFileData;
import com.jcaa.usersmanagement.infrastructure.adapter.filestorage.dto.DownloadUrlServiceResponse;
import com.jcaa.usersmanagement.infrastructure.adapter.filestorage.dto.UploadedFileServiceResponse;
import com.jcaa.usersmanagement.infrastructure.adapter.filestorage.exception.FileServiceRejectedException;
import com.jcaa.usersmanagement.infrastructure.adapter.filestorage.exception.FileServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Adaptador de salida hacia el file-service, que es quien escribe en Backblaze.
 *
 * <p>Reenvia el header {@code Authorization} tal cual: el file-service valida el mismo JWT con el
 * mismo {@code JWT_SECRET}. Nunca se construye ni se firma un token aqui.
 *
 * <p>Traduccion de errores: 5xx o inalcanzable -> {@link FileServiceUnavailableException} (503 al
 * cliente); 4xx -> {@link FileServiceRejectedException}, que conserva el codigo recibido.
 */
@Slf4j
@Component
public class FileServiceRestAdapter implements FileServicePort {

  private static final String PATH_FILES = "/api/files";
  private static final String PATH_FILES_URL = "/api/files/url";
  private static final String PATH_FILES_OF_USER = "/api/files/user/{userId}";

  private static final String PARAM_KEY = "key";
  private static final String PARAM_EXPIRES_IN_SECONDS = "expiresInSeconds";
  private static final String PART_FILE = "file";
  private static final String PART_USER_ID = "userId";

  /** Las URLs firmadas se piden con 5 minutos de vigencia. */
  private static final int DOWNLOAD_URL_TTL_SECONDS = 300;

  /** Respuesta valida en HTTP pero inutil para nosotros (sin key o sin url). */
  private static final int STATUS_BAD_GATEWAY = 502;

  private static final int MAX_ERROR_BODY_CHARS = 500;
  private static final String DEFAULT_UPLOAD_FILENAME = "archivo";

  private final RestClient restClient;
  private final FileServiceProperties properties;

  public FileServiceRestAdapter(
      final RestClient.Builder restClientBuilder, final FileServiceProperties properties) {
    this.properties = properties;
    this.restClient =
        restClientBuilder.baseUrl(properties.isConfigured() ? properties.url() : "").build();
    if (!properties.isConfigured()) {
      log.warn("FILE_SERVICE_URL no esta definido: las operaciones de archivos responderan 503.");
    }
  }

  @Override
  public StoredFileData upload(
      final String userId, final FileUploadData file, final String authorizationHeader) {
    ensureConfigured();
    final UploadedFileServiceResponse response =
        execute(
            () ->
                restClient
                    .post()
                    .uri(PATH_FILES)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(buildMultipartBody(userId, file))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, FileServiceRestAdapter::translateError)
                    .body(UploadedFileServiceResponse.class));

    if (Objects.isNull(response) || Objects.isNull(response.key()) || response.key().isBlank()) {
      throw FileServiceUnavailableException.becauseItReturnedServerError(STATUS_BAD_GATEWAY);
    }

    // El file-service es la fuente de verdad de la key; los metadatos se completan con los
    // del archivo recibido si no vienen en la respuesta.
    return new StoredFileData(
        response.key(),
        Objects.isNull(response.contentType()) ? file.contentType() : response.contentType(),
        Objects.isNull(response.sizeBytes()) ? file.sizeBytes() : response.sizeBytes());
  }

  @Override
  public String getDownloadUrl(final String fileKey, final String authorizationHeader) {
    ensureConfigured();
    final DownloadUrlServiceResponse response =
        execute(
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path(PATH_FILES_URL)
                                .queryParam(PARAM_KEY, fileKey)
                                .queryParam(PARAM_EXPIRES_IN_SECONDS, DOWNLOAD_URL_TTL_SECONDS)
                                .build())
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, FileServiceRestAdapter::translateError)
                    .body(DownloadUrlServiceResponse.class));

    if (Objects.isNull(response) || Objects.isNull(response.url()) || response.url().isBlank()) {
      throw FileServiceUnavailableException.becauseItReturnedServerError(STATUS_BAD_GATEWAY);
    }
    return response.url();
  }

  @Override
  public void delete(final String fileKey, final String authorizationHeader) {
    ensureConfigured();
    execute(
        () ->
            restClient
                .delete()
                .uri(uriBuilder -> uriBuilder.path(PATH_FILES).queryParam(PARAM_KEY, fileKey).build())
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, FileServiceRestAdapter::translateError)
                .toBodilessEntity());
  }

  @Override
  public void deleteAllOfUser(final String userId, final String authorizationHeader) {
    ensureConfigured();
    execute(
        () ->
            restClient
                .delete()
                .uri(PATH_FILES_OF_USER, userId)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, FileServiceRestAdapter::translateError)
                .toBodilessEntity());
  }

  private void ensureConfigured() {
    if (!properties.isConfigured()) {
      throw FileServiceUnavailableException.becauseItIsNotConfigured();
    }
  }

  /** Centraliza la traduccion de fallos de red: nunca se filtra una excepcion de Spring Web. */
  private static <T> T execute(final FileServiceCall<T> call) {
    try {
      return call.run();
    } catch (final ResourceAccessException exception) {
      throw FileServiceUnavailableException.becauseItIsUnreachable(exception);
    }
  }

  private static void translateError(final HttpRequest request, final ClientHttpResponse response)
      throws IOException {
    final HttpStatusCode statusCode = response.getStatusCode();
    if (statusCode.is5xxServerError()) {
      log.warn("El file-service respondio con error de servidor. status={}", statusCode.value());
      throw FileServiceUnavailableException.becauseItReturnedServerError(statusCode.value());
    }
    log.info("El file-service rechazo la peticion. status={}", statusCode.value());
    throw FileServiceRejectedException.withStatus(statusCode.value(), readErrorBody(response));
  }

  private static String readErrorBody(final ClientHttpResponse response) throws IOException {
    final String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    return body.length() > MAX_ERROR_BODY_CHARS ? body.substring(0, MAX_ERROR_BODY_CHARS) : body;
  }

  private static MultiValueMap<String, Object> buildMultipartBody(
      final String userId, final FileUploadData file) {
    final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(PART_FILE, toNamedResource(file));
    body.add(PART_USER_ID, userId);
    return body;
  }

  /** {@code ByteArrayResource} sin nombre no genera la cabecera {@code filename} del multipart. */
  private static ByteArrayResource toNamedResource(final FileUploadData file) {
    final String filename =
        (Objects.isNull(file.originalFilename()) || file.originalFilename().isBlank())
            ? DEFAULT_UPLOAD_FILENAME
            : file.originalFilename();
    return new ByteArrayResource(file.content()) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }

  /** Permite envolver las llamadas al {@code RestClient} en un unico manejo de errores de red. */
  @FunctionalInterface
  private interface FileServiceCall<T> {
    T run();
  }
}
