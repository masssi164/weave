package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService;
import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.agentruntime.port.ReleaseMcpBindingRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class ReleaseMcpBindingConfigurationTest {
    @Test
    void releaseBindingModeWiresOnlyTheBoundedAuthorizationPath() {
        runner().withPropertyValues(
                "weave.mcp.release-binding-file=/run/secrets/agent-runtime/workloads/release-mcp.json",
                "weave.agent-runtime.workload-identity.enabled=true",
                "weave.agent-runtime.policy.enabled=true",
                "weave.agent-runtime.profile-signing.enabled=true",
                "weave.agent-runtime.state-store.enabled=true",
                "weave.agent-runtime.entitlement.enabled=true",
                "weave.agent-runtime.workload-identity.keycloak-admin-base-url=http://keycloak:8080",
                "weave.agent-runtime.workload-identity.issuer=https://auth.weave.test/realms/weave",
                "weave.agent-runtime.workload-identity.keycloak-organization-id=8f771be4-f526-5bef-97dc-00c8e2fa383d")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ReleaseMcpBindingRepository.class);
                    assertThat(context).hasSingleBean(RuntimeEntitlementAuthority.class);
                    assertThat(context).hasSingleBean(McpExchangedTokenPolicy.class);
                    assertThat(context).hasSingleBean(McpWorkloadAuthorizationService.class);
                    assertThat(context).doesNotHaveBean(AgentRuntimeAdminService.class);
                });
    }

    @Test
    void releaseBindingModeIsAbsentWithoutItsPrivateFileSetting() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ReleaseMcpBindingRepository.class);
            assertThat(context).doesNotHaveBean(McpWorkloadAuthorizationService.class);
        });
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        ReleaseMcpBindingConfiguration.class,
                        AgentRuntimeWorkloadIdentityConfiguration.class,
                        AgentRuntimeAdminConfiguration.class)
                .withBean(ObjectMapper.class, () ->
                        tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build())
                .withBean(PlatformContractProperties.class, () ->
                        new PlatformContractProperties(null, null, null, null, null, null, null, null))
                .withBean(KeycloakAdminClientConfiguration.KEYCLOAK_ADMIN_ACCESS_TOKENS,
                        KeycloakAdminAccessTokenProvider.class,
                        () -> mock(KeycloakAdminAccessTokenProvider.class));
    }
}
