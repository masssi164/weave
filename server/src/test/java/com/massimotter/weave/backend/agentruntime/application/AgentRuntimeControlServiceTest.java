package com.massimotter.weave.backend.agentruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class AgentRuntimeControlServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");
    private static final String ISSUER = "https://auth.weave.test/realms/weave";

    private JdbcRuntimeCellRepository cells;
    private CountingWorkloadAdmin workloadAdmin;
    private AgentRuntimeControlService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("arc-service-" + UUID.randomUUID())
                .build();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V011__agent_runtime_control_foundation.sql")).execute(database);
        JdbcTemplate jdbc = new JdbcTemplate(database);
        cells = new JdbcRuntimeCellRepository(jdbc);
        workloadAdmin = new CountingWorkloadAdmin();
        service = new AgentRuntimeControlService(
                cells, new JdbcRuntimeCommandRepository(jdbc), workloadAdmin,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void repeatedProvisioningConvergesOnOneCellAndOneWorkloadIdentity() {
        RuntimeCell first = service.provision(command("idempotency-key-0001", "member-1"));
        RuntimeCell replay = service.provision(command("idempotency-key-0001", "member-1"));
        RuntimeCell newCommand = service.provision(command("idempotency-key-0002", "member-1"));

        assertThat(replay).isEqualTo(first);
        assertThat(newCommand).isEqualTo(first);
        assertThat(workloadAdmin.calls).hasValue(1);
        assertThat(first.cellRef()).startsWith("cell:");
        assertThat(first.workloadBinding().clientId()).startsWith("weaver-cell-");
        assertThat(first.workloadBinding().credentialRef()).startsWith("credentialref://");
    }

    @Test
    void aPersonReferenceCannotBeReboundToAnotherMemberIdentity() {
        service.provision(command("idempotency-key-0001", "member-1"));

        assertThatThrownBy(() -> service.provision(command("idempotency-key-0002", "member-2")))
                .isInstanceOf(RuntimeCommandConflictException.class)
                .hasMessageContaining("different runtime cell");
        assertThat(workloadAdmin.calls).hasValue(1);
    }

    @Test
    void retryAfterExternalFailureUsesTheSameDeterministicClientIdentity() {
        workloadAdmin.failNext = true;
        AgentRuntimeControlService.ProvisionRuntimeCommand command = command("idempotency-key-0001", "member-1");

        assertThatThrownBy(() -> service.provision(command)).isInstanceOf(IllegalStateException.class);
        RuntimeCell recovered = service.provision(command);

        assertThat(workloadAdmin.calls).hasValue(2);
        assertThat(workloadAdmin.lastClientId).isEqualTo(recovered.workloadBinding().clientId());
        assertThat(cells.findByPerson("org:example", "person:example")).contains(recovered);
    }

    private static AgentRuntimeControlService.ProvisionRuntimeCommand command(String key, String subject) {
        return new AgentRuntimeControlService.ProvisionRuntimeCommand(
                "org:example", "person:example", new RuntimeMemberBinding(ISSUER, subject),
                "entitlement:1", "workspace:1", "webdav-manifest:workspace:1",
                "runtime-state://org/example/person/example/state/1",
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT, key, "audit:example");
    }

    private static final class CountingWorkloadAdmin implements RuntimeWorkloadIdentityAdmin {
        private final AtomicInteger calls = new AtomicInteger();
        private boolean failNext;
        private String lastClientId;

        @Override
        public RuntimeWorkloadBinding ensureBinding(EnsureBindingCommand command) {
            calls.incrementAndGet();
            lastClientId = command.clientId();
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated identity provider outage");
            }
            return new RuntimeWorkloadBinding(
                    ISSUER, "service-account-" + command.clientId(), command.clientId(),
                    command.authenticationMethod(), "credentialref://weave/runtime/" + command.cellRef().substring(5));
        }
    }
}
