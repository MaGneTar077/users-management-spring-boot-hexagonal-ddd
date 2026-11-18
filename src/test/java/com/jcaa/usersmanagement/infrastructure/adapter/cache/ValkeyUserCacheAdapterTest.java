package com.jcaa.usersmanagement.infrastructure.adapter.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.application.service.dto.view.UserView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

/**
 * Tests de ValkeyUserCacheAdapter.
 *
 * <p>Lo esencial que se verifica: ninguna operacion propaga excepciones, de modo que un Valkey
 * caido no tumba el {@code GET /api/users/{id}}.
 */
@DisplayName("ValkeyUserCacheAdapter")
@ExtendWith(MockitoExtension.class)
class ValkeyUserCacheAdapterTest {

  private static final String USER_ID = "u-001";
  private static final String KEY = "user:u-001";
  private static final Duration TTL = Duration.ofSeconds(300);

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private ObjectMapper objectMapper;
  private ValkeyUserCacheAdapter adapter;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    adapter = new ValkeyUserCacheAdapter(redisTemplate, objectMapper, TTL);
  }

  // ── flujo feliz

  @Test
  @DisplayName("put() guarda el JSON bajo la clave user:{id} con el TTL configurado")
  void shouldStoreJsonWithTtl() throws Exception {
    // Arrange
    final UserView user = userView();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Act
    adapter.put(USER_ID, user);

    // Assert
    verify(valueOperations).set(KEY, objectMapper.writeValueAsString(user), TTL);
  }

  @Test
  @DisplayName("get() deserializa el JSON guardado")
  void shouldReadStoredJson() throws Exception {
    // Arrange
    final UserView user = userView();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn(objectMapper.writeValueAsString(user));

    // Act
    final Optional<UserView> result = adapter.get(USER_ID);

    // Assert
    assertEquals(Optional.of(user), result);
  }

  @Test
  @DisplayName("get() devuelve vacio cuando la clave no existe")
  void shouldReturnEmptyWhenKeyIsAbsent() {
    // Arrange
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn(null);

    // Act
    final Optional<UserView> result = adapter.get(USER_ID);

    // Assert
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("evict() borra la clave user:{id}")
  void shouldDeleteKey() {
    // Act
    adapter.evict(USER_ID);

    // Assert
    verify(redisTemplate).delete(KEY);
  }

  // ── tolerancia a fallos

  @Test
  @DisplayName("get() devuelve vacio y no lanza cuando Valkey no responde")
  void shouldReturnEmptyWhenValkeyIsDown() {
    // Arrange
    when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("caido"));

    // Act
    final Optional<UserView> result = adapter.get(USER_ID);

    // Assert
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("get() devuelve vacio cuando el JSON guardado no es deserializable")
  void shouldReturnEmptyWhenStoredValueIsCorrupt() {
    // Arrange
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn("esto-no-es-json");

    // Act
    final Optional<UserView> result = adapter.get(USER_ID);

    // Assert
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("put() y evict() no lanzan cuando Valkey no responde")
  void shouldSwallowWriteFailures() {
    // Arrange
    when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("caido"));

    // Act & Assert
    assertDoesNotThrow(() -> adapter.put(USER_ID, userView()));
  }

  @Test
  @DisplayName("evict() no lanza cuando Valkey no responde")
  void shouldSwallowEvictFailures() {
    // Arrange
    when(redisTemplate.delete(KEY)).thenThrow(new RedisConnectionFailureException("caido"));

    // Act & Assert
    assertDoesNotThrow(() -> adapter.evict(USER_ID));
  }

  private static UserView userView() {
    return new UserView(USER_ID, "Ada Lovelace", "ada@example.com", "ADMIN", "ACTIVE");
  }
}
