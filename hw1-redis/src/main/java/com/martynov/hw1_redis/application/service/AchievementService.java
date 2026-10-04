package com.martynov.hw1_redis.application.service;

import com.martynov.hw1_redis.application.dto.achievement.CommonAchievementsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    private static final String ACHIEVEMENTS_KEY = "achievements:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Добавление достижения игроку.
     */
    public boolean addAchievement(
            String playerId,
            String achievement
    ) {
        Long added = redisTemplate
                .opsForSet()
                .add(
                        achievementsKey(playerId),
                        achievement
                );

        boolean result =
                added != null && added > 0;

        log.info(
                "Achievement {} for player {}: {}",
                achievement,
                playerId,
                result ? "added" : "already exists"
        );

        return result;
    }

    /**
     * Проверка наличия достижения у игрока.
     */
    public boolean hasAchievement(
            String playerId,
            String achievement
    ) {
        Boolean has = redisTemplate
                .opsForSet()
                .isMember(
                        achievementsKey(playerId),
                        achievement
                );

        return Boolean.TRUE.equals(has);
    }

    /**
     * Возвращает достижения, которые есть
     * одновременно у двух игроков.
     */
    public CommonAchievementsResponse commonAchievements(
            String id1,
            String id2
    ) {
        Set<Object> common =
                redisTemplate
                        .opsForSet()
                        .intersect(
                                achievementsKey(id1),
                                achievementsKey(id2)
                        );

        Set<String> result =
                common == null || common.isEmpty()
                        ? Collections.emptySet()
                        : common.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.toSet());

        log.info("Found {} common achievements for players {} and {}", result.size(), id1, id2);

        return new CommonAchievementsResponse(id1, id2, result);
    }

    private String achievementsKey(String playerId) {
        return ACHIEVEMENTS_KEY + playerId;
    }
}
