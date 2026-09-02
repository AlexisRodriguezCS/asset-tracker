package com.assettracker.authservice.service;

/** Thrown by {@code POST /auth/microsoft} when no Entra ID app registration is configured. */
public class MicrosoftAuthNotConfiguredException extends RuntimeException {

  public MicrosoftAuthNotConfiguredException() {
    super("Microsoft 365 sign-in is not configured on this deployment");
  }
}
