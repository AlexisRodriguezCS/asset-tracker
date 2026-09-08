package com.assettracker.peopleservice.web.dto;

/**
 * Counts for the directory's summary strip.
 *
 * <p>Exists for the same reason asset-service's does: the page used to fetch every person and count
 * them in the render, which is fine for five and a whole tenant over the wire for five thousand.
 * Counting belongs in the database.
 *
 * @param total everyone in the tenant
 * @param active still here
 * @param offboarding leaving, gear not yet all back
 * @param departed gone
 * @param withDesk how many have a desk, which is what makes the desk map worth opening
 */
public record PeopleStats(
    long total, long active, long offboarding, long departed, long withDesk) {}
