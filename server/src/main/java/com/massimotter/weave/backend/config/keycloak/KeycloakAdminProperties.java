package com.massimotter.weave.backend.config.keycloak;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/** An immutable snapshot of the existing, authoritative invitation properties. */
public record KeycloakAdminProperties(
    URI baseUrl,
    String realm,
    String adminClientId,
    String adminCredentialRef,
    Path adminPrivateJwkFile,
    URI privateKeyJwtAudience,
    Duration timeout) {
  public static KeycloakAdminProperties from(IdentityInvitationProperties properties) {
    var keycloak = properties.keycloak();
    return new KeycloakAdminProperties(
        keycloak.baseUrl(), keycloak.realm(), keycloak.adminClientId(),
        keycloak.adminCredentialRef(), keycloak.adminPrivateJwkFile(),
        keycloak.privateKeyJwtAudience(), keycloak.timeout());
  }
}
