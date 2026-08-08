package com.massimotter.weave.backend.identity.migration;

import java.util.List;
import java.util.Set;

/** Exact Keycloak 26.7 post-import FGAP operation accepted by the migration command. */
final class KeycloakFgapMigrationContract {
  static final String KEYCLOAK_VERSION = "26.7.1";
  static final String REALM = "weave";
  static final String BOOTSTRAP_REALM = "master";
  static final String MIGRATION_CLIENT_ID = "weave-realm-migration-bootstrap";
  static final String IDENTITY_ADMIN_CLIENT_ID = "weave-identity-admin";
  static final String ADMIN_PERMISSIONS_CLIENT_ID = "admin-permissions";
  static final String REALM_MANAGEMENT_CLIENT_ID = "realm-management";
  static final String ORGANIZATION_ALIAS = "weave";
  static final String ORGANIZATION_ID = "8f771be4-f526-5bef-97dc-00c8e2fa383d";

  static final String MANIFEST_SCHEMA = "weave.keycloak-realm-migration-manifest/v1";
  static final String BUNDLE_SCHEMA = "weave.keycloak-realm-migration-bundle/v1";
  static final String RESULT_SCHEMA = "weave.keycloak-fgap-migration-receipt/v1";
  static final String RECEIPT_PATH =
      "keycloak/migrations/fgap-v2-primary-organization-post-import.receipt.json";
  static final String BUNDLE_PATH = "keycloak/migrations/fresh-start-v1.json";
  static final String OPERATION_ID = "fgap-v2-primary-organization-post-import";
  static final String DESIRED_STATE_DIGEST =
      "sha256:4c08fafc5467fe2f8f521cfd31e09a40bd3fef034b93bbff43098d363f9ac57a";

  static final String POLICY_NAME = "weave-identity-admin user policy";
  static final String ORGANIZATION_PERMISSION_NAME =
      "weave-identity-admin primary organization";
  static final String USERS_PERMISSION_NAME = "weave-identity-admin users";
  static final Set<String> EXPECTED_PERMISSION_NAMES =
      Set.of(ORGANIZATION_PERMISSION_NAME, USERS_PERMISSION_NAME);
  static final List<String> ORGANIZATION_SCOPES = List.of("view", "manage");
  static final List<String> USERS_SCOPES =
      List.of("view", "manage", "manage-group-membership");

  private KeycloakFgapMigrationContract() {}
}
