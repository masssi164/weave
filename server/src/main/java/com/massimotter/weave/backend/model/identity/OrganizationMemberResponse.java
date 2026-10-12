package com.massimotter.weave.backend.model.identity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record OrganizationMemberResponse(
    String memberHandle,
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
