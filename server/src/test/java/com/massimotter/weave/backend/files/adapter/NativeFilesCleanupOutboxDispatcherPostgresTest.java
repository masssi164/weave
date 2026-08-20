package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.FilesDigests;
import com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupCoordinator;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository.CleanupLease;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository.RetryOutcome;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.operation.adapter.JpaOperationIntentRepository;
import com.massimotter.weave.backend.operation.adapter.OperationIntentJpaTestFactory;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@Tag("postgres")
class NativeFilesCleanupOutboxDispatcherPostgresTest {
    private static final Instant NOW = Instant.parse("2026-08-20T15:00:00Z");
    private static final String DIGEST = FilesDigests.sha256("outbox-fixture");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void leasesOnlyReservedFilesFailureWorkAndFencesExpiredLeaseRecovery() throws Exception {
        DriverManagerDataSource dataSource = migratedDataSource();
        insertFailedPlan(
                dataSource,
                "op-eligible",
                "outbox-stable",
                "private/eligible",
                "operation.failed");
        insertFailedPlan(
                dataSource,
                "op-wrong-event",
                "outbox-wrong-event",
                "private/wrong-event",
                "external.failed");
        insertNoPlanFailure(dataSource, "op-no-plan", "outbox-no-plan");
        JpaOperationIntentRepository first = OperationIntentJpaTestFactory.create(dataSource);
        JpaOperationIntentRepository second = OperationIntentJpaTestFactory.create(dataSource);

        CountDownLatch start = new CountDownLatch(1);
        List<List<CleanupLease>> competing;
        try (var workers = Executors.newFixedThreadPool(2)) {
            var firstLease = workers.submit(() -> {
                start.await();
                return first.leaseBatch(
                        NOW, NOW.plusSeconds(30), "worker-first", 10, 10);
            });
            var secondLease = workers.submit(() -> {
                start.await();
                return second.leaseBatch(
                        NOW, NOW.plusSeconds(30), "worker-second", 10, 10);
            });
            start.countDown();
            competing = List.of(firstLease.get(), secondLease.get());
        }
        List<CleanupLease> firstRound = competing.stream().flatMap(List::stream).toList();

        assertThat(firstRound).singleElement().satisfies(lease -> {
            assertThat(lease.outboxRef()).isEqualTo("outbox-stable");
            assertThat(lease.operationRef()).isEqualTo("op-eligible");
            assertThat(lease.eventType()).isEqualTo("operation.failed");
            assertThat(lease.attemptCount()).isEqualTo(1);
            assertThat(lease.leaseToken()).isNotBlank();
            assertThat(lease.leaseOwner()).isIn("worker-first", "worker-second");
        });
        CleanupLease expired = firstRound.getFirst();
        JdbcTemplate claimedJdbc = new JdbcTemplate(dataSource);
        assertThat(claimedJdbc.queryForMap(
                        "select available_at_utc, lease_token, lease_owner, lease_until_utc "
                                + "from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-stable'"))
                .containsEntry("lease_token", expired.leaseToken())
                .containsEntry("lease_owner", expired.leaseOwner());
        assertThat(claimedJdbc.queryForObject(
                        "select available_at_utc from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-stable'",
                        java.time.OffsetDateTime.class))
                .isEqualTo(expired.leaseUntil().atOffset(ZoneOffset.UTC));
        assertThat(claimedJdbc.queryForObject(
                        "select lease_until_utc from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-stable'",
                        java.time.OffsetDateTime.class))
                .isEqualTo(expired.leaseUntil().atOffset(ZoneOffset.UTC));
        assertThat(first.leaseBatch(
                        NOW.plusSeconds(29),
                        NOW.plusSeconds(59),
                        "worker-early",
                        10,
                        10))
                .isEmpty();
        assertThat(first.markDelivered(expired, NOW.plusSeconds(31))).isFalse();
        assertThat(first.retry(
                        expired,
                        NOW.plusSeconds(31),
                        NOW.plusSeconds(40),
                        "cleanup-incomplete",
                        10))
                .isEqualTo(RetryOutcome.STALE_LEASE);

        CleanupLease recovered = second.leaseBatch(
                        NOW.plusSeconds(31),
                        NOW.plusSeconds(61),
                        "worker-recovery",
                        10,
                        10)
                .getFirst();

        assertThat(recovered.outboxRef()).isEqualTo(expired.outboxRef());
        assertThat(recovered.attemptCount()).isEqualTo(2);
        assertThat(recovered.leaseToken()).isNotEqualTo(expired.leaseToken());
        assertThat(recovered.leaseOwner()).isEqualTo("worker-recovery");
        assertThat(first.markDelivered(expired, NOW.plusSeconds(32))).isFalse();
        assertThat(second.retry(
                        recovered,
                        NOW.plusSeconds(32),
                        NOW.plusSeconds(40),
                        "cleanup-incomplete",
                        10))
                .isEqualTo(RetryOutcome.REQUEUED);
        JdbcTemplate leaseJdbc = new JdbcTemplate(dataSource);
        assertThat(leaseJdbc.queryForMap(
                        "select available_at_utc, lease_token, lease_owner, "
                                + "lease_until_utc, last_diagnostic_code "
                                + "from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-stable'"))
                .containsEntry("last_diagnostic_code", "cleanup-incomplete")
                .containsEntry("lease_token", null)
                .containsEntry("lease_owner", null)
                .containsEntry("lease_until_utc", null);
        assertThat(leaseJdbc.queryForObject(
                        "select available_at_utc from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-stable'",
                        java.time.OffsetDateTime.class))
                .isEqualTo(NOW.plusSeconds(40).atOffset(ZoneOffset.UTC));
        assertThat(first.leaseBatch(
                        NOW.plusSeconds(39),
                        NOW.plusSeconds(69),
                        "worker-too-early",
                        10,
                        10))
                .isEmpty();
        CleanupLease third = first.leaseBatch(
                        NOW.plusSeconds(40),
                        NOW.plusSeconds(70),
                        "worker-third",
                        10,
                        10)
                .getFirst();
        assertThat(third.attemptCount()).isEqualTo(3);
        assertThat(third.leaseToken())
                .isNotIn(expired.leaseToken(), recovered.leaseToken());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                """
                insert into weave_files_blob_cleanup_dispositions (
                    operation_ref, binding_digest, disposition_version,
                    private_blob_binding, disposition, recorded_at_utc)
                values (?, ?, 'weave.files-blob-cleanup-disposition/v1', ?,
                        'ALREADY_ABSENT', ?)
                """,
                "op-eligible",
                FilesDigests.sha256("private/eligible"),
                "private/eligible",
                NOW.plusSeconds(41).atOffset(ZoneOffset.UTC));
        assertThat(first.markDelivered(third, NOW.plusSeconds(41))).isTrue();
        assertThat(first.leaseBatch(
                        NOW.plusSeconds(100),
                        NOW.plusSeconds(130),
                        "worker-after-delivery",
                        10,
                        10))
                .isEmpty();
        assertThat(jdbc.queryForObject(
                        "select delivery_state from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-stable'",
                        String.class))
                .isEqualTo("DELIVERED");
        assertThat(jdbc.queryForMap(
                        "select available_at_utc, lease_token, lease_owner, "
                                + "lease_until_utc, last_diagnostic_code "
                                + "from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-stable'"))
                .containsEntry("available_at_utc", null)
                .containsEntry("lease_token", null)
                .containsEntry("lease_owner", null)
                .containsEntry("lease_until_utc", null)
                .containsEntry("last_diagnostic_code", null);
        assertThat(jdbc.queryForObject(
                        "select count(*) from weave_operation_outbox "
                                + "where delivery_state = 'PENDING'",
                        Long.class))
                .isEqualTo(2L);
    }

    @Test
    void recoversCompletedCleanupAfterCrashBeforeOutboxSettlement() throws Exception {
        DriverManagerDataSource dataSource = migratedDataSource();
        insertFailedPlan(
                dataSource,
                "op-complete-crash",
                "outbox-complete-crash",
                "private/complete-crash",
                "operation.failed");
        JpaOperationIntentRepository operations = OperationIntentJpaTestFactory.create(dataSource);
        NativeFilesBlobCleanupCoordinator cleanup = JpaTestDatabase.transactional(
                dataSource,
                new NativeFilesBlobCleanupCoordinator(
                        cleanupRepository(dataSource),
                        new AbsentBlobStore()));

        CleanupLease crashed = operations.leaseBatch(
                        NOW,
                        NOW.plusSeconds(30),
                        "worker-crashed-after-cleanup",
                        1,
                        1)
                .getFirst();
        var completedBeforeCrash = cleanup.process("op-complete-crash", 100);

        assertThat(completedBeforeCrash.complete()).isTrue();
        assertThat(completedBeforeCrash.processedCount()).isEqualTo(1);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                        "select delivery_state from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-complete-crash'",
                        String.class))
                .isEqualTo("DELIVERING");

        CleanupLease recovered = operations.leaseBatch(
                        NOW.plusSeconds(31),
                        NOW.plusSeconds(61),
                        "worker-recovered-complete-cleanup",
                        1,
                        1)
                .getFirst();
        var revalidated = cleanup.process("op-complete-crash", 100);

        assertThat(recovered.attemptCount()).isEqualTo(1);
        assertThat(recovered.leaseToken()).isNotEqualTo(crashed.leaseToken());
        assertThat(revalidated.complete()).isTrue();
        assertThat(revalidated.recordedCount()).isEqualTo(1);
        assertThat(revalidated.processedCount()).isZero();
        assertThat(operations.markDelivered(crashed, NOW.plusSeconds(32))).isFalse();
        assertThat(operations.markDelivered(recovered, NOW.plusSeconds(32))).isTrue();
        assertThat(jdbc.queryForObject(
                        "select delivery_state from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-complete-crash'",
                        String.class))
                .isEqualTo("DELIVERED");
    }

    @Test
    void stopsRetryingAtTheBoundedAttemptLimit() throws Exception {
        DriverManagerDataSource dataSource = migratedDataSource();
        insertFailedPlan(
                dataSource,
                "op-bounded",
                "outbox-bounded",
                "private/bounded",
                "operation.failed");
        JpaOperationIntentRepository operations = OperationIntentJpaTestFactory.create(dataSource);
        CleanupLease lease = operations.leaseBatch(
                NOW, NOW.plusSeconds(30), "worker-bounded", 1, 1).getFirst();

        assertThat(operations.retry(
                        lease,
                        NOW.plusSeconds(31),
                        NOW.plusSeconds(32),
                        "cleanup-execution-failed",
                        1))
                .isEqualTo(RetryOutcome.STALE_LEASE);
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                        "select delivery_state from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-bounded'",
                        String.class))
                .isEqualTo("DELIVERING");
        CleanupLease recovered = operations.leaseBatch(
                NOW.plusSeconds(31),
                NOW.plusSeconds(61),
                "worker-bounded-recovery",
                1,
                1).getFirst();
        assertThat(recovered.attemptCount()).isEqualTo(1);
        assertThat(recovered.leaseToken()).isNotEqualTo(lease.leaseToken());
        assertThat(operations.retry(
                        recovered,
                        NOW.plusSeconds(32),
                        NOW.plusSeconds(33),
                        "cleanup-execution-failed",
                        1))
                .isEqualTo(RetryOutcome.FAILED_CLOSED);
        assertThat(operations.leaseBatch(
                        NOW.plusSeconds(31),
                        NOW.plusSeconds(61),
                        "worker-after-failure",
                        1,
                        1))
                .isEmpty();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                        "select delivery_state from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-bounded'",
                        String.class))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForMap(
                        "select available_at_utc, lease_token, lease_owner, "
                                + "lease_until_utc, last_diagnostic_code "
                                + "from weave_operation_outbox "
                                + "where outbox_ref = 'outbox-bounded'"))
                .containsEntry("available_at_utc", null)
                .containsEntry("lease_token", null)
                .containsEntry("lease_owner", null)
                .containsEntry("lease_until_utc", null)
                .containsEntry("last_diagnostic_code", "cleanup-execution-failed");
    }

    @Test
    void ordersDueWorkByAvailabilityThenSequenceAndRejectsUnsafeDiagnostics() throws Exception {
        DriverManagerDataSource dataSource = migratedDataSource();
        insertFailedPlan(
                dataSource,
                "op-order-later",
                "outbox-order-later",
                "private/order-later",
                "operation.failed");
        insertFailedPlan(
                dataSource,
                "op-order-early-first",
                "outbox-order-early-first",
                "private/order-early-first",
                "operation.failed");
        insertFailedPlan(
                dataSource,
                "op-order-early-second",
                "outbox-order-early-second",
                "private/order-early-second",
                "operation.failed");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                "update weave_operation_outbox set available_at_utc = ? where outbox_ref = ?",
                NOW.minusSeconds(1).atOffset(ZoneOffset.UTC),
                "outbox-order-later");
        for (String outboxRef : List.of(
                "outbox-order-early-first",
                "outbox-order-early-second")) {
            jdbc.update(
                    "update weave_operation_outbox set available_at_utc = ? where outbox_ref = ?",
                    NOW.minusSeconds(10).atOffset(ZoneOffset.UTC),
                    outboxRef);
        }

        JpaOperationIntentRepository operations = OperationIntentJpaTestFactory.create(dataSource);
        List<CleanupLease> leases = operations.leaseBatch(
                NOW,
                NOW.plusSeconds(30),
                "worker-order",
                3,
                1);

        assertThat(leases).extracting(CleanupLease::outboxRef).containsExactly(
                "outbox-order-early-first",
                "outbox-order-early-second",
                "outbox-order-later");
        for (CleanupLease lease : leases) {
            assertThat(operations.retry(
                            lease,
                            NOW.plusSeconds(1),
                            NOW.plusSeconds(1),
                            "cleanup-order-test",
                            1))
                    .isEqualTo(RetryOutcome.FAILED_CLOSED);
        }
        assertThatThrownBy(() -> jdbc.update(
                        "update weave_operation_outbox "
                                + "set last_diagnostic_code = 'unsafe diagnostic value' "
                                + "where outbox_ref = 'outbox-order-later'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertNoPlanFailure(
            DriverManagerDataSource dataSource,
            String operationRef,
            String outboxRef) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertIntent(connection, operationRef, outboxRef);
            markFailed(connection, operationRef);
            insertOutbox(connection, operationRef, outboxRef, "operation.failed");
            connection.commit();
        }
    }

    private void insertFailedPlan(
            DriverManagerDataSource dataSource,
            String operationRef,
            String outboxRef,
            String binding,
            String eventType) throws Exception {
        FilesMutationTargetCodec codec = mutationCodec();
        Target targetProjection = targetProjection(binding);
        FilesMutationPlan.Fence fenceProjection = FilesMutationPlan.Fence.absent(
                0,
                FilesMutationPlan.FenceRole.REQUEST_TARGET,
                "/file-1");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertIntent(connection, operationRef, outboxRef);
            try (var head = connection.prepareStatement("""
                    insert into weave_files_stream_heads (
                        organization_ref, space_ref, latest_revision,
                        reset_required_floor, lock_version, updated_at_utc)
                    values ('org-1', 'space-1', 0, 0, 0, ?)
                    on conflict (organization_ref, space_ref) do nothing
                    """)) {
                head.setObject(1, NOW.atOffset(ZoneOffset.UTC));
                head.executeUpdate();
            }
            try (var plan = connection.prepareStatement("""
                    insert into weave_files_mutation_plans (
                        operation_ref, organization_ref, space_ref, plan_version,
                        canonical_arguments_digest, operation_kind,
                        provider_binding_revision, if_match_condition,
                        if_none_match_condition, destination_must_remain_absent,
                        plan_state, target_count, targets_digest,
                        fence_count, fences_digest, sealed_at_utc)
                    values (?, 'org-1', 'space-1', 'weave.files-mutation-plan/v1', ?,
                            'PUT', 1, 'NOT_SUPPLIED', 'NOT_SUPPLIED', false,
                            'OPEN', 1, ?, 1, ?, null)
                    """)) {
                plan.setString(1, operationRef);
                plan.setString(2, DIGEST);
                plan.setString(3, codec.targetsDigest(List.of(targetProjection)));
                plan.setString(4, codec.fencesDigest(List.of(fenceProjection)));
                plan.executeUpdate();
            }
            try (var target = connection.prepareStatement("""
                    insert into weave_files_mutation_targets (
                        operation_ref, target_ordinal, target_version, change_kind,
                        target_file_ref, target_path, object_kind, result_lifecycle_state,
                        result_blob_binding, result_size, result_content_digest,
                        result_file_version, result_strong_etag, result_modified_at_utc,
                        result_hidden, result_observed_at_utc)
                    values (?, 0, 'weave.files-mutation-target/v1', 'CREATED',
                            'file-1', '/file-1', 'FILE', 'ACTIVE', ?, 0, ?,
                            'version-1', 'etag-1', ?, false, ?)
                    """)) {
                target.setString(1, operationRef);
                target.setString(2, binding);
                target.setString(3, DIGEST);
                target.setObject(4, NOW.atOffset(ZoneOffset.UTC));
                target.setObject(5, NOW.atOffset(ZoneOffset.UTC));
                target.executeUpdate();
            }
            try (var fence = connection.prepareStatement("""
                    insert into weave_files_mutation_fences (
                        operation_ref, fence_ordinal, fence_version, fence_role,
                        canonical_path, expected_presence, snapshot_digest)
                    values (?, 0, 'weave.files-mutation-fence/v1',
                            'REQUEST_TARGET', '/file-1', 'ABSENT', ?)
                    """)) {
                fence.setString(1, operationRef);
                fence.setString(2, fenceProjection.snapshotDigest());
                fence.executeUpdate();
            }
            try (var seal = connection.prepareStatement("""
                    update weave_files_mutation_plans
                    set plan_state = 'SEALED', sealed_at_utc = ?
                    where operation_ref = ?
                    """)) {
                seal.setObject(1, NOW.atOffset(ZoneOffset.UTC));
                seal.setString(2, operationRef);
                seal.executeUpdate();
            }
            markFailed(connection, operationRef);
            insertOutbox(connection, operationRef, outboxRef, eventType);
            connection.commit();
        }
    }

    private void insertIntent(
            Connection connection,
            String operationRef,
            String outboxRef) throws Exception {
        try (var intent = connection.prepareStatement("""
                insert into weave_operation_intents (
                    operation_ref, action_digest, actor_kind, audit_ref,
                    canonical_arguments_digest, created_at_utc, domain_key,
                    entitlement_revision, idempotency_key, initial_outbox_ref,
                    intent_version, object_refs_json, organization_ref, person_ref,
                    policy_revision, projection_kind, projection_value_1,
                    projection_value_2, projection_value_3, provider_binding_revision,
                    reconciliation_attempts, reconciliation_max_attempts,
                    result_digest, intent_state, subject_ref, updated_at_utc, version)
                values (?, ?, 'human', 'audit-1', ?, ?, 'files', 'entitlement-1', ?, ?,
                        'weave.operation-intent/v2', '[]', 'org-1', 'person-1',
                        'policy-1', 'protocol', 'webdav', 'webdav-put',
                        'weave.webdav.files/v1', 1, 0, 5,
                        ?, 'CREATED', 'subject-1', ?, 0)
                """)) {
            intent.setString(1, operationRef);
            intent.setString(2, DIGEST);
            intent.setString(3, DIGEST);
            intent.setObject(4, NOW.atOffset(ZoneOffset.UTC));
            intent.setString(5, "idempotency-" + operationRef);
            intent.setString(6, outboxRef);
            intent.setString(7, DIGEST);
            intent.setObject(8, NOW.atOffset(ZoneOffset.UTC));
            intent.executeUpdate();
        }
    }

    private void markFailed(Connection connection, String operationRef) throws Exception {
        try (var fail = connection.prepareStatement("""
                update weave_operation_intents
                set intent_state = 'FAILED', updated_at_utc = ?
                where operation_ref = ?
                """)) {
            fail.setObject(1, NOW.plusSeconds(1).atOffset(ZoneOffset.UTC));
            fail.setString(2, operationRef);
            fail.executeUpdate();
        }
    }

    private void insertOutbox(
            Connection connection,
            String operationRef,
            String outboxRef,
            String eventType) throws Exception {
        try (var outbox = connection.prepareStatement("""
                insert into weave_operation_outbox (
                    attempt_count, available_at_utc, created_at_utc,
                    delivery_state, event_type,
                    operation_ref, outbox_ref, payload_json, version)
                values (0, ?, ?, 'PENDING', ?, ?, ?, '{}', 0)
                """)) {
            outbox.setObject(1, NOW.atOffset(ZoneOffset.UTC));
            outbox.setObject(2, NOW.plusSeconds(1).atOffset(ZoneOffset.UTC));
            outbox.setString(3, eventType);
            outbox.setString(4, operationRef);
            outbox.setString(5, outboxRef);
            outbox.executeUpdate();
        }
    }

    private JpaNativeFilesBlobCleanupRepository cleanupRepository(
            DriverManagerDataSource dataSource) {
        return new JpaNativeFilesBlobCleanupRepository(
                JpaTestDatabase.repository(dataSource, FilesStreamHeadJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesMutationPlanJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesMutationTargetJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesMutationFenceJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesChangeJpaRepository.class),
                JpaTestDatabase.repository(
                        dataSource,
                        FilesBlobCleanupDispositionJpaRepository.class),
                OperationIntentJpaTestFactory.create(dataSource),
                mutationCodec());
    }

    private FilesMutationTargetCodec mutationCodec() {
        return new FilesMutationTargetCodec(
                JsonMapper.builder().findAndAddModules().build());
    }

    private Target targetProjection(String binding) {
        return new Target(
                0,
                ChangeKind.CREATED,
                null,
                "file-1",
                null,
                "/file-1",
                Kind.FILE,
                Lifecycle.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                binding,
                0,
                null,
                DIGEST,
                "version-1",
                "etag-1",
                NOW,
                false,
                NOW);
    }

    private static final class AbsentBlobStore implements BlobStorePort {
        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public BlobReceipt putStream(
                BlobScope scope,
                BlobReference reference,
                InputStream source,
                long expectedSize,
                String expectedDigest) {
            throw new AssertionError("cleanup must not publish blobs");
        }

        @Override
        public void readStream(
                BlobScope scope,
                BlobReference reference,
                OutputStream target) {
            throw new AssertionError("cleanup must not read blob content");
        }

        @Override
        public Optional<BlobReceipt> receipt(BlobScope scope, BlobReference reference) {
            return Optional.empty();
        }

        @Override
        public void delete(BlobScope scope, BlobReference reference) {
            // The fixture models an already-absent binding.
        }

        @Override
        public List<BlobReference> inventory(BlobScope scope, int limit) {
            return List.of();
        }
    }

    private DriverManagerDataSource migratedDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }
}
