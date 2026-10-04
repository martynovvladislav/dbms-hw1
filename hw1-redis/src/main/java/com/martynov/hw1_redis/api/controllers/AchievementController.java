package com.martynov.hw1_redis.api.controllers;

import com.martynov.hw1_redis.application.dto.achievement.AchievementDto;
import com.martynov.hw1_redis.application.dto.achievement.CommonAchievementsResponse;
import com.martynov.hw1_redis.application.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    /**
     * Добавить достижение
     */
    @PostMapping("/{id}/achievements")
    public boolean addAchievement(
            @PathVariable String id,
            @RequestBody AchievementDto dto
    ) {
        return achievementService.addAchievement(id, dto.getAchievementName());
    }

    /**
     * Проверить есть ли достижение
     */
    @GetMapping("/{id}/achievements/{name}")
    public boolean hasAchievement(
            @PathVariable String id,
            @PathVariable String name
    ) {
        return achievementService.hasAchievement(id, name);
    }

    /**
     * Получить общие достижения игроков
     */
    @GetMapping("/{id1}/achievements/common/{id2}")
    public CommonAchievementsResponse commonAchievements(
            @PathVariable String id1,
            @PathVariable String id2
    ) {
        return achievementService.commonAchievements(id1, id2);
    }
}