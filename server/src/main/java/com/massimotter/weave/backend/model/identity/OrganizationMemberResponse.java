package com.massimotter.weave.backend.model.identity;

import java.util.List;

public record OrganizationMemberResponse(
    String memberHandle,
    String email,
    String displayName,
    String role,
    List<String> capabilities,
    boolean enabled,
    String version) {

  public OrganizationMemberResponse {
    capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
  }
}
