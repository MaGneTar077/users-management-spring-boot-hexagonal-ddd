package com.jcaa.usersmanagement.infrastructure.adapter.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.event.UserCreatedDomainEvent;
import com.jcaa.usersmanagement.domain.event.UserDeletedDomainEvent;
import com.jcaa.usersmanagement.domain.event.UserUpdatedDomainEvent;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import com.jcaa.usersmanagement.infrastructure.config.KafkaProperties;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Tests para KafkaUserEventPublisherAdapter.
 *
 * <p>Cubre: publicacion de los tres eventos en su topico correspondiente con el envelope esperado,
 * publicacion del email (excepcion consciente para el microservicio de notificaciones), exclusion
 * permanente del password y el caso en que falla la serializacion (no se envia nada a Kafka).
 */
@DisplayName("KafkaUserEventPublisherAdapter")
@ExtendWith(MockitoExtension.class)
class KafkaUserEventPublisherAdapterTest {

  private static final String TOPIC_CREATED = "users.created";
  private static final String TOPIC_UPDATED = "users.updated";
  private static final String TOPIC_DELETED = "users.deleted";

  private static final String ID = "u-001";
  private static final String NAME = "John Doe";
  private static final String EMAIL = "john@example.com";
  private static final String HASH = "$2a$12$abcdefghijklmnopqrstuO";
  private static final String ROLE_ADMIN = "ADMIN";
  private static final String STATUS_ACTIVE = "ACTIVE";

  private static final String EVENT_TYPE_USER_CREATED = "USER_CREATED";
  private static final String EVENT_TYPE_USER_UPDATED = "USER_UPDATED";
  private static final String EVENT_TYPE_USER_DELETED = "USER_DELETED";

  private static final String KEY_EVENT_ID = "eventId";
  private static final String KEY_EVENT_TYPE = "eventType";
  private static final String KEY_OCCURRED_AT = "occurredAt";
  private static final String KEY_AGGREGATE_ID = "aggregateId";
  private static final String KEY_PAYLOAD = "payload";
  private static final String FIELD_ID = "id";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_ROLE = "role";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_EMAIL = "email";
  private static final String FIELD_PASSWORD = "password";

  @Mock private KafkaTemplate<String, String> kafkaTemplate;

  private ObjectMapper objectMapper;
  private KafkaProperties kafkaProperties;
  private UserModel user;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();

    kafkaProperties =
        new KafkaProperties(
            "localhost:9092",
            "SASL_SSL",
            "PLAIN",
            "user",
            "secret",
            "",
            "users-management-group",
            new KafkaProperties.Topics(TOPIC_CREATED, TOPIC_UPDATED, TOPIC_DELETED));

