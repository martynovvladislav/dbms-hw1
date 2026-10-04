package com.martynov.hw1_redis.application.dto.achievement;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AchievementDto {

    @JsonProperty("achievement_name")
    private String achievementName;
}