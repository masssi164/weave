package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigner;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class AgentRuntimeAdminConfigurationTest {
    @TempDir
    Path temporary;

    @Test
    void completeRuntimeControlPlanePublishesTheAdminServiceWhenStateStoreIsConfiguredAlongsideIt() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        AgentRuntimeAdminConfiguration.class,
                        AgentRuntimeStateStoreConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(RuntimePersonDirectory.class, () -> mock(RuntimePersonDirectory.class))
                .withBean(RuntimePolicyAuthority.class, () -> mock(RuntimePolicyAuthority.class))
                .withBean(AgentRuntimeControlService.class, () -> mock(AgentRuntimeControlService.class))
                .withBean(RuntimeProfileSigner.class, () -> mock(RuntimeProfileSigner.class))
                .withBean(RuntimeProfileRepository.class, () -> mock(RuntimeProfileRepository.class))
                .withBean(RuntimeCellRepository.class, () -> mock(RuntimeCellRepository.class))
                .withBean(RuntimeCommandRepository.class, () -> mock(RuntimeCommandRepository.class))
                .withBean(RuntimeWorkloadIdentityAdmin.class, () -> mock(RuntimeWorkloadIdentityAdmin.class))
                .withPropertyValues(
                        "weave.agent-runtime.storage.mode=jdbc",
                        "weave.agent-runtime.workload-identity.enabled=true",
                        "weave.agent-runtime.policy.enabled=true",
                        "weave.agent-runtime.profile-signing.enabled=true",
                        "weave.agent-runtime.state-store.enabled=true",
                        "weave.agent-runtime.state-store.wrapping-key-root=" + temporary)
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).hasSingleBean(RuntimeStateStoreAdmin.class);
                    assertThat(application).hasSingleBean(AgentRuntimeAdminService.class);
                });
    }
}
