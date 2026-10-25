package com.massimotter.weave.backend.identity.migration;

/** Support-safe failure from the explicit Keycloak realm migration boundary. */
final class KeycloakRealmMigrationException extends RuntimeException {
  private final String reasonCode;

  KeycloakRealmMigrationException(String reasonCode) {
    super(reasonCode);
    this.reasonCode = reasonCode;
  }

  String reasonCode() {
    return reasonCode;
  }
}
