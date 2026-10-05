package com.massimotter.weave.e2e;

import tools.jackson.databind.JsonNode;

/** Independent token assertions for the isolated product fixture's native organization. */
final class NativeOrganizationClaims {
  private static final String EXPECTED_ALIAS = "weave-e2e";
  private static final String EXPECTED_ID = "8f771be4-f526-5bef-97dc-00c8e2fa383d";

  private NativeOrganizationClaims() {}

  /** Returns only a fixed diagnostic code; no observed claim value leaves this assertion. */
  static String violation(JsonNode claims) {
    if (!claims.has("organization")) {
      return "native-organization-absent";
    }
    JsonNode organizations = claims.path("organization");
    if (!organizations.isObject()) {
      return "native-organization-malformed";
    }
    if (organizations.isEmpty()) {
      return "native-organization-empty";
    }
    if (organizations.size() != 1) {
      return "native-organization-multiple";
    }
    if (!organizations.path(EXPECTED_ALIAS).isObject()) {
      return "native-organization-alias";
    }
    JsonNode id = organizations.path(EXPECTED_ALIAS).path("id");
    if (!id.isString() || !EXPECTED_ID.equals(id.asString())) {
      return "native-organization-id";
    }
    return null;
  }
}
