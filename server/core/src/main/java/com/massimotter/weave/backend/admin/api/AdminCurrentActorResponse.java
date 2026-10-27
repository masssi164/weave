package com.massimotter.weave.backend.admin.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Current admitted Admin caller; references identify the actor and never grant access. */
public record AdminCurrentActorResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "acct_[0-9a-f]{32}")
    String accountRef,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String organizationId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String identityIssuer,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles) {
  public AdminCurrentActorResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
