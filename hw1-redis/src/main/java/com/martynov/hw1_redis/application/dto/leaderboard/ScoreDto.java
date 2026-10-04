package com.martynov.hw1_redis.application.dto.leaderboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoreDto {

    @JsonProperty("player_id")
    private String playerId;

    private Double score;
}