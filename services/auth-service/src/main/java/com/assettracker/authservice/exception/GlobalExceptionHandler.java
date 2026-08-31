package com.assettracker.authservice.exception;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain, validation and security errors onto {@link ApiError} responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleConflict(IllegalArgumentException ex) {
    return build(HttpStatus.CONFLICT, "REGISTRATION_CONFLICT", ex.getMessage());
  }

  @ExceptionHandler({BadCredentialsException.class, JwtException.class})
  public ResponseEntity<ApiError> handleUnauthorized(RuntimeException ex) {
    return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + " " + f.getDefaultMessage())
            .orElse("Invalid request");
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
  }

  private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message));
  }
}
