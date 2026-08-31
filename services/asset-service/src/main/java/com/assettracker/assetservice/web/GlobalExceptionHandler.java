package com.assettracker.assetservice.web;

import com.assettracker.assetservice.entity.AlreadyAssignedException;
import com.assettracker.assetservice.service.AssetNotFoundException;
import com.assettracker.assetservice.service.AssetTagTakenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates domain and validation errors into {@link ApiError} responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AssetNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(AssetNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(AssetTagTakenException.class)
  public ResponseEntity<ApiError> handleTagTaken(AssetTagTakenException ex) {
    return build(HttpStatus.CONFLICT, "ASSET_TAG_TAKEN", ex.getMessage());
  }

  @ExceptionHandler(AlreadyAssignedException.class)
  public ResponseEntity<ApiError> handleAlreadyAssigned(AlreadyAssignedException ex) {
    return build(HttpStatus.CONFLICT, "ALREADY_ASSIGNED", ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiError> handleBadState(IllegalStateException ex) {
    return build(HttpStatus.UNPROCESSABLE_ENTITY, "ASSET_NOT_MOVABLE", ex.getMessage());
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
