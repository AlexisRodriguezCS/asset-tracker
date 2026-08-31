package com.assettracker.locationservice.web;

import com.assettracker.locationservice.service.LocationNotFoundException;
import com.assettracker.locationservice.service.QrTagTakenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates domain and validation errors into {@link ApiError} responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(LocationNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(LocationNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(QrTagTakenException.class)
  public ResponseEntity<ApiError> handleConflict(QrTagTakenException ex) {
    return build(HttpStatus.CONFLICT, "QR_TAG_TAKEN", ex.getMessage());
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
