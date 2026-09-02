package com.assettracker.authservice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * "Sign in with Microsoft 365" settings. All optional - a blank {@code issuer-uri} or {@code
 * client-id} turns the feature off and {@code POST /auth/microsoft} answers 501.
 */
@ConfigurationProperties(prefix = "security.entra")
public class EntraProperties {

  /** e.g. https://login.microsoftonline.com/&lt;tenant-id&gt;/v2.0 */
  private String issuerUri = "";

  /** The Entra app registration's Application (client) ID - the token audience. */
  private String clientId = "";

  /**
   * Client (tenant) ids a first-seen Microsoft user is granted. A stand-in for a real group -&gt;
   * client mapping; for the demo it defaults to all three seeded clients.
   */
  private List<Long> defaultClientIds = List.of();

  public boolean isConfigured() {
    return issuerUri != null && !issuerUri.isBlank() && clientId != null && !clientId.isBlank();
  }

  public String getIssuerUri() {
    return issuerUri;
  }

  public void setIssuerUri(String issuerUri) {
    this.issuerUri = issuerUri;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public List<Long> getDefaultClientIds() {
    return defaultClientIds;
  }

  public void setDefaultClientIds(List<Long> defaultClientIds) {
    this.defaultClientIds = defaultClientIds;
  }
}
