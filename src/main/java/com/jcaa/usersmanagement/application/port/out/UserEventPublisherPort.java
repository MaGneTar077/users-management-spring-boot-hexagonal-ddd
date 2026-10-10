package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.domain.event.UserCreatedDomainEvent;
import com.jcaa.usersmanagement.domain.event.UserDeletedDomainEvent;
import com.jcaa.usersmanagement.domain.event.UserUpdatedDomainEvent;

/** Puerto de salida para publicar eventos de dominio de usuarios en un bus de mensajería. */
public interface UserEventPublisherPort {

  void publishUserCreated(UserCreatedDomainEvent event);

  void publishUserUpdated(UserUpdatedDomainEvent event);

  void publishUserDeleted(UserDeletedDomainEvent event);
}
