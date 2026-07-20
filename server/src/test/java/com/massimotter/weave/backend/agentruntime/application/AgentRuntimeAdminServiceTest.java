package com.massimotter.weave.backend.agentruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProvisioningPlan;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.model.agentruntime.AgentRuntimeProjectionResponse;
import com.massimotter.weave.backend.model.agentruntime.StopAgentRuntimeRequest;
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

class AgentRuntimeAdminServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");
    private static final String ORGANIZATION = "tenant-default";
    private static final String PERSON = "acct_" + "a".repeat(32);
    private static final RuntimeMemberBinding MEMBER = new RuntimeMemberBinding(
            "https://auth.weave.test/realms/weave", "keycloak-user-1");
    private static final AgentRuntimeAdminService.AdminContext ADMIN =
            new AgentRuntimeAdminService.AdminContext(
                    ORGANIZATION, "issuer+subject:https://auth.weave.test/realms/weave#admin-1",
                    "audit:request");
    private static final AgentRuntimeAdminService.AdminContext RETRY_ADMIN =
            new AgentRuntimeAdminService.AdminContext(
                    ORGANIZATION, "issuer+subject:https://auth.weave.test/realms/weave#admin-1",
                    "audit:another-request");

    private JdbcRuntimeCellRepository cells;
    private JdbcRuntimeProfileRepository profiles;
    private RuntimeProfileIssuanceService issuance;
    private CountingWorkloadAdmin workloads;
    private CountingStateAdmin state;
    private AgentRuntimeAdminService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("arc-admin-" + UUID.randomUUID())
                .build();
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V011__agent_runtime_control_foundation.sql"),
                new ClassPathResource("db/migration/V012__agent_runtime_governance_facts.sql"))
                .execute(database);
        JdbcTemplate jdbc = new JdbcTemplate(database);
        cells = new JdbcRuntimeCellRepository(jdbc);
        JdbcRuntimeCommandRepository commands = new JdbcRuntimeCommandRepository(jdbc);
        profiles = new JdbcRuntimeProfileRepository(jdbc);
        JdbcRuntimeGovernanceRepository governance = new JdbcRuntimeGovernanceRepository(jdbc);
        workloads = new CountingWorkloadAdmin();
        RuntimeEntitlementAuthority entitlement = command -> new RuntimeEntitlementObservation(
                command.organizationRef(), command.personRef(), command.memberBinding(), "keycloak",
                "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64),
                NOW, NOW.plusSeconds(300));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AgentRuntimeControlService control = new AgentRuntimeControlService(
                cells, commands, profiles, workloads, entitlement, governance, clock);
        RuntimePersonDirectory people = command -> {
            if (!ORGANIZATION.equals(command.organizationRef()) || !PERSON.equals(command.personRef())) {
                throw new IllegalArgumentException("unexpected test person");
            }
            return new RuntimePersonDirectory.ResolvedRuntimePerson(ORGANIZATION, PERSON, MEMBER);
        };
        RuntimePolicyAuthority policy = mock(RuntimePolicyAuthority.class);
        given(policy.provisioningPlan(any())).willReturn(new RuntimeProvisioningPlan(
                "workspace:v1", "webdav-manifest://tenant-default/person/current",
                "runtime-state://tenant-default/person/state",
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT));
        issuance = mock(RuntimeProfileIssuanceService.class);
        given(issuance.issue(any(), any(), any())).willAnswer(invocation -> {
            RuntimeCell cell = invocation.getArgument(0);
            String issuanceRef = invocation.getArgument(1);
            Instant issuedAt = invocation.getArgument(2);
            String fingerprint = RuntimeWorkloadOwnership.fingerprint(issuanceRef).substring(7);
            SignedRuntimeProfile signed = new SignedRuntimeProfile(
                    "protected", "payload", "A".repeat(86), "sha256:" + fingerprint,
                    "rp_" + fingerprint, cell.cellRef(), "key-example",
                    issuedAt, issuedAt.plusSeconds(120));
            return signed;
        });
        state = new CountingStateAdmin();
        service = new AgentRuntimeAdminService(
                people, policy, control, issuance, cells, commands, profiles,
                workloads, state, clock);
    }

    @Test
    void exactLifecycleIsIdempotentAndDeletionNeverTargetsCanonicalContent() {
        AgentRuntimeProjectionResponse provisioned = service.provision(
                ADMIN, PERSON, "idempotency-provision-0001");
        AgentRuntimeProjectionResponse started = service.start(
                ADMIN, PERSON, "idempotency-start-00000001");
        AgentRuntimeProjectionResponse replayedStart = service.start(
                ADMIN, PERSON, "idempotency-start-00000001");
        AgentRuntimeProjectionResponse stopped = service.stop(
                ADMIN, PERSON, "idempotency-stop-000000001", StopAgentRuntimeRequest.graceful());
        AgentRuntimeProjectionResponse suspended = service.suspend(
                ADMIN, PERSON, "idempotency-suspend-00001", "operator requested maintenance");
        AgentRuntimeProjectionResponse restarted = service.start(
                ADMIN, PERSON, "idempotency-restart-00001");
        AgentRuntimeProjectionResponse revoked = service.revoke(
                ADMIN, PERSON, "idempotency-revoke-00001", "membership ended",
                restarted.entitlementRevision());
        AgentRuntimeProjectionResponse deleted = service.deleteRuntimeState(
                ADMIN, PERSON, "idempotency-delete-state-01", "retention request");
        AgentRuntimeProjectionResponse replayedDelete = service.deleteRuntimeState(
                ADMIN, PERSON, "idempotency-delete-state-01", "retention request");

        assertThat(provisioned.desiredState()).isEqualTo("provisioning");
        assertThat(started.desiredState()).isEqualTo("starting");
        assertThat(replayedStart).isEqualTo(started);
        assertThat(stopped.desiredState()).isEqualTo("stopped");
        assertThat(suspended.desiredState()).isEqualTo("suspended");
        assertThat(suspended.capabilityState()).isEqualTo("disabled_by_policy");
        assertThat(suspended.auditRef()).doesNotContain("maintenance");
        assertThat(revoked.entitlementState()).isEqualTo("revoked");
        assertThat(revoked.desiredState()).isEqualTo("revoking");
        assertThat(deleted.desiredState()).isEqualTo("deleted");
        assertThat(deleted.entitlementState()).isEqualTo("revoked");
        assertThat(replayedDelete).isEqualTo(deleted);
        assertThat(state.calls).hasValue(1);
        assertThat(state.last.runtimeStateStoreRef()).startsWith("runtime-state://");
        assertThat(state.last.runtimeStateStoreRef()).doesNotContain("webdav");
        verify(issuance, times(2)).issue(any(), any(), any());
    }

    @Test
    void staleEntitlementRevisionCannotRevokeCurrentAuthority() {
        AgentRuntimeProjectionResponse provisioned = service.provision(
                ADMIN, PERSON, "idempotency-provision-0002");

        assertThatThrownBy(() -> service.revoke(
                ADMIN, PERSON, "idempotency-revoke-00002", "stale request",
                "sha256:" + "f".repeat(64)))
                .isInstanceOf(com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException.class)
                .hasMessageContaining("stale");
        assertThat(service.get(ADMIN, PERSON).entitlementState()).isEqualTo("entitled");
        assertThat(service.get(ADMIN, PERSON).entitlementRevision())
                .isEqualTo(provisioned.entitlementRevision());
    }

    @Test
    void aNewRequestAuditReferenceDoesNotBreakLegitimateIdempotentRetries() {
        AgentRuntimeProjectionResponse provisioned = service.provision(
                ADMIN, PERSON, "idempotency-provision-0003");
        AgentRuntimeProjectionResponse replayedProvision = service.provision(
                RETRY_ADMIN, PERSON, "idempotency-provision-0003");

        AgentRuntimeProjectionResponse revoked = service.revoke(
                ADMIN, PERSON, "idempotency-revoke-00003", "membership ended",
                provisioned.entitlementRevision());
        AgentRuntimeProjectionResponse replayedRevoke = service.revoke(
                RETRY_ADMIN, PERSON, "idempotency-revoke-00003", "membership ended",
                provisioned.entitlementRevision());

        assertThat(replayedProvision).isEqualTo(provisioned);
        assertThat(replayedRevoke).isEqualTo(revoked);
        assertThat(replayedRevoke.auditRef()).doesNotContain("membership ended");
    }

    private static final class CountingStateAdmin implements RuntimeStateStoreAdmin {
        private final AtomicInteger calls = new AtomicInteger();
        private DeleteRuntimeStateCommand last;

        @Override
        public void deleteRuntimeState(DeleteRuntimeStateCommand command) {
            calls.incrementAndGet();
            last = command;
        }
    }

    private static final class CountingWorkloadAdmin implements RuntimeWorkloadIdentityAdmin {
        private final AtomicInteger ensureCalls = new AtomicInteger();

        @Override
        public RuntimeWorkloadBinding ensureBinding(EnsureBindingCommand command) {
            ensureCalls.incrementAndGet();
            return new RuntimeWorkloadBinding(
                    "https://auth.weave.test/realms/weave",
                    "service-account-" + command.clientId(), command.clientId(),
                    command.authenticationMethod(),
                    "credentialref://weave/runtime/" + command.clientId());
        }

        @Override
        public RuntimeWorkloadBinding reconcileBinding(ReconcileBindingCommand command) {
            return command.binding();
        }

        @Override
        public void disableBinding(DisableBindingCommand command) {
            // Idempotent Keycloak disable is allowed during revoke and state deletion.
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
            // State deletion disables but deliberately does not delete the identity binding.
        }
    }
}
