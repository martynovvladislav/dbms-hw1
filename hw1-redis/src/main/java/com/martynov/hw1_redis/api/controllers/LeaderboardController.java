package com.martynov.hw1_redis.api.controllers;

import com.martynov.hw1_redis.application.dto.leaderboard.RankResponse;
import com.martynov.hw1_redis.application.dto.leaderboard.ScoreDto;
import com.martynov.hw1_redis.application.dto.leaderboard.TopPlayerResponse;
import com.martynov.hw1_redis.application.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @PostMapping("/score")
    public ScoreDto addScore(@RequestBody ScoreDto dto) {
        return leaderboardService.addScore(dto.getPlayerId(), dto.getScore());
    }

    @GetMapping("/top")
    public List<TopPlayerResponse> getTop(@RequestParam(defaultValue = "10") int limit) {
        return leaderboardService.getTop(limit);
    }

    @GetMapping("/rank/{playerId}")
    public RankResponse getRank(@PathVariable String playerId) {
        return leaderboardService.getRank(playerId);
    }
}