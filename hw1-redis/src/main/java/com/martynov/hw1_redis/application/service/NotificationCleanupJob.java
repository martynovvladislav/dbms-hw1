package com.martynov.hw1_redis.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XTrimOptions;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {

    private static final String STREAM = "notifications";
    private static final Duration RETENTION = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    /**
     * Удаляет сообщения старше семи дней по времени их добавления в Stream.
     */
    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void deleteOldNotifications() {
        long cutoff = System.currentTimeMillis() - RETENTION.toMillis();
        XTrimOptions options = XTrimOptions.trim(
                TrimOptions.minId(RecordId.of(cutoff + "-0")).exact()
        );

        Long removed = redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.streamCommands().xTrim(
                        STREAM.getBytes(StandardCharsets.UTF_8), options
                )
        );

        if (removed != null && removed > 0) {
            log.info("Deleted {} old notifications (7 days TTL)", removed);
        }
    }
}