    user =
        new UserModel(
            new UserId(ID),
            new UserName(NAME),
            new UserEmail(EMAIL),
            UserPassword.fromHash(HASH),
            UserRole.ADMIN,
            UserStatus.ACTIVE);
  }

  // ── publishUserCreated()

  @Test
  @DisplayName("publishUserCreated() publica en el topico de creacion con email y sin password")
  void shouldPublishUserCreated() throws JsonProcessingException {
    // Arrange
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // Act
    adapter().publishUserCreated(new UserCreatedDomainEvent(user));

    // Assert
    final JsonNode envelope = capturePublishedJson(TOPIC_CREATED);

    assertAll(
        "publishUserCreated()",
        () -> assertFalse(envelope.get(KEY_EVENT_ID).asText().isBlank(), "eventId no debe ser vacio"),
        () -> assertEquals(EVENT_TYPE_USER_CREATED, envelope.get(KEY_EVENT_TYPE).asText()),
        () ->
            assertFalse(
                envelope.get(KEY_OCCURRED_AT).asText().isBlank(), "occurredAt no debe ser vacio"),
        () -> assertEquals(ID, envelope.get(KEY_AGGREGATE_ID).asText()),
        () -> assertEquals(ID, envelope.get(KEY_PAYLOAD).get(FIELD_ID).asText()),
        () -> assertEquals(NAME, envelope.get(KEY_PAYLOAD).get(FIELD_NAME).asText()),
        () -> assertEquals(ROLE_ADMIN, envelope.get(KEY_PAYLOAD).get(FIELD_ROLE).asText()),
        () -> assertEquals(STATUS_ACTIVE, envelope.get(KEY_PAYLOAD).get(FIELD_STATUS).asText()),
        () -> assertEquals(EMAIL, envelope.get(KEY_PAYLOAD).get(FIELD_EMAIL).asText()),
        () ->
            assertFalse(envelope.get(KEY_PAYLOAD).has(FIELD_PASSWORD), "no debe exponer password"));
  }

  // ── publishUserUpdated()

  @Test
  @DisplayName("publishUserUpdated() publica en el topico de actualizacion con email y sin password")
  void shouldPublishUserUpdated() throws JsonProcessingException {
    // Arrange
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // Act
    adapter().publishUserUpdated(new UserUpdatedDomainEvent(user));

    // Assert
    final JsonNode envelope = capturePublishedJson(TOPIC_UPDATED);

    assertAll(
        "publishUserUpdated()",
        () -> assertEquals(EVENT_TYPE_USER_UPDATED, envelope.get(KEY_EVENT_TYPE).asText()),
        () -> assertEquals(ID, envelope.get(KEY_PAYLOAD).get(FIELD_ID).asText()),
        () -> assertEquals(EMAIL, envelope.get(KEY_PAYLOAD).get(FIELD_EMAIL).asText()),
        () ->
            assertFalse(envelope.get(KEY_PAYLOAD).has(FIELD_PASSWORD), "no debe exponer password"));
  }

  // ── publishUserDeleted()

  @Test
  @DisplayName("publishUserDeleted() publica solo el id en el payload")
  void shouldPublishUserDeleted() throws JsonProcessingException {
    // Arrange
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // Act
    adapter().publishUserDeleted(new UserDeletedDomainEvent(new UserId(ID)));

    // Assert
    final JsonNode envelope = capturePublishedJson(TOPIC_DELETED);

    assertAll(
        "publishUserDeleted()",
        () -> assertEquals(EVENT_TYPE_USER_DELETED, envelope.get(KEY_EVENT_TYPE).asText()),
        () -> assertEquals(ID, envelope.get(KEY_PAYLOAD).get(FIELD_ID).asText()),
        () -> assertEquals(1, envelope.get(KEY_PAYLOAD).size(), "el payload solo debe exponer id"),
        () -> assertFalse(envelope.get(KEY_PAYLOAD).has(FIELD_EMAIL), "deleted no lleva email"),
        () ->
            assertFalse(envelope.get(KEY_PAYLOAD).has(FIELD_PASSWORD), "no debe exponer password"));
  }

  // ── fallo de serializacion

  @Test
  @DisplayName("si falla la serializacion no se envia nada a Kafka")
  void shouldNotSendWhenSerializationFails() throws JsonProcessingException {
    // Arrange
    final ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
    when(failingObjectMapper.writeValueAsString(any()))
        .thenThrow(new JsonProcessingException("boom") {});

    final KafkaUserEventPublisherAdapter failingAdapter =
        new KafkaUserEventPublisherAdapter(kafkaTemplate, failingObjectMapper, kafkaProperties);

    // Act
    failingAdapter.publishUserCreated(new UserCreatedDomainEvent(user));

    // Assert
    verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
  }

  private KafkaUserEventPublisherAdapter adapter() {
    return new KafkaUserEventPublisherAdapter(kafkaTemplate, objectMapper, kafkaProperties);
  }

  private JsonNode capturePublishedJson(final String expectedTopic) throws JsonProcessingException {
    final ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(kafkaTemplate).send(eq(expectedTopic), eq(ID), jsonCaptor.capture());
    return objectMapper.readTree(jsonCaptor.getValue());
  }
}
