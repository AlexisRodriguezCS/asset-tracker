package com.assettracker.notificationservice.web;

import com.assettracker.notificationservice.domain.Notification;
import com.assettracker.notificationservice.service.NotificationService;
import com.assettracker.notificationservice.web.dto.NotificationRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints to record and read notifications. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Notification record(@Valid @RequestBody NotificationRequest request) {
    return service.record(request.clientId(), request.type(), request.message());
  }

  @GetMapping
  public List<Notification> list(@RequestParam(required = false) Long clientId) {
    return clientId == null ? service.all() : service.forClient(clientId);
  }
}
