package com.martynov.hw1_redis.application.dto.player;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponse {

    private Integer count;
    private Long elapsedMs;
}