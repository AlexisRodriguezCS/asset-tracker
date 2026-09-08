package com.assettracker.locationservice.web.dto;

/**
 * Request body for correcting a location.
 *
 * <p>Null means "leave this alone", so fixing a label cannot blank a building. Two fields are
 * deliberately absent: the client, because moving a desk between tenants would strand the gear on
 * it, and the kind, because a desk that becomes a site is not a correction - it is a different
 * thing, and the map groups by it.
 */
public record UpdateLocationRequest(String label, String qrTag, String building, String floor) {}
