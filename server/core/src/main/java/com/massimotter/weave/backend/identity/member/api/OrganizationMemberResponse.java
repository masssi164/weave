package com.massimotter.weave.backend.identity.member.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record OrganizationMemberResponse(
    String memberHandle,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "acct_[0-9a-f]{32}",
        description = "Canonical Weave account reference for the current live member; not an access grant.")
    String accountRef,
    String email,
    String displayName,
    String role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> capabilities,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
    String version) {

  public OrganizationMemberResponse {
    capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
  }
}
