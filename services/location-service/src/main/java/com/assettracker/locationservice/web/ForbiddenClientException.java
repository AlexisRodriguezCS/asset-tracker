package com.assettracker.locationservice.web;

/** The caller's grants (X-Client-Ids) do not cover the client the request targets. */
public class ForbiddenClientException extends RuntimeException {

  public ForbiddenClientException(Long clientId) {
    super("not permitted to act on client " + clientId);
  }
}
