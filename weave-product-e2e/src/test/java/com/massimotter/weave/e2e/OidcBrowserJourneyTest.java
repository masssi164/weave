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
  void recognizesOnlyTheNativeVerifyEmailRequiredActionOnTheExactIssuer() {
    URI issuer = URI.create("https://auth.weave.test:5443/realms/weave");

    assertThat(
            OidcBrowserJourney.isEmailVerificationRequiredAction(
                "https://auth.weave.test:5443/realms/weave/login-actions/required-action"
                    + "?execution=VERIFY_EMAIL&client_id=account&tab_id=session",
                issuer))
        .isTrue();
    assertThat(
            OidcBrowserJourney.isEmailVerificationRequiredAction(
                "https://auth.weave.test:5443/realms/weave/login-actions/required-action"
                    + "?execution=UPDATE_PASSWORD&client_id=account&tab_id=session",
                issuer))
        .isFalse();
    assertThat(
            OidcBrowserJourney.isEmailVerificationRequiredAction(
                "https://attacker.example/realms/weave/login-actions/required-action"
                    + "?execution=VERIFY_EMAIL",
                issuer))
        .isFalse();
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

  @Test
  void classifiesRedirectTargetsWithoutRetainingTheirUrl() {
    String issuerResponse =
        "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations";

    assertThat(OidcBrowserJourney.redirectTargetClass(issuerResponse, null))
        .isEqualTo("missing");
    assertThat(
            OidcBrowserJourney.redirectTargetClass(
                issuerResponse, "/realms/weave/login-actions/registration?secret=one-time"))
        .isEqualTo("issuer-relative");
    assertThat(
            OidcBrowserJourney.redirectTargetClass(
                issuerResponse,
                "https://auth.weave.test:5443/realms/weave/login-actions/registration"
                    + "?secret=one-time"))
        .isEqualTo("issuer");
    assertThat(
            OidcBrowserJourney.redirectTargetClass(
                issuerResponse, "https://weave.test/after-activation?secret=one-time"))
        .isEqualTo("other-https");
    assertThat(
            OidcBrowserJourney.redirectTargetClass(
                issuerResponse, "com.massimotter.weave:/oauthredirect?secret=one-time"))
        .isEqualTo("custom-scheme");
  }
}
