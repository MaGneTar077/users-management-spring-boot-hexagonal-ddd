package com.jcaa.usersmanagement.infrastructure.adapter.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.application.port.out.UserCachePort;
import com.jcaa.usersmanagement.application.service.dto.view.UserView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache de usuarios en Valkey.
 *
 * <p>Clave {@code user:{id}}, valor JSON del {@link UserView} (sin contraseña), con TTL
 * configurable. Se serializa con Jackson y no con serializacion de Java: el valor queda legible en
 * Redis Insight y cambiar la clase no invalida lo ya guardado de forma catastrofica.
 *
 * <p><b>Tolerancia a fallos:</b> toda operacion va en {@code try/catch} y nunca lanza hacia afuera.
 * Si Valkey esta caido, {@code get} devuelve vacio (se lee de PostgreSQL) y {@code put}/{@code
 * evict} solo registran un aviso.
 */
@Slf4j
public class ValkeyUserCacheAdapter implements UserCachePort {

  private static final String KEY_PREFIX = "user:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration ttl;

  public ValkeyUserCacheAdapter(
      final StringRedisTemplate redisTemplate,
      final ObjectMapper objectMapper,
      final Duration ttl) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.ttl = ttl;
  }

  @Override
  public Optional<UserView> get(final String userId) {
    try {
      final String json = redisTemplate.opsForValue().get(keyOf(userId));
      if (Objects.isNull(json)) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(json, UserView.class));
    } catch (final RuntimeException | JsonProcessingException exception) {
      log.warn("No se pudo leer de la cache. userId={}", userId, exception);
      return Optional.empty();
    }
  }

  @Override
  public void put(final String userId, final UserView user) {
    try {
      redisTemplate.opsForValue().set(keyOf(userId), objectMapper.writeValueAsString(user), ttl);
    } catch (final RuntimeException | JsonProcessingException exception) {
      log.warn("No se pudo escribir en la cache. userId={}", userId, exception);
    }
  }

  @Override
  public void evict(final String userId) {
    try {
      redisTemplate.delete(keyOf(userId));
    } catch (final RuntimeException exception) {
      log.warn("No se pudo invalidar la cache. userId={}", userId, exception);
    }
  }

  private static String keyOf(final String userId) {
    return KEY_PREFIX + userId;
  }
}
