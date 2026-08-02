package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  void retriesOnlyTheFirstTimeoutThatNeverLeftTheBlankPage() {
    assertThat(OidcBrowserJourney.shouldRetryBlankNavigation("timeout", "blank", null, 1))
        .isTrue();
    assertThat(OidcBrowserJourney.shouldRetryBlankNavigation("timeout", "blank", null, 2))
        .isFalse();
    assertThat(OidcBrowserJourney.shouldRetryBlankNavigation("dns", "blank", null, 1))
        .isFalse();
    assertThat(OidcBrowserJourney.shouldRetryBlankNavigation("timeout", "issuer-no-form", null, 1))
        .isFalse();
    assertThat(OidcBrowserJourney.shouldRetryBlankNavigation("timeout", "blank", 503, 1))
        .isFalse();
  }

  @Test
  void keepsAuthorizationStageDiagnosticsSupportSafe() {
    assertThat(OidcBrowserJourney.authorizationOperation("owner-post-collaboration-restart"))
        .isEqualTo("oidc-owner-post-collaboration-restart");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> OidcBrowserJourney.authorizationOperation("owner@example.invalid"));
  }

  @Test
  void executesExactlyOneProbedRetryForTheBlankNoResponseTimeout() {
    List<Integer> attempts = new ArrayList<>();
    AtomicInteger probes = new AtomicInteger();

    OidcBrowserJourney.executeBoundedNavigation(
        attempt -> {
          attempts.add(attempt);
          if (attempt == 1) {
            return OidcBrowserJourney.NavigationOutcome.failed(
                "timeout",
                "blank",
                null,
                new ProductFlowException("first navigation failed"));
          }
          return OidcBrowserJourney.NavigationOutcome.passed();
        },
        probes::incrementAndGet);

    assertThat(attempts).containsExactly(1, 2);
    assertThat(probes).hasValue(1);
  }

  @Test
  void issuerProbeFailurePreventsTheSecondNavigationAttempt() {
    for (String category : List.of("discovery-5xx", "transport", "issuer-mismatch")) {
      AtomicInteger attempts = new AtomicInteger();
      assertThatThrownBy(
          () ->
              OidcBrowserJourney.executeBoundedNavigation(
                  attempt -> {
                    attempts.incrementAndGet();
                    return OidcBrowserJourney.NavigationOutcome.failed(
                        "timeout",
                        "blank",
                        null,
                        new ProductFlowException("navigation failed"));
                  },
                  () -> {
                    throw new ProductFlowException("issuer probe failed category=" + category);
                  }))
          .isInstanceOf(ProductFlowException.class);
      assertThat(attempts).hasValue(1);
    }
  }

  @Test
  void secondFailureNamesOnlyTheSupportSafeStageAndAttempt() {
    AtomicInteger attempts = new AtomicInteger();
    assertThatThrownBy(
            () ->
                OidcBrowserJourney.executeBoundedNavigation(
                    attempt -> {
                      attempts.incrementAndGet();
                      String message =
                          OidcBrowserJourney.navigationFailureMessage(
                              "oidc-owner-post-collaboration-restart",
                              attempt,
                              "timeout",
                              "blank",
                              null,
                              "none");
                      return OidcBrowserJourney.NavigationOutcome.failed(
                          "timeout",
                          "blank",
                          null,
                          new ProductFlowException(message));
                    },
                    () -> {}))
        .isInstanceOf(ProductFlowException.class)
        .hasMessageContaining("operation=oidc-owner-post-collaboration-restart")
        .hasMessageContaining("attempt=2")
        .hasMessageContaining("issuerResponse=none")
        .hasMessageNotContaining("https://")
        .hasMessageNotContaining("code=")
        .hasMessageNotContaining("action");

    assertThat(attempts).hasValue(2);
  }
}
