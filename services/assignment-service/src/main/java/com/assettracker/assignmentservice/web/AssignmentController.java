package com.assettracker.assignmentservice.web;

import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.service.AssignmentService;
import com.assettracker.assignmentservice.web.dto.AssignmentResponse;
import com.assettracker.assignmentservice.web.dto.CheckOutRequest;
import com.assettracker.assignmentservice.web.dto.OffboardingResult;
import com.assettracker.assignmentservice.web.dto.TransferRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Check-out / check-in / transfer / offboard, plus assignment history. The acting tech's identity
 * comes from the {@code X-User-Id} header the gateway forwards after validating the JWT; it falls
 * back to {@code system} when called service-to-service.
 */
@RestController
@RequestMapping("/assignments")
public class AssignmentController {

  private final AssignmentService service;

  public AssignmentController(AssignmentService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AssignmentResponse checkOut(
      @Valid @RequestBody CheckOutRequest request,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
    return AssignmentResponse.from(service.checkOut(request, actor));
  }

  @PostMapping("/return")
  public AssignmentResponse checkIn(
      @RequestParam Long assetId,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
    return AssignmentResponse.from(service.checkIn(assetId, actor));
  }

  @PostMapping("/transfer")
  public AssignmentResponse transfer(
      @Valid @RequestBody TransferRequest request,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
    return AssignmentResponse.from(service.transfer(request, actor));
  }

  @PostMapping("/offboard")
  public OffboardingResult offboard(
      @RequestParam Long clientId,
      @RequestParam Long personId,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
    return service.offboardPerson(clientId, personId, actor);
  }

  @GetMapping("/{id}")
  public AssignmentResponse getById(@PathVariable Long id) {
    return AssignmentResponse.from(service.getById(id));
  }

  @GetMapping
  public List<AssignmentResponse> list(
      @RequestParam(required = false) Long clientId,
      @RequestParam(required = false) Long assetId,
      @RequestParam(required = false) Long personId) {
    List<Assignment> rows;
    if (assetId != null) {
      rows = service.byAsset(assetId);
    } else if (personId != null) {
      rows = service.openForPerson(personId);
    } else {
      rows = service.byClient(requireClientId(clientId));
    }
    return rows.stream().map(AssignmentResponse::from).toList();
  }

  private static Long requireClientId(Long clientId) {
    if (clientId == null) {
      throw new IllegalArgumentException(
          "clientId is required when no assetId or personId is given");
    }
    return clientId;
  }
}
