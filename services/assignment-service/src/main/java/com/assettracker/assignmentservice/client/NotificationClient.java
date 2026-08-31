package com.assettracker.assignmentservice.client;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

/** Fire-and-forget notifications. Failures are logged, never propagated to the caller. */
@Component
public class NotificationClient {

  private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

  private final RestClient client;

  public NotificationClient(
      Builder loadBalancedRestClientBuilder,
      @Value("${downstream.notification-service}") String baseUrl) {
    this.client = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
  }

  public void send(Long clientId, String type, String message) {
    try {
      client
          .post()
          .uri("/notifications")
          .body(Map.of("clientId", clientId, "type", type, "message", message))
          .retrieve()
          .toBodilessEntity();
    } catch (RuntimeException ex) {
      log.warn("notification '{}' failed: {}", type, ex.getMessage());
    }
  }
}
