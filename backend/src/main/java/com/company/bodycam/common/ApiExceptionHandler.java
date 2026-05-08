package com.company.bodycam.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException exception) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException exception) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleGeneric(Exception exception) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
