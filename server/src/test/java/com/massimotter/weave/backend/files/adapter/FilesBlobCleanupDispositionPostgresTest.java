package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.CleanupWork;
import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.Disposition;
import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.ReferenceStatus;
import com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupException;
import com.massimotter.weave.backend.files.application.FilesDigests;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.operation.adapter.OperationIntentJpaTestFactory;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@Tag("postgres")
class FilesBlobCleanupDispositionPostgresTest {
    private static final String DIGEST = FilesDigests.sha256("fixture");
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void v7AcceptsOnlyExactImmutableDispositionRowsForTheReservedFailureWork() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        JpaTestDatabase.validateSchema(dataSource);
        insertFailedPlan(dataSource, "op-failed", "outbox-failed", "private/result");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String bindingDigest = FilesDigests.sha256("private/result");

        insertNonterminalPlan(dataSource, "op-protect", "outbox-protect", "private/result");
        jdbc.update(
                """
                insert into weave_files_objects (
                    file_id, organization_ref, space_ref, active_path_key, byte_size,
                    canonical_path, content_digest, hidden, object_kind, lifecycle_state,
                    media_type, modified_at_utc, observed_at_utc,
                    provider_binding_revision, storage_reference, version, version_token)
                values ('canonical-1', 'org-1', 'space-1', '/canonical-1', 0,
                        '/canonical-1', ?, false, 'FILE', 'ACTIVE', null, ?, ?,
                        1, 'private/result', 0, 'version-1')
                """,
                DIGEST,
                NOW.atOffset(ZoneOffset.UTC),
                NOW.atOffset(ZoneOffset.UTC));
        JpaNativeFilesBlobCleanupRepository adapter = adapter(dataSource);
        CleanupWork work = new CleanupWork(
                "op-failed",
                new BlobScope("org-1", "space-1"),
                List.of(new BlobReference("private/result")));

        assertThat(adapter.recheck(work, new BlobReference("private/result")))
                .isEqualTo(ReferenceStatus.STILL_REFERENCED);
        jdbc.update("delete from weave_files_objects where file_id = 'canonical-1'");
        assertThat(adapter.recheck(work, new BlobReference("private/result")))
                .isEqualTo(ReferenceStatus.STILL_PROTECTED);
        FilesMutationTargetJpaRepository targetRepository = JpaTestDatabase.repository(
                dataSource,
                FilesMutationTargetJpaRepository.class);
        assertThat(targetRepository.findTerminalFailureTargetsWithIncompleteCleanup(
                        "org-1",
                        "space-1",
                        List.of("DENIED", "FAILED")))
                .hasSize(1);

        jdbc.update(
                """
                insert into weave_files_blob_cleanup_dispositions (
                    operation_ref, binding_digest, disposition_version,
                    private_blob_binding, disposition, recorded_at_utc)
                values (?, ?, 'weave.files-blob-cleanup-disposition/v1', ?, 'DELETED', ?)
                """,
                "op-failed",
                bindingDigest,
                "private/result",
                NOW.atOffset(ZoneOffset.UTC));

        assertThat(targetRepository.findTerminalFailureTargetsWithIncompleteCleanup(
                        "org-1",
                        "space-1",
                        List.of("DENIED", "FAILED")))
                .isEmpty();
        adapter.record(
                work,
                new BlobReference("private/result"),
                bindingDigest,
                Disposition.DELETED,
                NOW.plusSeconds(1));
        assertThatThrownBy(() -> adapter.record(
                        work,
                        new BlobReference("private/result"),
                        bindingDigest,
                        Disposition.ALREADY_ABSENT,
                        NOW.plusSeconds(1)))
                .isInstanceOf(NativeFilesBlobCleanupException.class)
                .hasMessage("Files cleanup disposition retry is contradictory");

        assertThat(jdbc.queryForObject(
                        "select disposition from weave_files_blob_cleanup_dispositions "
                                + "where operation_ref = 'op-failed'",
                        String.class))
                .isEqualTo("DELETED");
        assertThatThrownBy(() -> jdbc.update(
                        "update weave_files_blob_cleanup_dispositions "
                                + "set disposition = 'ALREADY_ABSENT' "
                                + "where operation_ref = 'op-failed'"))
                .hasMessageContaining("insert-only");
        assertThatThrownBy(() -> jdbc.update(
                        "delete from weave_files_blob_cleanup_dispositions "
                                + "where operation_ref = 'op-failed'"))
                .hasMessageContaining("insert-only");

