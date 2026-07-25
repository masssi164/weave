package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.adapter.RuntimeStateJpaAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigner;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class AgentRuntimeAdminConfigurationTest {
    @TempDir
    Path temporary;

    @Test
    void completeRuntimeControlPlanePublishesTheAdminServiceWhenStateStoreIsConfiguredAlongsideIt()
            throws Exception {
        Path accessKey = privateSecret("access-key", "test-access-key");
        Path secretKey = privateSecret("secret-key", "test-secret-key");
        new ApplicationContextRunner()
                .withUserConfiguration(
                        AgentRuntimeAdminConfiguration.class,
                        AgentRuntimeStateStoreConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RuntimeStateJpaAuthority.class, () -> mock(RuntimeStateJpaAuthority.class))
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
                        "weave.agent-runtime.workload-identity.enabled=true",
                        "weave.agent-runtime.policy.enabled=true",
                        "weave.agent-runtime.profile-signing.enabled=true",
                        "weave.agent-runtime.state-store.enabled=true",
                        "weave.agent-runtime.state-store.wrapping-key-root=" + temporary,
                        "weave.agent-runtime.state-store.endpoint=http://127.0.0.1:9000",
                        "weave.agent-runtime.state-store.region=us-east-1",
                        "weave.agent-runtime.state-store.bucket=weave-runtime-state-test",
                        "weave.agent-runtime.state-store.credential-ref=secretref:runtime-state/test",
                        "weave.agent-runtime.state-store.access-key-file=" + accessKey,
                        "weave.agent-runtime.state-store.secret-key-file=" + secretKey)
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).hasSingleBean(RuntimeStateStoreAdmin.class);
                    assertThat(application).hasSingleBean(AgentRuntimeAdminService.class);
                });
    }

    private Path privateSecret(String fileName, String value) throws Exception {
        Path secret = Files.writeString(temporary.resolve(fileName), value);
        try {
            Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows/non-POSIX test hosts retain the regular-file checks.
        }
        return secret.toAbsolutePath();
    }
}
