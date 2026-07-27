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
                ?client_id=weave-app&amp;token=one-time">Activate</a>
                """);

    URI result = inbox.actionLink(message);

    assertThat(result)
        .isEqualTo(
            URI.create(
                "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations"
                    + "?client_id=weave-app&token=one-time"));
  }

  @Test
  void rejectsWrongIssuerPortPathAndMissingQuery() {
    assertThat(
            inbox.actionLink(
                message(
                    "https://attacker.example/realms/weave/protocol/openid-connect/registrations"
                        + "?token=one-time")))
        .isNull();
    assertThat(
            inbox.actionLink(
                message(
                    "https://auth.weave.test:6443/realms/weave/protocol/openid-connect/registrations"
                        + "?token=one-time")))
        .isNull();
    assertThat(
            inbox.actionLink(
                message(
                    "https://auth.weave.test:5443/realms/other/protocol/openid-connect/registrations"
                        + "?token=one-time")))
        .isNull();
    assertThat(
            inbox.actionLink(
                message(
                    "https://auth.weave.test:5443/realms/weave/protocol/openid-connect/registrations")))
        .isNull();
  }

  private JsonNode message(String link) {
    return mapper.createObjectNode().put("Text", link);
  }
}
