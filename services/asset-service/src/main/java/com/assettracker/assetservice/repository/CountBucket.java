package com.assettracker.assetservice.repository;

/**
 * One row of a grouped count: the bucket's name and how many fell into it.
 *
 * <p>Shared by the catalog and the audit trail, which both answer "how many of each" for the
 * console's rollups. A projection interface rather than a record because Spring Data builds the
 * implementation from the query's aliases.
 */
public interface CountBucket {

  String getBucket();

  long getTotal();
}
