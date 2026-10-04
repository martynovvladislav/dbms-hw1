package com.martynov.hw1_redis.application.service;

import com.martynov.hw1_redis.application.dto.leaderboard.RankResponse;
import com.martynov.hw1_redis.application.dto.leaderboard.ScoreDto;
import com.martynov.hw1_redis.application.dto.leaderboard.TopPlayerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final String LEADERBOARD_KEY = "tournament:main";

    private final StringRedisTemplate redisTemplate;

    /**
     * Добавляет/изменяет количество очков игрока
     */
    public ScoreDto addScore(
            String playerId,
            double delta
    ) {
        Double newScore = redisTemplate
                .opsForZSet()
                .incrementScore(
                        LEADERBOARD_KEY,
                        playerId,
                        delta
                );

        log.info("Score updated: player={}, delta={}, newScore={}", playerId, delta, newScore);

        return new ScoreDto(playerId, newScore);
    }

    /**
     * Получение топ-N игроков
     */
    public List<TopPlayerResponse> getTop(int limit) {
        Set<ZSetOperations.TypedTuple<String>> top =
                redisTemplate
                        .opsForZSet()
                        .reverseRangeWithScores(
                                LEADERBOARD_KEY,
                                0,
                                limit - 1L
                        );

        if (top == null || top.isEmpty()) {
            return Collections.emptyList();
        }

        List<TopPlayerResponse> result = new ArrayList<>(top.size());

        int rank = 1;

        for (ZSetOperations.TypedTuple<String> entry : top) {
            result.add(
                    new TopPlayerResponse(rank++, entry.getValue(), entry.getScore())
            );
        }

        return result;
    }

    /**
     * Получение текущего места игрока.
     * Если игрок отсутствует, возвращаем -1.
     */
    public RankResponse getRank(String playerId) {
        Long rank = redisTemplate
                .opsForZSet()
                .reverseRank(
                        LEADERBOARD_KEY,
                        playerId
                );

        long result = rank == null ? -1L : rank + 1;

        return new RankResponse(playerId, result);
    }
}
