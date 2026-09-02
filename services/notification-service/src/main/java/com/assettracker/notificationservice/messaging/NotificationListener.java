package com.assettracker.notificationservice.messaging;

import com.assettracker.notificationservice.service.NotificationService;
import com.assettracker.notificationservice.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Consumes custody-change events off the queue and records them. */
@Component
public class NotificationListener {

  private final NotificationService service;

  public NotificationListener(NotificationService service) {
    this.service = service;
  }

  @RabbitListener(queues = "${messaging.queue}")
  public void onEvent(
      NotificationEvent event,
      @Header(name = CorrelationIdFilter.HEADER, required = false) String correlationId) {
    if (correlationId != null) {
      MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
    }
    try {
      service.record(event.clientId(), event.type(), event.message());
    } finally {
      MDC.remove(CorrelationIdFilter.MDC_KEY);
    }
  }
}
