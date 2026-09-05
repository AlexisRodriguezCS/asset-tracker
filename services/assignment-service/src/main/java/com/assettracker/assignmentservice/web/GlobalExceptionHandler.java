package com.assettracker.assignmentservice.web;

import com.assettracker.assignmentservice.events.EventRequestNotFoundException;
import com.assettracker.assignmentservice.events.EventRequestStateException;
import com.assettracker.assignmentservice.service.AssetNotMovableException;
import com.assettracker.assignmentservice.service.AssetServiceUnavailableException;
import com.assettracker.assignmentservice.service.AssetUnavailableException;
import com.assettracker.assignmentservice.service.AssignmentNotFoundException;
import com.assettracker.assignmentservice.service.NoOpenAssignmentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates orchestration failures into {@link ApiError} responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AssignmentNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(AssignmentNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler({AssetUnavailableException.class, NoOpenAssignmentException.class})
  public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
    return build(HttpStatus.CONFLICT, "ASSET_UNAVAILABLE", ex.getMessage());
  }

  @ExceptionHandler(AssetNotMovableException.class)
  public ResponseEntity<ApiError> handleUnprocessable(AssetNotMovableException ex) {
    return build(HttpStatus.UNPROCESSABLE_ENTITY, "ASSET_NOT_MOVABLE", ex.getMessage());
  }

  @ExceptionHandler(AssetServiceUnavailableException.class)
  public ResponseEntity<ApiError> handleUnavailable(AssetServiceUnavailableException ex) {
    return build(HttpStatus.SERVICE_UNAVAILABLE, "ASSET_SERVICE_UNAVAILABLE", ex.getMessage());
  }

  @ExceptionHandler(ForbiddenClientException.class)
  public ResponseEntity<ApiError> handleForbiddenClient(ForbiddenClientException ex) {
    return build(HttpStatus.FORBIDDEN, "CLIENT_FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler(ForbiddenRoleException.class)
  public ResponseEntity<ApiError> handleForbiddenRole(ForbiddenRoleException ex) {
    return build(HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler(EventRequestNotFoundException.class)
  public ResponseEntity<ApiError> handleEventRequestNotFound(EventRequestNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "EVENT_REQUEST_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(EventRequestStateException.class)
  public ResponseEntity<ApiError> handleEventRequestState(EventRequestStateException ex) {
    return build(HttpStatus.CONFLICT, "EVENT_REQUEST_STATE", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
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
