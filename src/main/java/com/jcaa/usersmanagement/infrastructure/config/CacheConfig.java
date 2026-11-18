package com.jcaa.usersmanagement.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.application.port.out.UserCachePort;
import com.jcaa.usersmanagement.infrastructure.adapter.cache.NoOpUserCacheAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.cache.ValkeyUserCacheAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * Elige la implementacion de {@link UserCachePort} segun {@code cache.type} ({@code CACHE_TYPE}).
 *
 * <p>{@code valkey} activa el adaptador real; cualquier otro valor (por defecto {@code none}) deja
 * la cache desactivada. Asi se puede apagar la cache en produccion sin recompilar.
 *
 * <p>Se decide en un unico {@code @Bean} y no con dos {@code @ConditionalOnProperty}: la condicion
 * "distinto de valkey" no se puede expresar con esa anotacion sin dejar los dos beans activos a la
 * vez.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class CacheConfig {

  private static final String CACHE_TYPE_VALKEY = "valkey";

  @Bean
  public UserCachePort userCachePort(
      @Value("${cache.type:none}") final String cacheType,
      @Value("${cache.user.ttl-seconds:300}") final long ttlSeconds,
      final ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      final ObjectMapper objectMapper) {

    if (!CACHE_TYPE_VALKEY.equalsIgnoreCase(cacheType)) {
      log.info("Cache de usuarios desactivada. cacheType={}", cacheType);
      return new NoOpUserCacheAdapter();
    }

    final StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (Objects.isNull(redisTemplate)) {
      log.warn("cache.type=valkey pero no hay StringRedisTemplate: la cache queda desactivada.");
      return new NoOpUserCacheAdapter();
    }

    log.info("Cache de usuarios activa en Valkey. ttlSeconds={}", ttlSeconds);
    return new ValkeyUserCacheAdapter(redisTemplate, objectMapper, Duration.ofSeconds(ttlSeconds));
  }
}
