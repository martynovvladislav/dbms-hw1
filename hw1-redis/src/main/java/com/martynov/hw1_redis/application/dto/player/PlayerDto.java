package com.martynov.hw1_redis.application.dto.player;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PlayerDto {
    private String name;
    private Integer level;
    private String region;
    @JsonProperty("created_at")
    private Long createdAt;
}
