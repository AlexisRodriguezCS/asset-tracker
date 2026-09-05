package com.assettracker.assignmentservice.web;

/** The caller's role does not permit this action (HTTP 403). */
public class ForbiddenRoleException extends RuntimeException {

  public ForbiddenRoleException(String role, String action) {
    super("role " + (role == null ? "none" : role) + " may not " + action);
  }
}
