package com.martynov.hw1_redis.application.dto.player;

import lombok.Data;

@Data
public class LevelUpdateDto {
    private Long delta = 1L;
}