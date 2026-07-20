package com.massimotter.weave.backend.agentruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthorityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementDeniedException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
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
    private FixedEntitlementAuthority entitlementAuthority;
    private JdbcRuntimeGovernanceRepository governance;
    private MutableClock clock;
    private AgentRuntimeControlService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("arc-service-" + UUID.randomUUID())
                .build();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V011__agent_runtime_control_foundation.sql"), new ClassPathResource(
                "db/migration/V012__agent_runtime_governance_facts.sql")).execute(database);
        JdbcTemplate jdbc = new JdbcTemplate(database);
        cells = new JdbcRuntimeCellRepository(jdbc);
        workloadAdmin = new CountingWorkloadAdmin();
        entitlementAuthority = new FixedEntitlementAuthority();
        governance = new JdbcRuntimeGovernanceRepository(jdbc);
        clock = new MutableClock(NOW);
        service = new AgentRuntimeControlService(
                cells, new JdbcRuntimeCommandRepository(jdbc), new JdbcRuntimeProfileRepository(jdbc), workloadAdmin,
                entitlementAuthority, governance, clock);
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

    @Test
    void revocationFencesTheCellBeforeDisablingItsWorkloadIdentity() {
        RuntimeCell provisioned = service.provision(command("idempotency-key-0001", "member-1"));

        RuntimeCell revoked = service.revoke(new AgentRuntimeControlService.RevokeRuntimeCommand(
                "org:example", "person:example", "membership-revoked", "actor:admin",
                "idempotency-key-0002", "audit:revoke"));

        assertThat(revoked.entitlementState()).isEqualTo(RuntimeEntitlementState.REVOKED);
        assertThat(revoked.desiredState()).isEqualTo(RuntimeCellState.REVOKING);
        assertThat(workloadAdmin.disabledClientId).isEqualTo(provisioned.workloadBinding().clientId());
    }

    @Test
    void identityProviderFailureCannotRestoreARevokedCellsLeaseAuthority() {
        service.provision(command("idempotency-key-0001", "member-1"));
        workloadAdmin.failDisableNext = true;
        AgentRuntimeControlService.RevokeRuntimeCommand revoke =
                new AgentRuntimeControlService.RevokeRuntimeCommand(
                        "org:example", "person:example", "membership-revoked", "actor:admin",
                        "idempotency-key-0002", "audit:revoke");

        assertThatThrownBy(() -> service.revoke(revoke)).isInstanceOf(IllegalStateException.class);

        RuntimeCell fenced = cells.findByPerson("org:example", "person:example").orElseThrow();
        assertThat(fenced.entitlementState()).isEqualTo(RuntimeEntitlementState.REVOKED);
        assertThat(fenced.leaseId()).isNull();
        assertThat(service.revoke(revoke).entitlementState()).isEqualTo(RuntimeEntitlementState.REVOKED);
    }

    @Test
    void currentGroupRemovalCreatesDurableRevocationAndFencesTheCell() {
        RuntimeCell provisioned = service.provision(command("idempotency-key-0001", "member-1"));
        entitlementAuthority.denied = true;

        RuntimeCell revoked = service.reconcileEntitlement(provisioned, "audit:reconcile");

        assertThat(revoked.entitlementState()).isEqualTo(RuntimeEntitlementState.REVOKED);
        assertThat(revoked.desiredState()).isEqualTo(RuntimeCellState.REVOKING);
        assertThat(workloadAdmin.disabledClientId).isEqualTo(provisioned.workloadBinding().clientId());
        assertThat(governance.findEffectiveRevision(
                provisioned.organizationRef(), provisioned.personRef(),
                provisioned.entitlementRevision(), NOW)).isEmpty();
    }

    @Test
    void authorityOutageUsesOnlyTheBoundedObservationWindowThenFailsClosed() {
        RuntimeCell provisioned = service.provision(command("idempotency-key-0001", "member-1"));
        entitlementAuthority.unavailable = true;

        assertThatThrownBy(() -> service.reconcileEntitlement(provisioned, "audit:reconcile"))
                .isInstanceOf(RuntimeEntitlementAuthorityException.class);
        assertThat(cells.findByCellRef(provisioned.cellRef()).orElseThrow().entitlementState())
                .isEqualTo(RuntimeEntitlementState.ENTITLED);

        clock.set(NOW.plusSeconds(301));
        RuntimeCell fenced = service.reconcileEntitlement(provisioned, "audit:reconcile-expired");
        assertThat(fenced.entitlementState()).isEqualTo(RuntimeEntitlementState.REVOKED);
    }

    @Test
    void capabilityPolicyChangeSupersedesTheOldFactAndRequiresANewProfile() {
        RuntimeCell provisioned = service.provision(command("idempotency-key-0001", "member-1"));
        entitlementAuthority.capabilityRevision = "sha256:" + "3".repeat(64);

        RuntimeCell rebound = service.reconcileEntitlement(provisioned, "audit:policy-change");

        assertThat(rebound.entitlementState()).isEqualTo(RuntimeEntitlementState.ENTITLED);
        assertThat(rebound.entitlementRevision()).isNotEqualTo(provisioned.entitlementRevision());
        assertThat(rebound.runtimeProfileHash()).isNull();
        assertThat(rebound.fencingEpoch()).isEqualTo(provisioned.fencingEpoch() + 1);
    }

    private static AgentRuntimeControlService.ProvisionRuntimeCommand command(String key, String subject) {
        return new AgentRuntimeControlService.ProvisionRuntimeCommand(
                "org:example", "person:example", new RuntimeMemberBinding(ISSUER, subject),
                "workspace:1", "webdav-manifest:workspace:1",
                "runtime-state://org/example/person/example/state/1",
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT, key, "audit:example");
    }

    private static final class FixedEntitlementAuthority implements RuntimeEntitlementAuthority {
        private boolean denied;
        private boolean unavailable;
        private String capabilityRevision = "sha256:" + "2".repeat(64);

        @Override
        public RuntimeEntitlementObservation observe(ObserveEntitlementCommand command) {
            if (denied) {
                throw new RuntimeEntitlementDeniedException("not currently entitled");
            }
            if (unavailable) {
                throw new RuntimeEntitlementAuthorityException("simulated authority outage");
            }
            return new RuntimeEntitlementObservation(
                    command.organizationRef(), command.personRef(), command.memberBinding(), "keycloak",
                    "sha256:" + "1".repeat(64), capabilityRevision,
                    NOW, NOW.plusSeconds(300));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant next) {
            instant = next;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class CountingWorkloadAdmin implements RuntimeWorkloadIdentityAdmin {
        private final AtomicInteger calls = new AtomicInteger();
        private boolean failNext;
        private String lastClientId;
        private String disabledClientId;
        private boolean failDisableNext;

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

        @Override
        public RuntimeWorkloadBinding reconcileBinding(ReconcileBindingCommand command) {
            return command.binding();
        }

        @Override
        public void disableBinding(DisableBindingCommand command) {
            disabledClientId = command.binding().clientId();
            if (failDisableNext) {
                failDisableNext = false;
                throw new IllegalStateException("simulated identity provider outage");
            }
        }

        @Override
        public RuntimeWorkloadBinding rotateBinding(RotateBindingCommand command) {
            return command.binding();
        }

        @Override
        public RuntimeWorkloadBinding retirePreviousCredential(RetireCredentialCommand command) {
            return command.binding();
        }

        @Override
        public void deleteBinding(DeleteBindingCommand command) {
            disabledClientId = command.binding().clientId();
        }
    }
}
