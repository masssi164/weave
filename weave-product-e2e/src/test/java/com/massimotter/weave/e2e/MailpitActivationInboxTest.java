package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MailpitActivationInboxTest {
  private static final URI ISSUER = URI.create("https://auth.weave.test:5443/realms/weave");

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final MailpitActivationInbox inbox =
      new MailpitActivationInbox(null, null, ISSUER, Duration.ofSeconds(1));

  @Test
  void acceptsOnlyTheIssuerBoundKeycloakOrganizationRegistrationLink() {
    JsonNode message =
        mapper
            .createObjectNode()
            .put(
                "HTML",
                """
                <a href="https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations\
                ?response_type&#x3D;code&#x26;client_id=account&amp;token=one-time">Activate</a>
                """);

    URI result = inbox.actionLink(message);

    assertThat(result)
        .isEqualTo(
            URI.create(
                "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations"
                    + "?response_type=code&client_id=account&token=one-time"));
  }

  @Test
  void rejectsWrongIssuerPortPathAndMissingQuery() {
    assertThat(
            inbox.actionLink(
                message(
                    "https://attacker.example/realms/weave/protocol/openid-connect/registrations"
                        + "?response_type=code&client_id=account&token=one-time")))
        .isNull();
    assertThat(
            inbox.actionLink(
                message(
                    "https://auth.weave.test:6443/realms/weave/protocol/openid-connect/registrations"
                        + "?response_type=code&client_id=account&token=one-time")))
        .isNull();
    assertThat(
            inbox.actionLink(
                message(
                    "https://auth.weave.test:5443/realms/other/protocol/openid-connect/registrations"
                        + "?response_type=code&client_id=account&token=one-time")))
        .isNull();
    assertThat(
            inbox.actionLink(
                message(
                    "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations")))
        .isNull();
  }

  @Test
  void skipsMalformedCandidatesAndRequiresTheOfficialRegistrationParameters() {
    JsonNode message =
        mapper
            .createArrayNode()
            .add(
                "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations"
                    + "?response_type=code&client_id=account&token=bad%ZZ")
            .add(
                "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations"
                    + "?response_type=code&client_id=account&token=one-time");

    assertThat(inbox.actionLink(message))
        .isEqualTo(
            URI.create(
                "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations"
                    + "?response_type=code&client_id=account&token=one-time"));
    assertThat(
            inbox.actionLink(
                message(
                    "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations"
                        + "?client_id=account&token=one-time")))
        .isNull();
  }

  @Test
  void acceptsOnlyTheIssuerBoundKeycloakEmailVerificationActionToken() {
    JsonNode message =
        mapper
            .createObjectNode()
            .put(
                "HTML",
                """
                <a href="https://auth.weave.test:5443/realms/weave/login-actions/action-token\
                ?key&#x3D;one-time&#x26;client_id=account&amp;tab_id=session">Verify</a>
                """);

    assertThat(inbox.emailVerificationLink(message))
        .isEqualTo(
            URI.create(
                "https://auth.weave.test:5443/realms/weave/login-actions/action-token"
                    + "?key=one-time&client_id=account&tab_id=session"));
    assertThat(
            inbox.emailVerificationLink(
                message(
                    "https://attacker.example/realms/weave/login-actions/action-token"
                        + "?key=one-time&client_id=account")))
        .isNull();
    assertThat(
            inbox.emailVerificationLink(
                message(
                    "https://auth.weave.test:5443/realms/weave/login-actions/action-token"
                        + "?key=one-time&client_id=other")))
        .isNull();
    assertThat(
            inbox.emailVerificationLink(
                message(
                    "https://auth.weave.test:5443/realms/weave/login-actions/action-token"
                        + "?client_id=account")))
        .isNull();
  }

  private JsonNode message(String link) {
    return mapper.createObjectNode().put("Text", link);
  }
}
