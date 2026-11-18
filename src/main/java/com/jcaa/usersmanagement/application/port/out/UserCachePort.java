package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.application.service.dto.view.UserView;

import java.util.Optional;

/**
 * Cache de lectura de usuarios.
 *
 * <p>Deliberadamente NO se usa {@code @Cacheable}: si el servidor de cache falla, la anotacion
 * propaga la excepcion y tumba el {@code GET}. Las implementaciones de este puerto deben absorber
 * cualquier fallo y comportarse como una cache vacia.
 */
public interface UserCachePort {

  Optional<UserView> get(String userId);

  void put(String userId, UserView user);

  void evict(String userId);
}
