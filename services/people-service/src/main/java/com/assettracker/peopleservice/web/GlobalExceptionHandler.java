package com.assettracker.peopleservice.web;

import com.assettracker.peopleservice.service.EmailTakenException;
import com.assettracker.peopleservice.service.PersonNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates domain and validation errors into {@link ApiError} responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(PersonNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(PersonNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(EmailTakenException.class)
  public ResponseEntity<ApiError> handleConflict(EmailTakenException ex) {
    return build(HttpStatus.CONFLICT, "EMAIL_TAKEN", ex.getMessage());
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
