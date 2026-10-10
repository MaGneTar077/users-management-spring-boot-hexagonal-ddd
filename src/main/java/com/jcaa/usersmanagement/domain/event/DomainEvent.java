package com.jcaa.usersmanagement.domain.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class DomainEvent {

  private final String eventId;
  private final String eventName;
  private final LocalDateTime occurredOn;

  protected DomainEvent(final String eventName) {
    // Identificador único del evento: permite idempotencia real en los consumidores.
    this.eventId = UUID.randomUUID().toString();
    this.eventName = eventName;
    this.occurredOn = LocalDateTime.now();
  }

  public abstract Map<String, String> payload();
}
