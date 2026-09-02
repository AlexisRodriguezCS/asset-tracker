package com.assettracker.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assettracker.authservice.config.EntraProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The "not configured" path - the only branch reachable without a live Entra tenant. Token
 * validation is exercised end to end in the manual OIDC flow, not here.
 */
class MicrosoftAuthServiceTest {

  private MicrosoftAuthService service(EntraProperties props) {
    return new MicrosoftAuthService(props, null, null, null);
  }

  @Test
  void reportsNotConfiguredWhenIssuerOrClientIdIsBlank() {
    assertThat(service(new EntraProperties()).isConfigured()).isFalse();

    EntraProperties issuerOnly = new EntraProperties();
    issuerOnly.setIssuerUri("https://login.microsoftonline.com/tenant/v2.0");
    assertThat(service(issuerOnly).isConfigured()).isFalse();
  }

  @Test
  void reportsConfiguredWhenBothAreSet() {
    EntraProperties props = new EntraProperties();
    props.setIssuerUri("https://login.microsoftonline.com/tenant/v2.0");
    props.setClientId("00000000-0000-0000-0000-000000000000");
    props.setDefaultClientIds(List.of(1L, 2L, 3L));
    assertThat(service(props).isConfigured()).isTrue();
  }

  @Test
  void exchangeIsRejectedUntilConfigured() {
    assertThatThrownBy(() -> service(new EntraProperties()).exchange("any.jwt.token"))
        .isInstanceOf(MicrosoftAuthNotConfiguredException.class);
  }
}
