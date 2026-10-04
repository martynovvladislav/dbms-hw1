package com.martynov.hw1_redis.application.dto.achievement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonAchievementsResponse {

    private String id1;
    private String id2;
    private Set<String> common;
}