package com.jcaa.usersmanagement.infrastructure.adapter.cache;

import com.jcaa.usersmanagement.application.port.out.UserCachePort;
import com.jcaa.usersmanagement.application.service.dto.view.UserView;

import java.util.Optional;

/**
 * Cache desactivada ({@code CACHE_TYPE=none}): se comporta como una cache siempre vacia.
 *
 * <p>Permite arrancar y operar sin Valkey sin ninguna rama condicional en los casos de uso.
 */
public class NoOpUserCacheAdapter implements UserCachePort {

  @Override
  public Optional<UserView> get(final String userId) {
    return Optional.empty();
  }

  @Override
  public void put(final String userId, final UserView user) {
    // Sin cache: nada que guardar.
  }

  @Override
  public void evict(final String userId) {
    // Sin cache: nada que invalidar.
  }
}
