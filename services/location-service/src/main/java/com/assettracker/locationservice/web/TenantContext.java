package com.assettracker.locationservice.web;

import java.util.Set;

/**
 * The set of client (tenant) ids the current caller may act on, taken from the gateway-issued
 * {@code X-Client-Ids} header. {@code null} means no scoping was supplied - a direct or
 * service-to-service call - which is allowed; an empty set means the caller holds no grants.
 */
public final class TenantContext {

  private static final ThreadLocal<Set<Long>> CLIENT_IDS = new ThreadLocal<>();

  private TenantContext() {}

  static void set(Set<Long> clientIds) {
    CLIENT_IDS.set(clientIds);
  }

  static void clear() {
    CLIENT_IDS.remove();
  }

  public static Set<Long> clientIds() {
    return CLIENT_IDS.get();
  }

  /**
   * True when the caller may act on {@code clientId} (or when the request was not tenant-scoped).
   */
  public static boolean allows(Long clientId) {
    Set<Long> ids = CLIENT_IDS.get();
    return ids == null || (clientId != null && ids.contains(clientId));
  }

  /**
   * Enforces {@link #allows(Long)}; throws {@link ForbiddenClientException} (HTTP 403) otherwise.
   */
  public static void requireAllowed(Long clientId) {
    if (!allows(clientId)) {
      throw new ForbiddenClientException(clientId);
    }
  }
}
