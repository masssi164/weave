package com.massimotter.weave.backend.model.identity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record OrganizationMemberPageResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrganizationMemberResponse> items,
    @Schema(nullable = true) String nextCursor) {
  public OrganizationMemberPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
