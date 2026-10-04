package com.martynov.hw1_redis.application.dto.leaderboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RankResponse {

    @JsonProperty("player_id")
    private String playerId;

    private Long rank;
}