package com.assettracker.locationservice.web;

/**
 * Who is calling, from the gateway's {@code X-User-Role} / {@code X-Person-Id} headers.
 *
 * <p>Role is a plain string rather than an enum: the authoritative {@code Role} type lives in
 * auth-service, and copying it into every downstream service would couple them to that service's
 * release cycle for no gain. Only one distinction matters here - whether the caller is an ordinary
 * employee, who may see only their own gear, or staff, who see the whole tenant.
 */
public final class CallerContext {

  private static final String ROLE_USER = "USER";

  private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
  private static final ThreadLocal<Long> PERSON_ID = new ThreadLocal<>();

  private CallerContext() {}

  static void set(String role, Long personId) {
    ROLE.set(role);
    PERSON_ID.set(personId);
  }

  static void clear() {
    ROLE.remove();
    PERSON_ID.remove();
  }

  public static String role() {
    return ROLE.get();
  }

  /** The employee record behind this login, or null for staff and unscoped calls. */
  public static Long personId() {
    return PERSON_ID.get();
  }

  /** True when the caller is an ordinary employee, confined to what is assigned to them. */
  public static boolean isSelfServiceUser() {
    return ROLE_USER.equals(ROLE.get());
  }

  private static final java.util.Set<String> STAFF = java.util.Set.of("ADMIN", "TECH", "POC", "HR");
  private static final java.util.Set<String> ASSET_OPERATORS = java.util.Set.of("ADMIN", "TECH");
  private static final java.util.Set<String> COLLECTORS = java.util.Set.of("ADMIN", "TECH", "HR");

  /**
   * A null role means the request never passed the gateway - a direct call inside the network, or
   * one service calling another. Those are already authorized at the edge they came through;
   * verifying a signed principal inside the mesh is the RUNBOOK's "deferred" item.
   */
  private static boolean isOneOf(java.util.Set<String> allowed) {
    String role = ROLE.get();
    return role == null || allowed.contains(role);
  }

  /** Creating or changing assets, people and locations: tech or admin. */
  public static void requireAssetOperator() {
    if (!isOneOf(ASSET_OPERATORS)) {
      throw new ForbiddenRoleException(ROLE.get(), "change assets");
    }
  }

  /** Collecting gear back in - tech, admin, or HR doing an offboarding sweep. */
  public static void requireCollector() {
    if (!isOneOf(COLLECTORS)) {
      throw new ForbiddenRoleException(ROLE.get(), "collect or return assets");
    }
  }

  /** Anything an ordinary employee has no business doing. */
  public static void requireStaff() {
    if (!isOneOf(STAFF)) {
      throw new ForbiddenRoleException(ROLE.get(), "perform this action");
    }
  }
}
