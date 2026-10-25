package com.massimotter.weave.backend.identity.invitation;

import com.massimotter.weave.backend.identity.keycloak.IdentityInvitationProperties;
import com.massimotter.weave.backend.identity.keycloak.KeycloakAuthConfig;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

final class KeycloakTestTransport {
    private KeycloakTestTransport() {}

    static KeycloakIdentityAdminClient client(
            IdentityInvitationProperties properties, ObjectMapper mapper, RestClient transport) {
        return new KeycloakIdentityAdminClient(
                properties, mapper, KeycloakAuthConfig.proxy(transport, properties.keycloak().baseUrl()));
    }
}
