package com.massimotter.weave.backend.agentruntime.application;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.adapter.AgentRuntimeJpaTestFactory;
import com.massimotter.weave.backend.agentruntime.adapter.JpaRuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport.Blocker;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport.State;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory.ClientObservation;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory.ManagementState;
import com.massimotter.weave.backend.config.ProviderHealthProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class AgentRuntimeWorkloadReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T13:00:00Z");
    private static final String ORGANIZATION = "org:example";
    private static final String PERSON = "person:example";
    private static final String CELL = "cell:example";
    private static final String CLIENT = "weaver-cell-example";
    private static final String SUBJECT = "service-account-example";
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String ENTITLEMENT_REVISION = "sha256:" + "1".repeat(64);
    private static final String REVOKED_ENTITLEMENT_REVISION = "sha256:" + "4".repeat(64);

    @TempDir
    Path temporary;

    private JpaRuntimeCellRepository cells;
    private FileRuntimeWorkloadCredentialStore credentials;
    private FakeIdentityBoundary identity;
    private AgentRuntimeWorkloadReconciliationService service;
    private RuntimeCell cell;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        var database =
                com.massimotter.weave.backend.testing.JpaTestDatabase
                        .migratedDataSource("arc-reconcile");
        JdbcTemplate jdbc = new JdbcTemplate(database);
        insertEntitlement(jdbc, ENTITLEMENT_REVISION, false);
        insertEntitlement(jdbc, REVOKED_ENTITLEMENT_REVISION, true);
        cells = AgentRuntimeJpaTestFactory.create(database).cells();
        credentials = new FileRuntimeWorkloadCredentialStore(
                temporary, new ObjectMapper(), Clock.fixed(NOW.minusSeconds(60), ZoneOffset.UTC));
        String owner = owner();
        RuntimeWorkloadCredentialState credential = credentials.create(
                new RuntimeWorkloadCredentialStore.CreateCredentialCommand(
                        CLIENT, owner, RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT));
        RuntimeWorkloadBinding binding = new RuntimeWorkloadBinding(
                ISSUER,
                SUBJECT,
                CLIENT,
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                credential.credentialRef());
        cell = RuntimeCell.provisioning(
                ORGANIZATION,
                PERSON,
                new RuntimeMemberBinding(ISSUER, "member-example"),
                CELL,
                binding,
                ENTITLEMENT_REVISION,
                "workspace:1",
                "webdav-manifest:workspace:1",
                "runtime-state://org/example/person/example/state/1",
                "audit:provision",
                NOW.minusSeconds(30));
        cells.insert(cell);
        identity = new FakeIdentityBoundary(credentials);
        identity.put(exactObservation(true));
        meters = new SimpleMeterRegistry();
        service = new AgentRuntimeWorkloadReconciliationService(
                cells,
                (runtimeCell, auditRef) -> runtimeCell,
                identity,
                identity,
                credentials,
                new ProviderHealthProperties(
                        Duration.ofSeconds(60), Duration.ZERO, Duration.ofMinutes(5), Duration.ofMinutes(15)),
                meters,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> 0L);
    }

    private static void insertEntitlement(JdbcTemplate jdbc, String revision, boolean revoked) {
        String digest = revision.substring("sha256:".length());
        jdbc.update("""
                insert into weave_agent_runtime_entitlements (
                  record_id, entitlement_ref, entitlement_revision, organization_ref, person_ref,
                  member_issuer, member_subject, source_provider, source_group_ref, capability_revision,
                  entitlement_state, effective_at, last_observed_at, expires_at, revocation_ref,
                  revoked_at, audit_ref, created_at, updated_at
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), "entitlement:" + digest, revision,
                ORGANIZATION, PERSON, ISSUER, "member-example", "keycloak",
                "sha256:" + "2".repeat(64), "sha256:" + "3".repeat(64),
                revoked ? "REVOKED" : "ENTITLED",
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(30)),
                Timestamp.from(NOW.plusSeconds(3600)),
                revoked ? "revocation:test" : null,
                revoked ? Timestamp.from(NOW.minusSeconds(1)) : null,
                "audit:entitlement",
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(30)));
    }

    @Test
    void convergedEvidenceContainsOnlyCountsHashedRevisionsAndGuardedClaim() throws Exception {
        RuntimeWorkloadReconciliationReport report = service.reconcileNow("audit:reconcile");

        assertThat(report.state()).isEqualTo(State.CONVERGED);
        assertThat(report.capabilityClaim()).isEqualTo("guarded");
        assertThat(report.blockers()).isEmpty();
        assertThat(report.counts().activeConverged()).isEqualTo(1);
        assertThat(report.counts().providerReservedClients()).isEqualTo(1);
        assertThat(report.authoritativeRevision()).matches("sha256:[a-f0-9]{64}");
        assertThat(report.providerRevision()).matches("sha256:[a-f0-9]{64}");
        assertThat(report.correlationRef()).matches("sha256:[a-f0-9]{64}");
        assertThat(identity.reconciliations).hasValue(1);

        int scans = identity.scans.get();
        assertThat(service.supportSafeSnapshot()).isEqualTo(report);
        assertThat(identity.scans).hasValue(scans);
        String serialized = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(report);
        assertThat(serialized)
                .doesNotContain(ORGANIZATION, PERSON, CELL, CLIENT, SUBJECT, "provider-exact");
        assertThat(meters.get("weave.agent.runtime.workload.clients")
                .tag("state", "active-converged").gauge().value()).isEqualTo(1);
    }

    @Test
    void managedOrphansAndCrossBindingsAreQuarantinedButRemainBlockingEvidence() {
        identity.put(managedObservation(
                "provider-orphan", "weaver-cell-orphan", "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                "orphan-subject", true, Set.of("wk_orphanorphanorphanorphanorphanorphan")));

        RuntimeWorkloadReconciliationReport orphaned = service.reconcileNow("audit:orphan");

        assertThat(orphaned.state()).isEqualTo(State.BLOCKED);
        assertThat(orphaned.blockers()).contains(Blocker.ORPHANED_CLIENT);
        assertThat(orphaned.counts().orphanedClients()).isEqualTo(1);
        assertThat(identity.observation("provider-orphan").enabled()).isFalse();

        identity.remove("provider-orphan");
        ClientObservation current = identity.observation("provider-exact");
        identity.put(new ClientObservation(
                current.providerRef(), current.clientId(), true, ManagementState.MANAGED,
                RuntimeWorkloadOwnership.fingerprint("another-owner"),
                current.organizationFingerprint(), current.personFingerprint(), current.cellFingerprint(),
                true, current.serviceAccountSubject(), current.authenticationMethod(), current.acceptedKeyIds()));

        RuntimeWorkloadReconciliationReport crossBound = service.reconcileNow("audit:cross-bound");

        assertThat(crossBound.blockers()).contains(Blocker.CROSS_BOUND_CLIENT);
        assertThat(crossBound.counts().crossBoundClients()).isEqualTo(1);
        assertThat(identity.observation("provider-exact").enabled()).isFalse();
    }

    @Test
    void unownedReservedClientIsNeverMutatedAndBlocksConvergence() {
        ClientObservation current = identity.observation("provider-exact");
        identity.put(new ClientObservation(
                current.providerRef(), current.clientId(), true, ManagementState.UNOWNED,
                null, null, null, null, true, SUBJECT, "client-jwt", current.acceptedKeyIds()));

        RuntimeWorkloadReconciliationReport report = service.reconcileNow("audit:unowned");

        assertThat(report.state()).isEqualTo(State.BLOCKED);
        assertThat(report.blockers()).contains(Blocker.UNOWNED_CLIENT, Blocker.CROSS_BOUND_CLIENT);
        assertThat(report.counts().unownedClients()).isEqualTo(1);
        assertThat(identity.observation("provider-exact").enabled()).isTrue();
        assertThat(identity.quarantines).hasValue(0);
        assertThat(identity.reconciliations).hasValue(0);
    }

    @Test
    void duplicateManagedClientsAreAllQuarantinedAndNeverSelectedByPosition() {
        ClientObservation exact = identity.observation("provider-exact");
        identity.put(new ClientObservation(
                "provider-duplicate", exact.clientId(), true, exact.managementState(),
                exact.ownerFingerprint(), exact.organizationFingerprint(), exact.personFingerprint(),
                exact.cellFingerprint(), exact.serviceAccountsEnabled(), exact.serviceAccountSubject(),
                exact.authenticationMethod(), exact.acceptedKeyIds()));

        RuntimeWorkloadReconciliationReport report = service.reconcileNow("audit:duplicate");

        assertThat(report.blockers()).contains(Blocker.DUPLICATE_CLIENT);
        assertThat(report.counts().duplicateClientBindings()).isEqualTo(1);
        assertThat(identity.observation("provider-exact").enabled()).isFalse();
        assertThat(identity.observation("provider-duplicate").enabled()).isFalse();
        assertThat(identity.quarantines).hasValue(2);
        assertThat(identity.reconciliations).hasValue(0);
    }

    @Test
    void missingProviderAndSecretStateBlockRestoreConvergence() {
        identity.remove("provider-exact");
        credentials.delete(new RuntimeWorkloadCredentialStore.DeleteCredentialCommand(CLIENT, owner()));

        RuntimeWorkloadReconciliationReport report = service.reconcileNow("audit:restore");

        assertThat(report.blockers()).contains(Blocker.MISSING_CLIENT, Blocker.MISSING_CREDENTIAL);
        assertThat(report.counts().missingClients()).isEqualTo(1);
        assertThat(report.counts().missingCredentials()).isEqualTo(1);
        assertThat(report.state()).isEqualTo(State.BLOCKED);
    }

    @Test
    void revokedCellsDisableIdentityAndRemoveCredentialBeforeConverging() {
        cells.revoke(ORGANIZATION, PERSON, REVOKED_ENTITLEMENT_REVISION, "audit:revoke", NOW.minusSeconds(1));

        RuntimeWorkloadReconciliationReport report = service.reconcileNow("audit:revoke-reconcile");

        assertThat(report.state()).isEqualTo(State.CONVERGED);
        assertThat(report.counts().authoritativeInactiveCells()).isEqualTo(1);
        assertThat(report.counts().inactiveConverged()).isEqualTo(1);
        assertThat(identity.observation("provider-exact").enabled()).isFalse();
        assertThat(credentials.find(CLIENT)).isEmpty();
    }

    @Test
    void providerFailureProducesCachedUnavailableEvidenceWithoutRawErrors() throws Exception {
        identity.failScans = true;

        RuntimeWorkloadReconciliationReport report = service.reconcileNow("audit:outage");

        assertThat(report.state()).isEqualTo(State.UNAVAILABLE);
        assertThat(report.blockers()).contains(Blocker.PROVIDER_UNAVAILABLE, Blocker.RECONCILE_FAILURE);
        assertThat(report.nextReconcileAt()).isEqualTo(NOW.plusSeconds(60));
        String serialized = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(report);
        assertThat(serialized).doesNotContain("private-provider-diagnostic");
        int scans = identity.scans.get();
        assertThat(service.supportSafeSnapshot()).isEqualTo(report);
        assertThat(identity.scans).hasValue(scans);
    }

    @Test
    void staleCachedEvidenceBecomesBlockedWithoutPollingKeycloak() {
        MutableClock mutableClock = new MutableClock(NOW);
        service = new AgentRuntimeWorkloadReconciliationService(
                cells,
                (runtimeCell, auditRef) -> runtimeCell,
                identity,
                identity,
                credentials,
                new ProviderHealthProperties(
                        Duration.ofSeconds(60), Duration.ZERO, Duration.ofMinutes(5), Duration.ofMinutes(15)),
                new SimpleMeterRegistry(),
                mutableClock,
                () -> 0L);
        assertThat(service.reconcileNow("audit:fresh").state()).isEqualTo(State.CONVERGED);
        int scans = identity.scans.get();

        mutableClock.advance(Duration.ofMinutes(6));
        RuntimeWorkloadReconciliationReport stale = service.supportSafeSnapshot();

        assertThat(stale.state()).isEqualTo(State.BLOCKED);
        assertThat(stale.blockers()).containsExactly(Blocker.STALE_OBSERVATION);
        assertThat(identity.scans).hasValue(scans);
    }

    private ClientObservation exactObservation(boolean enabled) {
        RuntimeWorkloadCredentialState credential = credentials.find(CLIENT).orElseThrow();
        return managedObservation(
                "provider-exact",
                CLIENT,
                owner(),
                RuntimeWorkloadOwnership.fingerprint(ORGANIZATION),
                RuntimeWorkloadOwnership.fingerprint(PERSON),
                RuntimeWorkloadOwnership.fingerprint(CELL),
                SUBJECT,
                enabled,
                credential.acceptedKeyIds());
    }

    private static ClientObservation managedObservation(
            String providerRef,
            String clientId,
            String owner,
            String organization,
            String person,
            String cell,
            String subject,
            boolean enabled,
            Set<String> keyIds) {
        return new ClientObservation(
                providerRef,
                clientId,
                enabled,
                ManagementState.MANAGED,
                owner,
                organization,
                person,
                cell,
                true,
                subject,
                "client-jwt",
                keyIds);
    }

    private static String owner() {
        return RuntimeWorkloadOwnership.ownerFingerprint(ORGANIZATION, PERSON, CELL, CLIENT);
    }

    private static final class FakeIdentityBoundary
            implements RuntimeWorkloadIdentityAdmin, RuntimeWorkloadIdentityInventory {
        private final RuntimeWorkloadCredentialStore credentials;
        private final Map<String, ClientObservation> clients = new LinkedHashMap<>();
        private final AtomicInteger scans = new AtomicInteger();
        private final AtomicInteger reconciliations = new AtomicInteger();
        private final AtomicInteger quarantines = new AtomicInteger();
        private boolean failScans;

        private FakeIdentityBoundary(RuntimeWorkloadCredentialStore credentials) {
            this.credentials = credentials;
        }

        void put(ClientObservation observation) {
            clients.put(observation.providerRef(), observation);
        }

        void remove(String providerRef) {
            clients.remove(providerRef);
        }

        ClientObservation observation(String providerRef) {
            return clients.get(providerRef);
        }

        @Override
        public Snapshot scan() {
            scans.incrementAndGet();
            if (failScans) {
                throw new IllegalStateException("private-provider-diagnostic");
            }
            List<ClientObservation> values = new ArrayList<>(clients.values());
            String revision = RuntimeWorkloadOwnership.fingerprint(values.toString());
            return new Snapshot(revision, values);
        }

        @Override
        public void quarantineManaged(QuarantineManagedCommand command) {
            ClientObservation current = clients.get(command.providerRef());
            if (current == null
                    || current.managementState() != ManagementState.MANAGED
                    || !current.clientId().equals(command.clientId())
                    || !current.ownerFingerprint().equals(command.ownerFingerprint())) {
                throw new IllegalStateException("changed quarantine target");
            }
            quarantines.incrementAndGet();
            put(withEnabled(current, false, current.acceptedKeyIds()));
        }

        @Override
        public RuntimeWorkloadBinding ensureBinding(EnsureBindingCommand command) {
            throw new UnsupportedOperationException("not used by reconciliation tests");
        }

        @Override
        public RuntimeWorkloadBinding reconcileBinding(ReconcileBindingCommand command) {
            reconciliations.incrementAndGet();
            ClientObservation current = clients.values().stream()
                    .filter(candidate -> candidate.clientId().equals(command.binding().clientId()))
                    .findFirst()
                    .orElseThrow();
            Set<String> keyIds = credentials.find(command.binding().clientId()).orElseThrow().acceptedKeyIds();
            put(withEnabled(current, true, keyIds));
            return command.binding();
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
        public void disableBinding(DisableBindingCommand command) {
            clients.values().stream()
                    .filter(candidate -> candidate.clientId().equals(command.binding().clientId()))
                    .findFirst()
                    .ifPresent(current -> put(withEnabled(current, false, current.acceptedKeyIds())));
            credentials.delete(new RuntimeWorkloadCredentialStore.DeleteCredentialCommand(
                    command.binding().clientId(), RuntimeWorkloadOwnership.ownerFingerprint(
                            command.organizationRef(), command.personRef(), command.cellRef(),
                            command.binding().clientId())));
        }

        @Override
        public void deleteBinding(DeleteBindingCommand command) {
            clients.values().removeIf(candidate -> candidate.clientId().equals(command.binding().clientId()));
        }

        private static ClientObservation withEnabled(
                ClientObservation current,
                boolean enabled,
                Set<String> acceptedKeyIds) {
            return new ClientObservation(
                    current.providerRef(), current.clientId(), enabled, current.managementState(),
                    current.ownerFingerprint(), current.organizationFingerprint(), current.personFingerprint(),
                    current.cellFingerprint(), current.serviceAccountsEnabled(), current.serviceAccountSubject(),
                    current.authenticationMethod(), acceptedKeyIds);
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MutableClock(Instant initial) {
            now = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            now.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Test clock is UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
