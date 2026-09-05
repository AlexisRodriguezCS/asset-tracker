package com.assettracker.assetservice.web;

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
}
