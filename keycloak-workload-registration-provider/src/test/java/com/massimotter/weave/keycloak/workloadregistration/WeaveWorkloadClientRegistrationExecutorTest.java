package com.massimotter.weave.keycloak.workloadregistration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.authenticators.client.JWTClientAuthenticator;
import org.keycloak.crypto.Algorithm;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;

class WeaveWorkloadClientRegistrationExecutorTest {

    @Test
    void mapsValidatedClientNameToInternalClientIdAndNormalizesPosture() throws Exception {
        ClientRepresentation representation = validRegistration("weaver-cell-alpha");

        WeaveWorkloadClientRegistrationExecutor.prepareRegistration(representation);

        assertEquals("weaver-cell-alpha", representation.getClientId());
        assertEquals(List.of("weaver-runtime-workload"), representation.getDefaultClientScopes());
        assertEquals(
                WeaveWorkloadClientRegistrationExecutor.OPTIONAL_SCOPES,
                SetSupport.copyOf(representation.getOptionalClientScopes()));
        assertFalse(representation.isFullScopeAllowed());
        assertFalse(representation.isStandardFlowEnabled());
        assertTrue(representation.isServiceAccountsEnabled());
    }

    @Test
    void rejectsMalformedNamespace() {
        ClientRepresentation representation = validRegistration("weaver-cell-invalid.value");

        ClientPolicyException error = assertThrows(
                ClientPolicyException.class,
                () -> WeaveWorkloadClientRegistrationExecutor.prepareRegistration(representation));

        assertEquals("invalid_client_metadata", error.getError());
        assertEquals(WeaveWorkloadClientRegistrationExecutor.INVALID_DETAIL, error.getErrorDetail());
    }

    @Test
    void rejectsHumanFlowAndUnapprovedScope() {
        ClientRepresentation representation = validRegistration("weaver-cell-alpha");
        representation.setStandardFlowEnabled(true);
        representation.setOptionalClientScopes(
                List.of("agent-runtime.profile.read", "mcp.tools", "files.read", "calendar.read"));

        assertThrows(
                ClientPolicyException.class,
                () -> WeaveWorkloadClientRegistrationExecutor.prepareRegistration(representation));
    }

    @Test
    void rejectsRedirectUrlAndArbitraryAttribute() {
        ClientRepresentation representation = validRegistration("weaver-cell-alpha");
        representation.setRedirectUris(List.of("https://invalid.example/callback"));
        representation.getAttributes().put("arbitrary", "value");

        assertThrows(
                ClientPolicyException.class,
                () -> WeaveWorkloadClientRegistrationExecutor.prepareRegistration(representation));
    }

    @Test
    void rejectsUpdateForAnotherCell() {
        ClientRepresentation representation = validRegistration("weaver-cell-alpha");
        representation.setClientId("weaver-cell-alpha");

        assertThrows(
                ClientPolicyException.class,
                () -> WeaveWorkloadClientRegistrationExecutor.prepareUpdate(
                        representation, "weaver-cell-beta"));
    }

    @Test
    void providerIdentityAndConfigurationAreVersionedAndClosed() {
        WeaveWorkloadClientRegistrationExecutorFactory factory =
                new WeaveWorkloadClientRegistrationExecutorFactory();

        assertEquals(
                "weave-workload-client-registration-enforcer",
                factory.getId());
        assertTrue(factory.getConfigProperties().isEmpty());
    }

    private static ClientRepresentation validRegistration(String clientName) {
        ClientRepresentation representation = new ClientRepresentation();
        representation.setName(clientName);
        representation.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        representation.setClientAuthenticatorType(JWTClientAuthenticator.PROVIDER_ID);
        representation.setServiceAccountsEnabled(true);
        representation.setPublicClient(false);
        representation.setBearerOnly(false);
        representation.setStandardFlowEnabled(false);
        representation.setImplicitFlowEnabled(false);
        representation.setDirectAccessGrantsEnabled(false);
        representation.setAuthorizationServicesEnabled(false);
        representation.setRedirectUris(List.of());
        representation.setWebOrigins(List.of());
        representation.setOptionalClientScopes(
                new ArrayList<>(WeaveWorkloadClientRegistrationExecutor.OPTIONAL_SCOPES));
        Map<String, String> attributes = new HashMap<>();
        attributes.put(OIDCConfigAttributes.USE_JWKS_STRING, "true");
        attributes.put(OIDCConfigAttributes.USE_JWKS_URL, "false");
        attributes.put(OIDCConfigAttributes.JWKS_STRING, "{\"keys\":[{\"kid\":\"cell\"}]}");
        attributes.put(
                JWTClientAuthenticator.ATTR_PREFIX + ".public.key",
                "public-key");
        attributes.put(
                JWTClientAuthenticator.ATTR_PREFIX + ".kid",
                "cell");
        attributes.put(OIDCConfigAttributes.TOKEN_ENDPOINT_AUTH_SIGNING_ALG, Algorithm.PS256);
        attributes.put("use.refresh.tokens", "false");
        attributes.put("backchannel.logout.session.required", "false");
        attributes.put("backchannel.logout.revoke.offline.tokens", "false");
        attributes.put("frontchannel.logout.session.required", "false");
        representation.setAttributes(attributes);
        return representation;
    }

    private static final class SetSupport {
        private SetSupport() {
        }

        static <T> java.util.Set<T> copyOf(List<T> values) {
            return java.util.Set.copyOf(values);
        }
    }
}
