package com.assettracker.assignmentservice.web;

/**
 * Lets tests outside this package stand in as a particular caller. {@code CallerContext.set} is
 * package-private on purpose - only the filter should populate it in production - so this lives in
 * the same package under src/test rather than widening the real API.
 */
public final class CallerContextTestSupport {

  private CallerContextTestSupport() {}

  public static void as(String role, Long personId) {
    CallerContext.set(role, personId);
  }

  public static void reset() {
    CallerContext.clear();
  }

  public static void tenant(java.util.Set<Long> clientIds) {
    TenantContext.set(clientIds);
  }

  public static void clearTenant() {
    TenantContext.clear();
  }
}
