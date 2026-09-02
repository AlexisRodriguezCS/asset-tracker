package com.assettracker.apigateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code security.*} configuration for token validation and CORS. */
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

  private Jwt jwt = new Jwt();
  private Entra entra = new Entra();
  private Cors cors = new Cors();

  public Jwt getJwt() {
    return jwt;
  }

  public void setJwt(Jwt jwt) {
    this.jwt = jwt;
  }

  public Entra getEntra() {
    return entra;
  }

  public void setEntra(Entra entra) {
    this.entra = entra;
  }

  public Cors getCors() {
    return cors;
  }

  public void setCors(Cors cors) {
    this.cors = cors;
  }

  /** Local (auth-service) token settings. */
  public static class Jwt {
    private Local local = new Local();

    public Local getLocal() {
      return local;
    }

    public void setLocal(Local local) {
      this.local = local;
    }

    /** RS256 issuer + the JWK Set URI to fetch the public key from. */
    public static class Local {
      private String issuer;
      private String jwkSetUri;

      public String getIssuer() {
        return issuer;
      }

      public void setIssuer(String issuer) {
        this.issuer = issuer;
      }

      public String getJwkSetUri() {
        return jwkSetUri;
      }

      public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
      }
    }
  }

  /** Microsoft Entra ID (optional). */
  public static class Entra {
    private String issuerUri;

    public String getIssuerUri() {
      return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
      this.issuerUri = issuerUri;
    }
  }

  /** Browser CORS settings for the future web client. */
  public static class Cors {
    private List<String> allowedOrigins = List.of();

    public List<String> getAllowedOrigins() {
      return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
      this.allowedOrigins = allowedOrigins;
    }
  }
}
