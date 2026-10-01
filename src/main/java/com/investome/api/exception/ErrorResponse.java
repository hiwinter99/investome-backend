package com.investome.api.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public record ErrorResponse(LocalDateTime timestamp, int status, String error, String code,
                            String message, String path) {
    public static ErrorResponse of(HttpStatus status, String code, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), code, message, path);
    }
}
