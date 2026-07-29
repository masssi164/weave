package com.massimotter.weave.keycloak.workloadregistration;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.idm.ClientPolicyExecutorConfigurationRepresentation;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProvider;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProviderFactory;

/**
 * Keycloak 26.7 Client Policy factory for the Weave per-Cell workload boundary.
 */
public final class WeaveWorkloadClientRegistrationExecutorFactory
        implements ClientPolicyExecutorProviderFactory {

    public static final String PROVIDER_ID = "weave-workload-client-registration-enforcer";

    @Override
    public ClientPolicyExecutorProvider<ClientPolicyExecutorConfigurationRepresentation> create(
            KeycloakSession session) {
        return new WeaveWorkloadClientRegistrationExecutor(session);
    }

    @Override
    public void init(Config.Scope config) {
        // The contract is intentionally versioned in code and has no realm-specific knobs.
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No global state is retained.
    }

    @Override
    public void close() {
        // No resources are retained.
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Enforces the Weave per-Cell OIDC Dynamic Client Registration contract.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
