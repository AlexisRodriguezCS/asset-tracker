package com.assettracker.notificationservice.messaging;

import com.assettracker.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumes custody-change events off the queue and records them. */
@Component
public class NotificationListener {

  private final NotificationService service;

  public NotificationListener(NotificationService service) {
    this.service = service;
  }

  @RabbitListener(queues = "${messaging.queue}")
  public void onEvent(NotificationEvent event) {
    service.record(event.clientId(), event.type(), event.message());
  }
}
