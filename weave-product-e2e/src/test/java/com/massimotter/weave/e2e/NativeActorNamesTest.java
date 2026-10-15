package com.massimotter.weave.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.massimotter.weave.userapi.model.ProductProfileResponse;
import org.junit.jupiter.api.Test;

final class NativeActorNamesTest {
  @Test
  void keepsAdmittedCanonicalTenantSeparateFromObservedNativeOrganization() {
    var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    String nativeId = "8f771be4-f526-5bef-97dc-00c8e2fa383d";
    var claims = mapper.readTree("{\"sub\":\"actor\",\"iss\":\"https://issuer.test\","
        + "\"organization\":{\"weave-e2e\":{\"id\":\"" + nativeId + "\"}}}");
    var admitted = new com.massimotter.weave.userapi.model.AuthenticatedUserResponse()
        .subject("actor").identityIssuer("https://issuer.test").organizationId("canonical-tenant");
    assertEquals(nativeId, FreshProductFlow.requireNativeOrganizationId(claims, admitted,
        "https://issuer.test", "actor", "canonical-tenant"));
    for (String wrong : new String[] {"other-tenant", nativeId}) {
      assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireNativeOrganizationId(
          claims, admitted, "https://issuer.test", "actor", wrong));
    }
    assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireNativeOrganizationId(
        claims, admitted, "https://other.test", "actor", "canonical-tenant"));
    assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireNativeOrganizationId(
        claims, admitted, "https://issuer.test", "other", "canonical-tenant"));
    assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireNativeOrganizationId(
        mapper.readTree("{}"), admitted, "https://issuer.test", "actor", "canonical-tenant"));
  }

  @Test
  void bindsCurrentProfileNamesToTheCorrectIndependentAccounts() {
    ProductProfileResponse owner = new ProductProfileResponse().userId("owner-account").displayName("Current owner");
    ProductProfileResponse peer = new ProductProfileResponse().userId("peer-account").displayName("Aktuelles Mitglied");
    assertEquals(new FreshProductFlow.NativeActorNames("Current owner", "Aktuelles Mitglied", "native-org"),
        FreshProductFlow.requireNativeActorNames(owner, peer, "owner-account", "peer-account", "native-org"));
    assertThrows(ProductFlowException.class, () ->
        FreshProductFlow.requireNativeActorNames(owner, peer, "peer-account", "owner-account", "native-org"));
    for (String invalid : new String[] {"", " ", "Current owner"}) {
      peer.displayName(invalid);
      assertThrows(ProductFlowException.class, () ->
          FreshProductFlow.requireNativeActorNames(owner, peer, "owner-account", "peer-account", "native-org"));
    }
  }
}
