package com.martynov.hw1_redis.api.controllers;

import com.martynov.hw1_redis.application.dto.player.BatchPlayerDto;
import com.martynov.hw1_redis.application.dto.player.BatchResponse;
import com.martynov.hw1_redis.application.dto.player.LevelUpdateDto;
import com.martynov.hw1_redis.application.dto.player.PlayerDto;
import com.martynov.hw1_redis.application.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    /**
     * Массовое создание профилей через pipeline.
     */
    @PostMapping("/batch")
    public BatchResponse batchCreate(@RequestBody List<BatchPlayerDto> profiles) {
        return playerService.batchCreateProfiles(profiles);
    }

    /**
     * Создать/обновить профиль.
     */
    @PostMapping("/{id}")
    public void saveProfile(
            @PathVariable String id,
            @RequestBody PlayerDto dto
    ) {
        playerService.saveProfile(id, dto);
    }

    /**
     * Получить профиль (с кешем).
     */
    @GetMapping("/{id}")
    public PlayerDto getProfile(@PathVariable String id) {
        PlayerDto dto = playerService.getProfile(id);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found");
        }
        return dto;
    }

    /**
     * Обновить уровень + уведомление в Stream.
     */
    @PatchMapping("/{id}/level")
    public Long updateLevel(
            @PathVariable String id,
            @RequestBody LevelUpdateDto dto
    ) {
        return playerService.incrementLevel(id, dto.getDelta() == null ? 1L : dto.getDelta());
    }

    /**
     * Зафиксировать вход (Lua: INCR + EXPIRE).
     */
    @PostMapping("/{id}/login")
    public Long login(@PathVariable String id) {
        return playerService.recordLogin(id);
    }
}