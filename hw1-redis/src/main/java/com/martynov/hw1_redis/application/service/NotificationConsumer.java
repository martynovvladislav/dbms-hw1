package com.martynov.hw1_redis.application.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final String STREAM = "notifications";
    private static final String GROUP = "notifications-group";

    private static final Duration BLOCK_TIMEOUT =
            Duration.ofSeconds(2);

    private static final int BATCH_SIZE = 10;

    private final StringRedisTemplate redisTemplate;

    private volatile boolean running = true;

    private Thread consumerThread;

    private String consumerName;

    /**
     * Инициализация Redis Stream и Consumer Group
     */
    @PostConstruct
    public void init() {
        consumerName = createConsumerName();

        initializeStream();
        initializeConsumerGroup();

        startConsumer();

        log.info(
                "Notification consumer initialized: group={}, consumer={}",
                GROUP,
                consumerName
        );
    }

    /**
     * Graceful shutdown.
     */
    @PreDestroy
    public void shutdown() {
        running = false;

        if (consumerThread != null) {
            consumerThread.interrupt();
        }

        log.info("Notification consumer stopped: {}", consumerName);
    }

    /**
     * Создание stream, если его ещё нет.
     */
    private void initializeStream() {
        if (Boolean.TRUE.equals(
                redisTemplate.hasKey(STREAM)
        )) {
            return;
        }

        redisTemplate.opsForStream().add(
                STREAM,
                Map.of(
                        "type", "INIT",
                        "message", "stream initialized"
                )
        );

        log.info("Redis stream '{}' initialized", STREAM);
    }

    /**
     * Создание Consumer Group.
     */
    private void initializeConsumerGroup() {
        StreamInfo.XInfoGroups groups =
                redisTemplate
                        .opsForStream()
                        .groups(STREAM);

        boolean exists = groups.stream()
                .anyMatch(group ->
                        GROUP.equals(group.groupName())
                );

        if (exists) {
            log.info("Consumer group '{}' already exists", GROUP);
            return;
        }

        redisTemplate
                .opsForStream()
                .createGroup(
                        STREAM,
                        ReadOffset.from("0-0"),
                        GROUP
                );

        log.info("Consumer group '{}' created", GROUP);
    }

    /**
     * Запуск consumer thread
     */
    private void startConsumer() {
        consumerThread = new Thread(
                this::consumeLoop,
                "notification-consumer-" + consumerName
        );

        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    /**
     * Основной цикл чтения Stream
     */
    private void consumeLoop() {
        log.info("Notification consumer started: {}", consumerName);

        while (running &&
                !Thread.currentThread().isInterrupted()) {

            try {
                List<MapRecord<String, Object, Object>> records =
                        redisTemplate
                                .opsForStream()
                                .read(
                                        Consumer.from(GROUP, consumerName),
                                        StreamReadOptions
                                                .empty()
                                                .count(BATCH_SIZE)
                                                .block(BLOCK_TIMEOUT),
                                        StreamOffset.create(
                                                STREAM,
                                                ReadOffset.lastConsumed()
                                        )
                                );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                processRecords(records);

            } catch (Exception e) {
                if (!running) {
                    break;
                }

                log.error("Error while reading notification stream", e);

                sleepAfterError();
            }
        }

        log.info("Notification consumer loop stopped: {}", consumerName);
    }

    /**
     * Обработка batch сообщений
     */
    private void processRecords(
            List<MapRecord<String, Object, Object>> records
    ) {
        for (MapRecord<String, Object, Object> record : records) {

            try {
                log.info("Notification received: id={}, body={}", record.getId(), record.getValue());

                processNotification(record);

                /*
                 * ACK отправляем только после успешной обработки.
                 */
                redisTemplate
                        .opsForStream()
                        .acknowledge(
                                STREAM,
                                GROUP,
                                record.getId()
                        );

                log.debug(
                        "Notification ACKed: {}",
                        record.getId()
                );

            } catch (Exception e) {
                log.error("Failed to process notification: {}", record.getId(), e);
            }
        }
    }

    private void processNotification(
            MapRecord<String, Object, Object> record
    ) {
        Map<Object, Object> body = record.getValue();

        String type = String.valueOf(
                body.get("type")
        );

        String playerId = String.valueOf(
                body.get("player_id")
        );

        String message = String.valueOf(
                body.get("message")
        );

        log.info("Processing notification: type={}, playerId={}, message={}", type, playerId, message);
    }

    private void sleepAfterError() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private String createConsumerName() {
        return "consumer-" +
                java.util.UUID.randomUUID();
    }
}