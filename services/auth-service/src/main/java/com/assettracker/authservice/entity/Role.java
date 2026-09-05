package com.assettracker.authservice.entity;

/**
 * What a signed-in user may do.
 *
 * <ul>
 *   <li>{@link #ADMIN} - platform operator; every tenant, every action.
 *   <li>{@link #TECH} - IT staff ("admin (tech)"): full asset operations - create, edit, import,
 *       check out, check in, offboard - across the tenants granted to them.
 *   <li>{@link #POC} - a customer's point of contact ("admin (POC)"): sees their own organisation's
 *       people, assets and event requests, and approves or denies those requests. Does not create
 *       or edit assets; that stays with {@link #TECH}.
 *   <li>{@link #HR} - offboarding collection for their own organisation.
 *   <li>{@link #USER} - an ordinary employee: sees only what is assigned to them, and raises event
 *       sign-out requests.
 * </ul>
 */
public enum Role {
  ADMIN,
  TECH,
  POC,
  HR,
  USER;

  /** True for the roles that may approve or deny an event sign-out request. */
  public boolean canApproveRequests() {
    return this == ADMIN || this == TECH || this == POC;
  }

  /** True for the roles that may create, edit and hand out assets. */
  public boolean canOperateAssets() {
    return this == ADMIN || this == TECH;
  }

  /** True for the roles that see the whole tenant rather than only their own gear. */
  public boolean seesWholeTenant() {
    return this != USER;
  }
}
