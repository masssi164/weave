package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class McpJwtAudienceValidatorTest {
  private static final String RESOURCE = "https://api.weave.test/mcp";
  private static final String EXCHANGE_CLIENT = "weave-mcp-server";

  @Test
  void acceptsOnlyTheManifestedResourceAndExchangeClientAudienceSet() {
    var validator =
        McpSecurityConfiguration.exactAudienceValidator(Set.of(RESOURCE, EXCHANGE_CLIENT));

    assertThat(validator.validate(token(List.of(RESOURCE, EXCHANGE_CLIENT))).hasErrors()).isFalse();
    assertThat(validator.validate(token(List.of(RESOURCE))).hasErrors()).isTrue();
    assertThat(
            validator
                .validate(token(List.of(RESOURCE, EXCHANGE_CLIENT, "unexpected-audience")))
                .hasErrors())
        .isTrue();
  }

  private static Jwt token(List<String> audiences) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("test-token")
        .header("alg", "RS256")
        .subject("service-account-cell-subject")
        .audience(audiences)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(30))
        .build();
  }
}