        insertFailedPlan(dataSource, "op-unplanned", "outbox-unplanned", "private/planned");
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into weave_files_blob_cleanup_dispositions (
                            operation_ref, binding_digest, disposition_version,
                            private_blob_binding, disposition, recorded_at_utc)
                        values (?, ?, 'weave.files-blob-cleanup-disposition/v1', ?, 'STILL_PROTECTED', ?)
                        """,
                        "op-unplanned",
                        FilesDigests.sha256("private/unplanned"),
                        "private/unplanned",
                        NOW.atOffset(ZoneOffset.UTC)))
                .hasMessageContaining("not in the sealed plan");
        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into weave_files_blob_cleanup_dispositions (
                            operation_ref, binding_digest, disposition_version,
                            private_blob_binding, disposition, recorded_at_utc)
                        values (?, ?, 'weave.files-blob-cleanup-disposition/v1', ?, 'STILL_REFERENCED', ?)
                        """,
                        "op-unplanned",
                        FilesDigests.sha256("wrong-binding"),
                        "private/planned",
                        NOW.atOffset(ZoneOffset.UTC)))
                .hasMessageContaining("exact_digest");
    }

    private void insertFailedPlan(
            DriverManagerDataSource dataSource,
            String operationRef,
            String outboxRef,
            String binding) throws Exception {
        insertPlan(dataSource, operationRef, outboxRef, binding, true);
    }

    private void insertNonterminalPlan(
            DriverManagerDataSource dataSource,
            String operationRef,
            String outboxRef,
            String binding) throws Exception {
        insertPlan(dataSource, operationRef, outboxRef, binding, false);
    }

    private void insertPlan(
            DriverManagerDataSource dataSource,
            String operationRef,
            String outboxRef,
            String binding,
            boolean terminalFailure) throws Exception {
        Target targetProjection = new Target(
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
        var fence = FilesMutationPlan.Fence.absent(
                0, FilesMutationPlan.FenceRole.REQUEST_TARGET, "/file-1");
        FilesMutationTargetCodec codec = mutationCodec();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var intent = connection.prepareStatement("""
                    insert into weave_operation_intents (
                        operation_ref, action_digest, actor_kind, audit_ref,
                        canonical_arguments_digest, created_at_utc, domain_key,
                        entitlement_revision, idempotency_key, initial_outbox_ref,
                        intent_version, object_refs_json, organization_ref, person_ref,
                        policy_revision, projection_kind, projection_value_1,
                        projection_value_2, provider_binding_revision,
                        reconciliation_attempts, reconciliation_max_attempts,
                        result_digest, intent_state, subject_ref, updated_at_utc, version)
                    values (?, ?, 'human', 'audit-1', ?, ?, 'files', 'entitlement-1', ?, ?,
                            'weave.operation-intent/v2', '[]', 'org-1', 'person-1',
                            'policy-1', 'protocol', 'webdav', 'PUT', 1, 0, 5,
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
                plan.setString(4, codec.fencesDigest(List.of(fence)));
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
            try (var fenceRow = connection.prepareStatement("""
                    insert into weave_files_mutation_fences (
                        operation_ref, fence_ordinal, fence_version, fence_role,
                        canonical_path, expected_presence, snapshot_digest)
                    values (?, 0, 'weave.files-mutation-fence/v1', 'REQUEST_TARGET',
                            '/file-1', 'ABSENT', ?)
                    """)) {
                fenceRow.setString(1, operationRef);
                fenceRow.setString(2, fence.snapshotDigest());
                fenceRow.executeUpdate();
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
            if (terminalFailure) {
                try (var fail = connection.prepareStatement("""
                        update weave_operation_intents
                        set intent_state = 'FAILED', updated_at_utc = ?
                        where operation_ref = ?
                        """)) {
                    fail.setObject(1, NOW.plusSeconds(1).atOffset(ZoneOffset.UTC));
                    fail.setString(2, operationRef);
                    fail.executeUpdate();
                }
                try (var outbox = connection.prepareStatement("""
                        insert into weave_operation_outbox (
                            attempt_count, available_at_utc, created_at_utc,
                            delivery_state, event_type,
                            operation_ref, outbox_ref, payload_json, version)
                        values (0, ?, ?, 'PENDING', 'operation.failed', ?, ?, '{}', 0)
                        """)) {
                    outbox.setObject(1, NOW.plusSeconds(1).atOffset(ZoneOffset.UTC));
                    outbox.setObject(2, NOW.plusSeconds(1).atOffset(ZoneOffset.UTC));
                    outbox.setString(3, operationRef);
                    outbox.setString(4, outboxRef);
                    outbox.executeUpdate();
                }
            }
            connection.commit();
        }
    }

    private JpaNativeFilesBlobCleanupRepository adapter(DriverManagerDataSource dataSource) {
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

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
