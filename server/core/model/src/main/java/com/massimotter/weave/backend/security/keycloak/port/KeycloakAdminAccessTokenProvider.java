package com.massimotter.weave.backend.security.keycloak.port;

/** Supplies short-lived Keycloak administration access tokens without exposing its long-lived credential. */
public interface KeycloakAdminAccessTokenProvider {
    String accessToken();

    default void invalidate(String rejectedToken) {
        // Stateless providers do not cache tokens.
    }
}
