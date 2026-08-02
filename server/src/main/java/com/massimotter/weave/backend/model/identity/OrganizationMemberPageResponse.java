package com.massimotter.weave.backend.model.identity;

import java.util.List;

public record OrganizationMemberPageResponse(
    List<OrganizationMemberResponse> items, String nextCursor) {
  public OrganizationMemberPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
