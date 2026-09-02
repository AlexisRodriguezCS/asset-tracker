package com.assettracker.notificationservice.messaging;

/**
 * The custody-change event published by assignment-service and consumed here. The producer side
 * declares the same record - a shared contract module is a later refactor.
 */
public record NotificationEvent(Long clientId, String type, String message) {}
