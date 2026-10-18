package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

class NativeFixtureIdentityTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private static final String ORGANIZATION = "8f771be4-f526-5bef-97dc-00c8e2fa383d";

  @Test
  void acceptsReplacementSessionForTheSameFixtureIdentity() {
    var old = session("weave-app", "fixture-subject", "expired-access", "expired-refresh");
    var fresh = session("weave-app", "fixture-subject", "new-access", "new-refresh");
    assertThatCode(() -> FreshProductFlow.requireNativeFixtureIdentity(old, fresh, claims("weave-app", "guest"), "guest"))
        .doesNotThrowAnyException();
    assertThatCode(() -> FreshProductFlow.requireNativeFixtureIdentity(
        session("weave-admin-console", "fixture-subject", "old-admin", "expired-admin-refresh"),
        session("weave-admin-console", "fixture-subject", "fresh-admin", "new-admin-refresh"),
        claims("weave-admin-console", "owner"), "owner")).doesNotThrowAnyException();
  }

  @Test
  void changedIdentityClientScopeRoleAndOrganizationCannotReachNativeProbes() {
    var expected = session("weave-app", "fixture-subject", "expired-access", "expired-refresh");
    var valid = session("weave-app", "fixture-subject", "new-access", "new-refresh");
    reject(expected, session("weave-app", "different-subject", "new-access", "new-refresh"), claims("weave-app", "guest"));
    reject(expected, session("weave-admin-console", "fixture-subject", "new-access", "new-refresh"), claims("weave-admin-console", "guest"));
    reject(expected, new OidcBrowserJourney.TokenSet("weave-app", "new-access", "new-refresh", "new-id",
        "fixture-subject", List.of("openid", "admin")), claims("weave-app", "guest"));
    ObjectNode wrongSubject = claims("weave-app", "guest"); wrongSubject.put("sub", "different-subject");
    reject(expected, valid, wrongSubject);
    ObjectNode wrongParty = claims("other-client", "guest"); reject(expected, valid, wrongParty);
    reject(expected, valid, claims("weave-app", "member"));
    ObjectNode ambiguous = claims("weave-app", "guest");
    ((ObjectNode) ambiguous.path("organization").path("weave-e2e").path("resource_access").path("weave-app"))
        .putArray("roles").add("guest").add("owner");
    reject(expected, valid, ambiguous);
    ObjectNode foreign = claims("weave-app", "guest");
    ((ObjectNode) foreign.path("organization").path("weave-e2e")).put("id", "foreign-organization");
    reject(expected, valid, foreign);
    ObjectNode absent = claims("weave-app", "guest"); absent.remove("organization"); reject(expected, valid, absent);
  }

  private void reject(OidcBrowserJourney.TokenSet expected, OidcBrowserJourney.TokenSet current, ObjectNode claims) {
    assertThatThrownBy(() -> FreshProductFlow.requireNativeFixtureIdentity(expected, current, claims, "guest"))
        .isInstanceOf(ProductFlowException.class).hasMessage("native fixture identity authority changed");
  }

  private OidcBrowserJourney.TokenSet session(String client, String subject, String access, String refresh) {
    return new OidcBrowserJourney.TokenSet(client, access, refresh, "fixture-id", subject,
        client.equals("weave-admin-console") ? List.of("openid", "profile", "email", "agent-runtime.admin")
            : List.of("openid", "profile", "email"));
  }

  private ObjectNode claims(String client, String role) {
    ObjectNode claims = mapper.createObjectNode(); claims.put("sub", "fixture-subject"); claims.put("azp", client);
    ObjectNode organization = claims.putObject("organization").putObject("weave-e2e"); organization.put("id", ORGANIZATION);
    organization.putObject("resource_access").putObject("weave-app").putArray("roles").add(role);
    return claims;
  }
}
