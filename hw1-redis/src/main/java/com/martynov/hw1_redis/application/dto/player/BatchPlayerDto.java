package com.martynov.hw1_redis.application.dto.player;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BatchPlayerDto {

    private String id;
    private String name;
    private Integer level;
    private String region;

    @JsonProperty("created_at")
    private Long createdAt;
}