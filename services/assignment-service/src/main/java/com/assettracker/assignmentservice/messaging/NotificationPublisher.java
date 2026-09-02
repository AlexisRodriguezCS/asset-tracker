package com.assettracker.assignmentservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes a {@link NotificationEvent} to the events exchange instead of calling
 * notification-service over HTTP. Fire-and-forget: a broker outage is logged, never propagated to
 * the caller (the custody change has already committed).
 */
@Component
public class NotificationPublisher {

  private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

  private final RabbitTemplate rabbit;
  private final String exchange;

  public NotificationPublisher(
      RabbitTemplate rabbit, @Value("${messaging.exchange}") String exchange) {
    this.rabbit = rabbit;
    this.exchange = exchange;
  }

  public void publish(Long clientId, String type, String message) {
    try {
      rabbit.convertAndSend(
          exchange, routingKey(type), new NotificationEvent(clientId, type, message));
    } catch (RuntimeException ex) {
      log.warn("event '{}' not published: {}", type, ex.getMessage());
    }
  }

  /** ASSET_CHECKED_OUT -> assignment.asset-checked-out; notification-service binds assignment.# */
  private static String routingKey(String type) {
    return "assignment." + type.toLowerCase().replace('_', '-');
  }
}
