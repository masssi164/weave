package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class OidcBrowserJourneyTest {

  @Test
  void classifiesBrowserFailuresWithoutRetainingTheirSensitiveMessage() {
    assertThat(
            OidcBrowserJourney.browserFailureCategory(
                "page.navigate: net::ERR_CERT_AUTHORITY_INVALID at https://secret.example"))
        .isEqualTo("tls");
    assertThat(
            OidcBrowserJourney.browserFailureCategory(
                "page.navigate: net::ERR_NAME_NOT_RESOLVED at https://secret.example"))
        .isEqualTo("dns");
    assertThat(
            OidcBrowserJourney.browserFailureCategory(
                "page.navigate: net::ERR_ABORTED at https://secret.example"))
        .isEqualTo("navigation-aborted");
    assertThat(OidcBrowserJourney.browserFailureCategory("Timeout 30000ms exceeded"))
        .isEqualTo("timeout");
    assertThat(OidcBrowserJourney.browserFailureCategory("opaque failure"))
        .isEqualTo("playwright");
  }

  @Test
  void recognizesOnlyRealmPagesOnTheExactIssuerOrigin() {
    URI issuer = URI.create("https://auth.weave.test:5443/realms/weave");

    assertThat(
            OidcBrowserJourney.isIssuerPage(
                "https://auth.weave.test:5443/realms/weave/login-actions/registration", issuer))
        .isTrue();
    assertThat(
            OidcBrowserJourney.isIssuerPage(
                "https://auth.weave.test:6443/realms/weave/login-actions/registration", issuer))
        .isFalse();
    assertThat(
            OidcBrowserJourney.isIssuerPage(
                "https://attacker.example:5443/realms/weave/login-actions/registration", issuer))
        .isFalse();
    assertThat(
            OidcBrowserJourney.isIssuerPage("com.massimotter.weave:/oauthredirect", issuer))
        .isFalse();
    assertThat(OidcBrowserJourney.isIssuerPage("about:blank", issuer)).isFalse();
  }

  @Test
  void reducesIssuerResponsesToSupportSafeStatusClasses() {
    assertThat(OidcBrowserJourney.statusClass(null)).isEqualTo("none");
    assertThat(OidcBrowserJourney.statusClass(204)).isEqualTo("2xx");
    assertThat(OidcBrowserJourney.statusClass(302)).isEqualTo("3xx");
    assertThat(OidcBrowserJourney.statusClass(400)).isEqualTo("4xx");
    assertThat(OidcBrowserJourney.statusClass(503)).isEqualTo("5xx");
    assertThat(OidcBrowserJourney.statusClass(700)).isEqualTo("other");
  }
}
