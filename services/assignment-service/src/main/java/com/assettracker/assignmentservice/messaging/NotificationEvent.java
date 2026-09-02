package com.assettracker.assignmentservice.messaging;

/**
 * Published to the events exchange when custody changes. notification-service consumes it. The same
 * shape is duplicated there - a shared contract module is a later refactor.
 */
public record NotificationEvent(Long clientId, String type, String message) {}
