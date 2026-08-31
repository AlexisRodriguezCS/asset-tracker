package com.assettracker.notificationservice.service;

import com.assettracker.notificationservice.domain.Notification;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * In-memory notification store. A real implementation would consume domain events from a broker and
 * dispatch email / chat; here it records and logs them so the assignment flow is observable.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final List<Notification> notifications = new CopyOnWriteArrayList<>();

  public Notification record(Long clientId, String type, String message) {
    Notification notification = Notification.create(clientId, type, message);
    notifications.add(notification);
    log.info("notification [{}] client={} : {}", type, clientId, message);
    return notification;
  }

  public List<Notification> forClient(Long clientId) {
    return notifications.stream().filter(n -> n.clientId().equals(clientId)).toList();
  }

  public List<Notification> all() {
    return List.copyOf(notifications);
  }
}
