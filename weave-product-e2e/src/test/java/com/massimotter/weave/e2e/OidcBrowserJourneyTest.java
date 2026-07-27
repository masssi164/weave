package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

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
}
