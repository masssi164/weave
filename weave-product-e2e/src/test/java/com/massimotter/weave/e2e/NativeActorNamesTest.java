package com.massimotter.weave.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.massimotter.weave.userapi.model.ProductProfileResponse;
import org.junit.jupiter.api.Test;

final class NativeActorNamesTest {
  @Test
  void bindsCurrentProfileNamesToTheCorrectIndependentAccounts() {
    ProductProfileResponse owner = new ProductProfileResponse().userId("owner-account").displayName("Current owner");
    ProductProfileResponse peer = new ProductProfileResponse().userId("peer-account").displayName("Aktuelles Mitglied");
    assertEquals(new FreshProductFlow.NativeActorNames("Current owner", "Aktuelles Mitglied"),
        FreshProductFlow.requireNativeActorNames(owner, peer, "owner-account", "peer-account"));
    assertThrows(ProductFlowException.class, () ->
        FreshProductFlow.requireNativeActorNames(owner, peer, "peer-account", "owner-account"));
    for (String invalid : new String[] {"", " ", "Current owner"}) {
      peer.displayName(invalid);
      assertThrows(ProductFlowException.class, () ->
          FreshProductFlow.requireNativeActorNames(owner, peer, "owner-account", "peer-account"));
    }
  }
}
