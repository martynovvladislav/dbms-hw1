package com.martynov.hw1_redis.application.service;

import com.martynov.hw1_redis.application.dto.player.BatchPlayerDto;
import com.martynov.hw1_redis.application.dto.player.BatchResponse;
import com.martynov.hw1_redis.application.dto.player.PlayerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private static final String PLAYER_KEY = "player:";
    private static final String CACHE_KEY = "cache:player:";
    private static final String LOGINS_KEY = "logins:";
    private static final String NOTIFICATIONS_STREAM = "notifications";

    private static final long CACHE_TTL_SECONDS = 60;
    private static final long LOGIN_TTL_SECONDS = 24 * 60 * 60;

    private static final String LOGIN_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return count
            """;

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Создание/обновление профиля
     */
    public void saveProfile(String id, PlayerDto dto) {

        String key = playerKey(id);

        Map<String, Object> fields = new HashMap<>();

        if (dto.getName() != null) {
            fields.put("name", dto.getName());
        }

        if (dto.getLevel() != null) {
            fields.put("level", dto.getLevel());
        }

        if (dto.getRegion() != null) {
            fields.put("region", dto.getRegion());
        }

        fields.put(
                "created_at",
                dto.getCreatedAt() != null
                        ? dto.getCreatedAt()
                        : System.currentTimeMillis()
        );

        redisTemplate.opsForHash().putAll(key, fields);

        invalidateCache(id);

        log.info("Profile saved: {}", id);
    }

    /**
     * Получение профиля с использованием Cache-Aside
     */
    public PlayerDto getProfile(String id) {
        String cacheKey = cacheKey(id);

        // 1. Cache
        String cached = stringRedisTemplate
                .opsForValue()
                .get(cacheKey);

        if (cached != null) {
            try {
                PlayerDto dto = objectMapper.readValue(cached, PlayerDto.class);

                log.info("Cache HIT: {}", id);

                return dto;
            } catch (Exception e) {
                log.warn("Invalid cached profile {}, removing cache", id, e);

                stringRedisTemplate.delete(cacheKey);
            }
        }

        // 2. Redis Hash
        log.info("Cache MISS: {}", id);

        Map<Object, Object> raw =
                redisTemplate
                        .opsForHash()
                        .entries(playerKey(id));

        if (raw.isEmpty()) {
            return null;
        }

        // 3. Redis Hash -> DTO
        PlayerDto dto = mapToPlayerDto(raw);

        // 4. DTO -> Cache
        cacheProfile(id, dto);

        return dto;
    }

    /**
     * Атомарное изменение уровня
     */
    public Long incrementLevel(String id, long delta) {

        Long newLevel = redisTemplate
                .opsForHash()
                .increment(
                        playerKey(id),
                        "level",
                        delta
                );

        invalidateCache(id);

        publishLevelNotification(
                id,
                delta,
                newLevel
        );

        log.info("Level updated: player={}, delta={}, newLevel={}", id, delta, newLevel);

        return newLevel;
    }

    /**
     * Фиксация входа игрока.
     */
    public Long recordLogin(String id) {
        Long count = stringRedisTemplate.execute(
                new DefaultRedisScript<>(
                        LOGIN_SCRIPT,
                        Long.class
                ),
                Collections.singletonList(loginKey(id)),
                String.valueOf(LOGIN_TTL_SECONDS)
        );

        log.info("Login recorded: player={}, count={}", id, count);

        return count;
    }

    /**
     * Массовое создание профилей через Redis pipeline.
     */
    public BatchResponse batchCreateProfiles(
            List<BatchPlayerDto> profiles
    ) {
        if (profiles == null || profiles.isEmpty()) {
            return new BatchResponse(0, 0L);
        }

        long start = System.currentTimeMillis();

        redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {

                    for (BatchPlayerDto dto : profiles) {
                        writeProfile(connection, dto);
                    }

                    return null;
                }
        );

        for (BatchPlayerDto dto : profiles) {
            invalidateCache(dto.getId());
        }

        long elapsed =
                System.currentTimeMillis() - start;

        log.info("Batch created {} profiles in {} ms", profiles.size(), elapsed);

        return new BatchResponse(profiles.size(), elapsed);
    }

    /**
     * Запись одного профиля внутри pipeline.
     */
    private void writeProfile(
            RedisConnection connection,
            BatchPlayerDto dto
    ) {
        Map<byte[], byte[]> fields = new HashMap<>();

        if (dto.getName() != null) {
            fields.put(
                    bytes("name"),
                    bytes(dto.getName())
            );
        }

        if (dto.getLevel() != null) {
            fields.put(
                    bytes("level"),
                    bytes(dto.getLevel().toString())
            );
        }

        if (dto.getRegion() != null) {
            fields.put(
                    bytes("region"),
                    bytes(dto.getRegion())
            );
        }

        long createdAt =
                dto.getCreatedAt() != null
                        ? dto.getCreatedAt()
                        : System.currentTimeMillis();

        fields.put(
                bytes("created_at"),
                bytes(String.valueOf(createdAt))
        );

        connection.hashCommands().hMSet(
                bytes(playerKey(dto.getId())),
                fields
        );
    }

    /**
     * Явный Redis Hash -> PlayerDto.
     */
    private PlayerDto mapToPlayerDto(
            Map<Object, Object> raw
    ) {
        PlayerDto dto = new PlayerDto();

        dto.setName(value(raw, "name"));

        String level = value(raw, "level");
        if (level != null) {
            dto.setLevel(Integer.valueOf(level));
        }

        dto.setRegion(value(raw, "region"));

        String createdAt = value(raw, "created_at");
        if (createdAt != null) {
            dto.setCreatedAt(Long.valueOf(createdAt));
        }

        return dto;
    }

    private String value(
            Map<Object, Object> map,
            String key
    ) {
        Object value = map.get(key);

        return value != null ? value.toString() : null;
    }

    /**
     * Запись профиля в cache.
     */
    private void cacheProfile(
            String id,
            PlayerDto dto
    ) {
        try {
            String json = objectMapper.writeValueAsString(dto);

            stringRedisTemplate
                    .opsForValue()
                    .set(
                            cacheKey(id),
                            json,
                            CACHE_TTL_SECONDS,
                            TimeUnit.SECONDS
                    );

        } catch (Exception e) {
            log.warn("Failed to cache profile: {}", id, e);
        }
    }

    private void invalidateCache(String id) {
        stringRedisTemplate.delete(cacheKey(id));
    }

    private void publishLevelNotification(
            String id,
            long delta,
            Long newLevel
    ) {
        Map<String, String> notification = Map.of(
                "player_id",
                id,

                "type",
                "LEVEL_UP",

                "message",
                "Level changed by "
                        + delta
                        + ", new level: "
                        + newLevel,

                "timestamp",
                String.valueOf(
                        System.currentTimeMillis()
                )
        );

        redisTemplate
                .opsForStream()
                .add(
                        NOTIFICATIONS_STREAM,
                        notification
                );
    }

    private String playerKey(String id) {
        return PLAYER_KEY + id;
    }

    private String cacheKey(String id) {
        return CACHE_KEY + id;
    }

    private String loginKey(String id) {
        return LOGINS_KEY + id;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}