package com.assettracker.assignmentservice.web;

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
  private static final java.util.Set<String> APPROVERS = java.util.Set.of("ADMIN", "TECH", "POC");
  private static final java.util.Set<String> ASSET_OPERATORS = java.util.Set.of("ADMIN", "TECH");
  private static final java.util.Set<String> COLLECTORS = java.util.Set.of("ADMIN", "TECH", "HR");

  private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
  private static final ThreadLocal<Long> PERSON_ID = new ThreadLocal<>();

  private CallerContext() {}

  /**
   * Sets the caller for the current thread. Public because the two things that legitimately
   * establish a caller live outside this package: the request filter, and a test exercising a
   * service directly - which now has to say who it is, since an absent role no longer passes.
   */
  public static void set(String role, Long personId) {
    ROLE.set(role);
    PERSON_ID.set(personId);
  }

  public static void clear() {
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

  /**
   * A role that is absent denies, rather than waving the caller through.
   *
   * <p>It used to mean "a call that did not come via the gateway" and was allowed: service to
   * service, or anything already inside the network. That made the weakest way in the easiest -
   * send no identity header at all and every role gate opened. The network boundary was doing the
   * authorizing, which is an assumption about the deployment rather than a control in the code.
   *
   * <p>The role now comes from a token this service verified (see {@code TenantFilter}), and an
   * internal call carries the caller's own token, so there is no legitimate request left that
   * arrives without one.
   */
  private static boolean isOneOf(java.util.Set<String> allowed) {
    String role = ROLE.get();
    return role != null && allowed.contains(role);
  }

  /** Approving or denying an event request: POC, tech or admin. */
  public static void requireApprover() {
    if (!isOneOf(APPROVERS)) {
      throw new ForbiddenRoleException(ROLE.get(), "approve or deny event requests");
    }
  }

  /** Anything that moves custody or edits the catalog: tech or admin. */
  public static void requireAssetOperator() {
    if (!isOneOf(ASSET_OPERATORS)) {
      throw new ForbiddenRoleException(ROLE.get(), "hand out assets");
    }
  }

  /** Collecting gear back in - tech, admin, or HR running an offboarding sweep. */
  public static void requireCollector() {
    if (!isOneOf(COLLECTORS)) {
      throw new ForbiddenRoleException(ROLE.get(), "collect or return assets");
    }
  }
}
