package com.massimotter.weave.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.security.keycloak.port.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.security.workload.application.ReleaseMcpWorkloadAuthorizationService;
import com.massimotter.weave.backend.security.workload.jwt.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.security.workload.port.McpWorkloadAuthorization;
import com.massimotter.weave.backend.security.workload.port.ReleaseMcpBindingRepository;
import com.massimotter.weave.backend.security.workload.port.WorkloadEntitlementAuthority;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

class ReleaseMcpSecurityDiscoveryTest {
    private static final String[] RELEASE_SETTINGS = {
            "weave.mcp.release-binding-file=/run/secrets/agent-runtime/workloads/release-mcp.json",
            "weave.agent-runtime.workload-identity.enabled=true",
            "weave.agent-runtime.entitlement.enabled=true",
            "weave.agent-runtime.workload-identity.keycloak-admin-base-url=http://keycloak:8080",
            "weave.agent-runtime.workload-identity.issuer=https://auth.weave.test/realms/weave",
            "weave.agent-runtime.workload-identity.keycloak-organization-id=8f771be4-f526-5bef-97dc-00c8e2fa383d"
    };

    @Test
    void moduleImportsDiscoverRealReleaseAuthorizationWithoutFutureRuntimeOnTheClasspath() {
        runner().withPropertyValues(RELEASE_SETTINGS).run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(ReleaseMcpSecurityAutoConfiguration.class)
                    .hasSingleBean(ReleaseMcpBindingRepository.class)
                    .hasSingleBean(WorkloadEntitlementAuthority.class)
                    .hasSingleBean(McpExchangedTokenPolicy.class)
                    .hasSingleBean(McpWorkloadAuthorization.class)
                    .hasSingleBean(ReleaseMcpWorkloadAuthorizationService.class);
            assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.agentruntime.domain.RuntimeCell",
                    context.getClassLoader())).isFalse();
        });
    }

    @Test
    void absentReleaseBindingDoesNotCreateAnAuthorizationFallback() {
        runner().run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(McpWorkloadAuthorization.class)
                .doesNotHaveBean(ReleaseMcpBindingRepository.class));
    }

    @Test
    void nonWebSchemaAndOperatorProcessesNeverConstructTheServingAuthority() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues(RELEASE_SETTINGS)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(ReleaseMcpSecurityAutoConfiguration.class)
                        .doesNotHaveBean(McpWorkloadAuthorization.class)
                        .doesNotHaveBean(KeycloakAdminAccessTokenProvider.class));
    }

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
