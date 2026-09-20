package com.domain.backend.user.config;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), "Invalid request"));
    }

    record ApiError(Instant timestamp, int status, String message) {
    }
}
