package com.assettracker.notificationservice.messaging;

import static org.mockito.Mockito.verify;

import com.assettracker.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

  @Mock NotificationService service;

  @Test
  void anEventIsRecorded() {
    NotificationListener listener = new NotificationListener(service);

    listener.onEvent(
        new NotificationEvent(1L, "ASSET_CHECKED_OUT", "Asset 40 checked out"), "abc123def456");

    verify(service).record(1L, "ASSET_CHECKED_OUT", "Asset 40 checked out");
  }

  @Test
  void aMissingCorrelationHeaderIsTolerated() {
    NotificationListener listener = new NotificationListener(service);

    listener.onEvent(new NotificationEvent(2L, "ASSET_RETURNED", "Asset 7 back"), null);

    verify(service).record(2L, "ASSET_RETURNED", "Asset 7 back");
  }
}
