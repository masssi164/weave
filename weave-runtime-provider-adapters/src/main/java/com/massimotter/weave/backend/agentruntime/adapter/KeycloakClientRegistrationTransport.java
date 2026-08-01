package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Narrow transport for Keycloak's OIDC Dynamic Client Registration and workload token protocols.
 *
 * <p>Registration Access Tokens are adapter-private and must never be logged, surfaced through
 * application ports, or reused for a different registration URI.
 */
public interface KeycloakClientRegistrationTransport {

    JsonNode create(
            JsonNode metadata,
            String administrationAccessToken,
            RegistrationHandoffProof handoff);

    JsonNode retrieve(
            String clientId,
            URI registrationUri,
            byte[] registrationAccessToken);

    JsonNode update(
            String clientId,
            URI registrationUri,
            JsonNode metadata,
            byte[] registrationAccessToken,
            RegistrationHandoffProof handoff);

    JsonNode recover(
            String clientId,
            URI registrationUri,
            String administrationAccessToken,
            RegistrationHandoffProof handoff);

    FinalizeResult finalizeHandoff(
            String clientId,
            URI registrationUri,
            byte[] registrationAccessToken,
            RegistrationHandoffProof handoff);

    void delete(
            String clientId,
            URI registrationUri,
            byte[] registrationAccessToken);

    JsonNode clientCredentials(Map<String, String> parameters);

    enum RegistrationHandoffOperation {
        CREATE("create"),
        ROTATE("rotate"),
        DISABLE("disable"),
        REENABLE("reenable");

        private final String wireValue;

        RegistrationHandoffOperation(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    enum FinalizeResult {
        FINALIZED,
        ALREADY_FINALIZED
    }

    record RegistrationHandoffProof(
            byte[] capability,
            String stateDigest,
            RegistrationHandoffOperation operation) {
        public RegistrationHandoffProof {
            capability = capability == null ? null : capability.clone();
            if (capability == null
                    || capability.length != 32
                    || stateDigest == null
                    || !stateDigest.matches("sha256:[a-f0-9]{64}")
                    || operation == null) {
                throw new IllegalArgumentException(
                        "The registration handoff proof is invalid");
            }
        }

        @Override
        public byte[] capability() {
            return capability.clone();
        }

        public String capabilityHeader() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(capability);
        }

        public void destroy() {
            Arrays.fill(capability, (byte) 0);
        }
    }
}
