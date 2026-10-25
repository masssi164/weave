package com.massimotter.weave.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.persistence.EntityManagerFactory;
import com.massimotter.weave.backend.security.port.MemberSessionStateStore;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import com.massimotter.weave.backend.health.ProviderHealthContribution;
import com.massimotter.weave.backend.health.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.security.MemberSessionCutoffService;
import com.massimotter.weave.backend.security.MemberSessionRevocationService;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationPort;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CorePersistenceDiscoveryTest {
    @Test
    void discoversTheModuleImportsResourceAndCreatesItsRealRepositories() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:core-discovery;DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CorePersistenceAutoConfiguration.class);
                    assertThat(context).hasSingleBean(EntityManagerFactory.class);
                    assertThat(context).hasSingleBean(MemberSessionStateStore.class);
                    assertThat(context).doesNotHaveBean(ProviderCapabilityHealthService.class);
                    assertThat(context).doesNotHaveBean(CoreSecurityAutoConfiguration.class);
                    assertThat(context).hasBean("schemaAuthorityJpaRepository").hasBean("personBindingJpaRepository");
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities())
                            .extracting(entity -> entity.getJavaType().getName())
                            .contains("com.massimotter.weave.backend.identity.PersonBindingJpaEntity");
                });
    }

    @Test
    void discoversServingHealthWithoutAnyOptionalDomainAndDefersProviderAccess() {
        AtomicInteger probes = new AtomicInteger();
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .withBean(ProviderHealthContribution.class, () -> new ProviderHealthContribution(
                        "example", () -> true, () -> {
                            probes.incrementAndGet();
                            return ProviderCapabilityProbeResult.available("example-ready");
                        }))
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(CoreProviderHealthAutoConfiguration.class)
                            .hasSingleBean(ProviderCapabilityHealthService.class)
                            .hasSingleBean(CoreSecurityAutoConfiguration.class)
                            .hasSingleBean(OrganizationIdentityContextResolver.class)
                            .hasSingleBean(ContextAuthorizationPort.class)
                            .hasSingleBean(MemberSessionCutoffService.class)
                            .hasSingleBean(MemberSessionRevocationService.class);
                    assertThat(context.getBean(ProviderCapabilityHealthService.class)
                            .supportSafeSnapshot().capabilities())
                            .extracting(capability -> capability.capability()).containsExactly("example");
                    assertThat(probes).hasValue(0);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
