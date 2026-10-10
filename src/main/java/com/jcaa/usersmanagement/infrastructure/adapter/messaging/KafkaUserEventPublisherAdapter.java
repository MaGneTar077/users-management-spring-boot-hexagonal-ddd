package com.jcaa.usersmanagement.infrastructure.adapter.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.application.port.out.UserEventPublisherPort;
import com.jcaa.usersmanagement.domain.event.UserCreatedDomainEvent;
import com.jcaa.usersmanagement.domain.event.UserDeletedDomainEvent;
import com.jcaa.usersmanagement.domain.event.UserUpdatedDomainEvent;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.infrastructure.config.KafkaProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter de salida que publica eventos de dominio de usuarios en el broker de Kafka.
 *
 * <p>Sanitiza el payload antes de publicar: expone únicamente {@code id, name, role, status} y
 * excluye explícitamente {@code email} y {@code password} (PII). Actualmente no relanza errores de
 * publicación hacia el caller; está previsto migrar a un patrón Transactional Outbox para no perder
 * eventos si falla la publicación tras el guardado.
 */
@Slf4j
@Component
public class KafkaUserEventPublisherAdapter implements UserEventPublisherPort {

  private static final String EVENT_TYPE_USER_CREATED = "USER_CREATED";
  private static final String EVENT_TYPE_USER_UPDATED = "USER_UPDATED";
  private static final String EVENT_TYPE_USER_DELETED = "USER_DELETED";

  private static final String FIELD_ID = "id";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_ROLE = "role";
  private static final String FIELD_STATUS = "status";

  private static final String KEY_EVENT_ID = "eventId";
  private static final String KEY_EVENT_TYPE = "eventType";
  private static final String KEY_OCCURRED_AT = "occurredAt";
  private static final String KEY_AGGREGATE_ID = "aggregateId";
  private static final String KEY_PAYLOAD = "payload";

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final KafkaProperties kafkaProperties;

  public KafkaUserEventPublisherAdapter(
      final KafkaTemplate<String, String> kafkaTemplate,
      final ObjectMapper objectMapper,
      final KafkaProperties kafkaProperties) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.kafkaProperties = kafkaProperties;
  }

  @Override
  public void publishUserCreated(final UserCreatedDomainEvent event) {
    final UserModel user = event.getUser();
    publish(
        kafkaProperties.topics().userCreated(),
        event.getEventId(),
        EVENT_TYPE_USER_CREATED,
        event.getOccurredOn().toString(),
        user.getId().value(),
        sanitizeUser(user));
  }

  @Override
  public void publishUserUpdated(final UserUpdatedDomainEvent event) {
    final UserModel user = event.getUser();
    publish(
        kafkaProperties.topics().userUpdated(),
        event.getEventId(),
        EVENT_TYPE_USER_UPDATED,
        event.getOccurredOn().toString(),
        user.getId().value(),
        sanitizeUser(user));
  }

  @Override
  public void publishUserDeleted(final UserDeletedDomainEvent event) {
    final String userId = event.getUserId().value();
    publish(
        kafkaProperties.topics().userDeleted(),
        event.getEventId(),
        EVENT_TYPE_USER_DELETED,
        event.getOccurredOn().toString(),
        userId,
        Map.of(FIELD_ID, userId));
  }

  private static Map<String, String> sanitizeUser(final UserModel user) {
    final Map<String, String> payload = new LinkedHashMap<>();
    payload.put(FIELD_ID, user.getId().value());
    payload.put(FIELD_NAME, user.getName().value());
    payload.put(FIELD_ROLE, user.getRole().name());
    payload.put(FIELD_STATUS, user.getStatus().name());
    return payload;
  }

  private void publish(
      final String topic,
      final String eventId,
      final String eventType,
      final String occurredAt,
      final String aggregateId,
      final Map<String, String> payload) {
    final Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put(KEY_EVENT_ID, eventId);
    envelope.put(KEY_EVENT_TYPE, eventType);
    envelope.put(KEY_OCCURRED_AT, occurredAt);
    envelope.put(KEY_AGGREGATE_ID, aggregateId);
    envelope.put(KEY_PAYLOAD, payload);

    final String json;
    try {
      json = objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException ex) {
      log.error("Error serializando evento. topic={} eventId={}", topic, eventId, ex);
      return;
    }

    kafkaTemplate
        .send(topic, aggregateId, json)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error(
                    "Error publicando evento. topic={} eventId={} key={}",
                    topic,
                    eventId,
                    aggregateId,
                    ex);
                // TODO: migrar a Transactional Outbox para no perder eventos.
              } else {
                log.debug(
                    "Evento publicado. topic={} eventId={} key={}", topic, eventId, aggregateId);
              }
            });
  }
}
