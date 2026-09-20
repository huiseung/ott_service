package com.domain.backend.config;

import com.domain.backend.video.application.UploadException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UploadException.class)
    ResponseEntity<ApiError> handleUploadException(UploadException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ApiError(Instant.now(), e.getStatus().value(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), "Invalid request"));
    }

    record ApiError(Instant timestamp, int status, String message) {
    }
}
