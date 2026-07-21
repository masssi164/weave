package com.massimotter.weave.backend.agentruntime.adapter;

/** Supplies short-lived Keycloak administration access tokens without exposing its long-lived credential. */
public interface KeycloakAdminAccessTokenProvider {
    String accessToken();

    default void invalidate(String rejectedToken) {
        // Stateless providers do not cache tokens.
    }
}
