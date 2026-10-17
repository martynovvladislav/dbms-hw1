package com.martynov.hw1_redis.application.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Player not found")
public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException() {
        super("Player not found");
    }
}
