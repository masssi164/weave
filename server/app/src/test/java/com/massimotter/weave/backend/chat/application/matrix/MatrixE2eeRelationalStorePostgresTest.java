package com.massimotter.weave.backend.chat.application.matrix;

import com.massimotter.weave.backend.chat.port.MatrixE2eePersistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.chat.persistence.MatrixE2eeJpaModel.OneTimeKey;
import com.massimotter.weave.backend.chat.persistence.MatrixE2eeRelationalStore;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import jakarta.persistence.LockModeType;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Actual normalized ORM behavior on disposable PostgreSQL, using the generated entity graph. */
@EnabledIfSystemProperty(named = "weave.test.postgres", matches = "true")
class MatrixE2eeRelationalStorePostgresTest {
    private static final String TENANT = "org-one";
    private static final String USER = "@member:weave.test";
    private static final String DEVICE = "DEVICE";

    @Test
    void generatedMappingsRetainPartialUniquenessChecksAndNullDefaults() {
        DataSource source = database("matrix-schema");
        JpaTestDatabase.validateSchema(source);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("insert into weave_matrix_devices(tenant_id,user_id,device_id) values (?,?,?)", TENANT, USER, DEVICE);
        var store = store(source);
        assertThat(store.device(TENANT, USER, DEVICE)).get().satisfies(device -> {
            assertThat(device.deviceKeys()).isEmpty(); assertThat(device.revoked()).isFalse(); assertThat(device.changedRevision()).isZero();
        });
        assertThatThrownBy(() -> jdbc.update("insert into weave_matrix_device_proofs(tenant_id,user_id,device_id,proof_hash) values (?,?,?,?)", TENANT, USER, DEVICE, "invalid"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into weave_matrix_cross_signing_keys(tenant_id,user_id,usage,key_id,key_json) values (?,?,?,?,?)", TENANT, USER, "invalid", "key", "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(store.createBackupVersion(TENANT, USER, "algo", Map.of())).isEqualTo("1");
        assertThat(store.createBackupVersion(TENANT, USER, "algo", Map.of())).isEqualTo("2");
        assertThatThrownBy(() -> jdbc.update("insert into weave_matrix_key_backup_versions(tenant_id,user_id,version_id,algorithm,auth_data_json,current_version) values (?,?,3,'algo','{}',true)", TENANT, USER))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where schemaname=current_schema() and indexname='weave_matrix_key_backup_current_idx'", String.class))
                .contains("UNIQUE INDEX", "tenant_id", "user_id", "WHERE current_version");
    }

    @Test
    void devicesKeysRevocationAndReadFiltersStayScopedAndOrdered() {
        DataSource source = database("matrix-device-orm"); var store = store(source);
        Map<String, Object> keys = Map.of("keys", Map.of("ed25519:" + DEVICE, "public-key"));
        store.upsertDevice(TENANT, USER, DEVICE, keys, 4);
        store.upsertDevice(TENANT, USER, "A", Map.of(), 2);
        store.upsertDevice("org-two", USER, DEVICE, Map.of("foreign", true), 9);
        store.upsertDevice(TENANT, USER, DEVICE, Map.of(), 5);
        assertThat(store.device(TENANT, USER, DEVICE)).get().extracting(MatrixE2eePersistence.DeviceRecord::deviceKeys).isEqualTo(keys);
        assertThat(store.devices(TENANT, USER, Set.of(DEVICE))).extracting(MatrixE2eePersistence.DeviceRecord::deviceId).containsExactly(DEVICE);
        assertThat(store.activeDeviceIds(TENANT, USER)).containsExactly("A", DEVICE);
        assertThat(store.deviceUsersChanged(TENANT, 2, 5)).containsExactly(USER);
        store.addOneTimeKeys(TENANT, USER, DEVICE, Map.of("signed_curve25519:k", Map.of("key", "public-otk")));
        store.replaceFallbackKeys(TENANT, USER, DEVICE, Map.of("signed_curve25519:f", Map.of("key", "public-fallback")), 6);
        assertThat(store.oneTimeKeyCounts(TENANT, USER, DEVICE)).containsEntry("signed_curve25519", 1L);
        assertThat(store.oneTimeKeyCounts("org-two", USER, DEVICE)).isEmpty();
        assertThat(store.unusedFallbackAlgorithms(TENANT, USER, DEVICE)).containsExactly("signed_curve25519");
        assertThat(store.claimOneTimeKey(TENANT, USER, DEVICE, "signed_curve25519")).get().satisfies(key -> {
            assertThat(key.keyId()).isEqualTo("signed_curve25519:k"); assertThat(key.fallback()).isFalse();
        });
        assertThat(store.claimOneTimeKey(TENANT, USER, DEVICE, "signed_curve25519")).get().extracting(MatrixE2eePersistence.ClaimedKey::fallback).isEqualTo(true);
        assertThat(store.unusedFallbackAlgorithms(TENANT, USER, DEVICE)).isEmpty();
        // Fallback keys remain reusable after marking used.
        assertThat(store.claimOneTimeKey(TENANT, USER, DEVICE, "signed_curve25519")).isPresent();
        store.revokeDevice(TENANT, USER, DEVICE, 7);
        assertThat(store.activeDeviceIds(TENANT, USER)).containsExactly("A");
        assertThat(store.claimOneTimeKey(TENANT, USER, DEVICE, "signed_curve25519")).isEmpty();
        assertThat(store.device("org-two", USER, DEVICE)).get().extracting(MatrixE2eePersistence.DeviceRecord::revoked).isEqualTo(false);
    }

    @Test
    void oneTimeKeyClaimSkipsAnotherTransactionsLockedFirstKey() throws Exception {
        DataSource source = database("matrix-key-lock"); var store = store(source);
        store.addOneTimeKeys(TENANT, USER, DEVICE, Map.of("curve:a", "a", "curve:b", "b"));
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var transaction = new TransactionTemplate(JpaTestDatabase.transactionManager(source));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holding = workers.submit(() -> transaction.execute(ignored -> {
                JpaTestDatabase.entityManager(source).createQuery("select e from MatrixOneTimeKey e where e.tenantId=?1 and e.userId=?2 and e.deviceId=?3 and e.keyId=?4", OneTimeKey.class)
                        .setParameter(1, TENANT).setParameter(2, USER).setParameter(3, DEVICE).setParameter(4, "curve:a")
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult();
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("key lock release timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return null;
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var claiming = workers.submit(() -> store.claimOneTimeKey(TENANT, USER, DEVICE, "curve"));
                assertThat(claiming.get(5, TimeUnit.SECONDS)).get().extracting(MatrixE2eePersistence.ClaimedKey::keyId).isEqualTo("curve:b");
            } finally { release.countDown(); }
            holding.get(5, TimeUnit.SECONDS);
        }
        assertThat(store.claimOneTimeKey(TENANT, USER, DEVICE, "curve")).get().extracting(MatrixE2eePersistence.ClaimedKey::keyId).isEqualTo("curve:a");
        assertThat(store.claimOneTimeKey(TENANT, USER, DEVICE, "curve")).isEmpty();
    }

    @Test
    void concurrentDuplicateEnvelopeHasOneRevisionAndDatabaseBoundedReadback() throws Exception {
        DataSource source = database("matrix-message-race"); var first = store(source); var second = store(source);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var one = workers.submit(() -> { start.await(); return first.appendToDevice(TENANT, USER, DEVICE, "@sender:weave.test", "m.room.encrypted", "same", Map.of("ciphertext", Map.of())); });
            var two = workers.submit(() -> { start.await(); return second.appendToDevice(TENANT, USER, DEVICE, "@sender:weave.test", "m.room.encrypted", "same", Map.of("ciphertext", Map.of())); });
            start.countDown(); assertThat(one.get(10, TimeUnit.SECONDS)).isEqualTo(1L); assertThat(two.get(10, TimeUnit.SECONDS)).isEqualTo(1L);
        }
        assertThat(first.currentRevision(TENANT)).isEqualTo(1);
        long secondRevision = second.appendToDevice(TENANT, USER, DEVICE, "@sender:weave.test", "m.room.encrypted", "next", Map.of("opaque", true));
        first.appendToDevice("org-two", USER, DEVICE, "@sender:weave.test", "m.room.encrypted", "same", Map.of("foreign", true));
        assertThat(first.toDeviceEvents(TENANT, USER, DEVICE, 0, secondRevision, 1)).extracting(MatrixE2eePersistence.ToDeviceRecord::revision).containsExactly(1L);
        assertThat(first.toDeviceEvents(TENANT, USER, DEVICE, 1, secondRevision, 10)).extracting(MatrixE2eePersistence.ToDeviceRecord::revision).containsExactly(secondRevision);
        assertThat(first.toDeviceEvents(TENANT, USER, "OTHER", 0, Long.MAX_VALUE, 10)).isEmpty();
        assertThat(first.toDeviceEvents(TENANT, USER, DEVICE, 0, 1, 10)).hasSize(1);
    }

    @Test
    void sharedUserJournalAndProgressPreserveTransitionsAndMonotonicHighWater() {
        DataSource source = database("matrix-progress"); var store = store(source);
        var initial = store.reconcileSharedUsers(TENANT, USER, DEVICE, Set.of("@one:weave.test"));
        long joined = initial.get("@one:weave.test").changedRevision();
        assertThat(store.reconcileSharedUsers(TENANT, USER, DEVICE, Set.of("@one:weave.test"))).isEmpty();
        var left = store.reconcileSharedUsers(TENANT, USER, DEVICE, Set.of()); long leftAt = left.get("@one:weave.test").changedRevision();
        store.reconcileSharedUsers(TENANT, USER, DEVICE, Set.of("@one:weave.test")); long highWater = store.currentRevision(TENANT);
        assertThat(store.sharedUserChanges(TENANT, USER, DEVICE, 0, joined)).containsEntry("@one:weave.test", new MatrixE2eePersistence.DeviceListState(true, joined));
        assertThat(store.sharedUserChanges(TENANT, USER, DEVICE, joined, leftAt)).containsEntry("@one:weave.test", new MatrixE2eePersistence.DeviceListState(false, leftAt));
        assertThat(store.sharedUserChanges(TENANT, USER, DEVICE, joined, highWater)).containsEntry("@one:weave.test", new MatrixE2eePersistence.DeviceListState(true, highWater));
        assertThat(store.sharedUserChanges("org-two", USER, DEVICE, 0, highWater)).isEmpty();
        store.recordDeviceSyncProgress(TENANT, USER, DEVICE, 9); store.recordDeviceListProgress(TENANT, USER, DEVICE, 7);
        store.recordDeviceSyncProgress(TENANT, USER, DEVICE, 2); store.recordDeviceListProgress(TENANT, USER, DEVICE, 3);
        assertThat(store.deviceListProgress(TENANT, USER, DEVICE)).isEqualTo(7);
        assertThatThrownBy(() -> store.recordDeviceSyncProgress(TENANT, USER, "NEW-NEGATIVE", -1)).hasStackTraceContaining("violates check constraint");
        assertThatThrownBy(() -> store.recordDeviceListProgress(TENANT, USER, "NEW-NEGATIVE-LIST", -1)).hasStackTraceContaining("violates check constraint");
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForMap("select to_device_sequence,device_list_revision,last_issued_revision,row_version from weave_matrix_device_sync_progress where tenant_id=? and user_id=? and device_id=?", TENANT, USER, DEVICE))
                .containsEntry("to_device_sequence", 9L).containsEntry("device_list_revision", 7L).containsEntry("last_issued_revision", 9L).containsEntry("row_version", 3L);
    }

    @Test
    void crossSigningAccountDataAndBackupRoundTripWithoutExposingManagedEntities() {
        DataSource source = database("matrix-json-values"); var store = store(source);
        Map<String, Object> master = Map.of("keys", Map.of("ed25519:master", "public-master"));
        store.upsertCrossSigning(TENANT, USER, master, Map.of(), Map.of(), 1);
        store.mergeCrossSigningSignatures(TENANT, USER, "public-master", Map.of(USER, Map.of("ed25519:" + DEVICE, "public-signature")), 2);
        assertThat(store.crossSigning(TENANT, USER)).get().satisfies(value -> {
            assertThat(value.masterKey()).containsKeys("keys", "signatures"); assertThat(value.selfSigningKey()).isEmpty();
        });
        assertThat(store.crossSigning("org-two", USER)).isEmpty();
        store.putAccountData(TENANT, USER, "z", Map.of("value", 1), 1); store.putAccountData(TENANT, USER, "a", Map.of("value", 2), 2);
        store.putAccountData(TENANT, USER, "z", Map.of("value", 3), 3);
        assertThat(store.accountData(TENANT, USER, "z")).contains(Map.of("value", 3));
        assertThat(store.accountData(TENANT, USER)).containsOnlyKeys("a", "z");
        assertThat(store.accountData("org-two", USER)).isEmpty();
        String version = store.createBackupVersion(TENANT, USER, "algorithm", Map.of("public", "auth"));
        var put = store.putBackupKeys(TENANT, USER, version, null, null, Map.of("rooms", Map.of("!room:weave.test", Map.of("sessions", Map.of("s1", Map.of("encrypted", "opaque"), "s2", Map.of("encrypted", "second"))))));
        assertThat(put.count()).isEqualTo(2);
        assertThat(store.backupKeys(TENANT, USER, version, "!room:weave.test", "s1")).containsEntry("encrypted", "opaque");
        assertThat(store.backupKeys("org-two", USER, version, "!room:weave.test", "s1")).isEmpty();
        store.putBackupKeys(TENANT, USER, version, "!room:weave.test", "s1", Map.of("encrypted", "replacement"));
        assertThat(store.backupVersion(TENANT, USER, version)).get().extracting(MatrixE2eePersistence.BackupVersionRecord::count).isEqualTo(2L);
        store.deleteBackupKeys(TENANT, USER, version, "!room:weave.test", "s2");
        assertThat(store.backupVersion(TENANT, USER, version)).get().extracting(MatrixE2eePersistence.BackupVersionRecord::count).isEqualTo(1L);
        store.updateBackupVersion(TENANT, USER, version, "changed", Map.of("auth", true));
        assertThat(store.backupVersion(TENANT, USER, "invalid")).isEmpty();
        assertThat(store.deleteBackupVersion(TENANT, USER, version)).isTrue();
        assertThat(store.backupKeys(TENANT, USER, version, null, null)).isEqualTo(Map.of("rooms", Map.of()));
    }

    @Test
    void rollbackRemovesEnvelopeAndRevisionAndRestoresDeletedBackup() {
        DataSource source = database("matrix-rollback"); var store = store(source);
        String version = store.createBackupVersion(TENANT, USER, "algo", Map.of());
        store.putBackupKeys(TENANT, USER, version, "room", "session", Map.of("opaque", true));
        new TransactionTemplate(JpaTestDatabase.transactionManager(source)).executeWithoutResult(status -> {
            store.upsertDevice(TENANT, USER, DEVICE, Map.of("public", "key"), 1);
            store.appendToDevice(TENANT, USER, DEVICE, USER, "m.room.encrypted", "rolled-back", Map.of());
            store.deleteBackupVersion(TENANT, USER, version);
            status.setRollbackOnly();
        });
        assertThat(store.device(TENANT, USER, DEVICE)).isEmpty(); assertThat(store.currentRevision(TENANT)).isZero();
        assertThat(store.toDeviceEvents(TENANT, USER, DEVICE, 0, Long.MAX_VALUE, 10)).isEmpty();
        assertThat(store.backupVersion(TENANT, USER, version)).isPresent();
        assertThat(store.backupKeys(TENANT, USER, version, "room", "session")).containsEntry("opaque", true);
    }

    @Test
    void supportCountsOnlyInspectPublicEnvelopeTypesAndCountScopedTuples() {
        DataSource source = database("matrix-support-counts"); var store = store(source);
        store.upsertDevice(TENANT, USER, DEVICE, Map.of(), 1); store.upsertDevice(TENANT, USER, "REVOKED", Map.of(), 2);
        store.revokeDevice(TENANT, USER, "REVOKED", 3);
        store.appendToDevice(TENANT, USER, DEVICE, USER, "m.room.encrypted", "txn", Map.of("ciphertext", Map.of("public-recipient", Map.of("type", 0, "body", "opaque"), "other", Map.of("type", 1, "body", "opaque"))));
        store.appendToDevice("org-two", USER, DEVICE, USER, "m.room.encrypted", "txn", Map.of("ciphertext", Map.of()));
        store.appendToDevice(TENANT, USER, DEVICE, USER, "m.room_key", "txn", Map.of());
        var stats = store.supportSafeStats();
        assertThat(stats.activeDeviceCount()).isEqualTo(1); assertThat(stats.revokedDeviceCount()).isEqualTo(1);
        assertThat(stats.queuedEventCount()).isEqualTo(3); assertThat(stats.encryptedEventCount()).isEqualTo(2);
        assertThat(stats.plaintextRoomKeyEventCount()).isEqualTo(1); assertThat(stats.olmPreKeyEnvelopeCount()).isEqualTo(1);
        assertThat(stats.olmExistingSessionEnvelopeCount()).isEqualTo(1); assertThat(stats.targetedDeviceCount()).isEqualTo(2);
        assertThat(stats.transactionCount()).isEqualTo(2); assertThat(stats.currentRevision()).isEqualTo(2);
    }

    @Test
    void replaceAndReinsertWithinOneTransactionDoNotReuseDeletedManagedRows() {
        DataSource source = database("matrix-repeat-mutation"); var store = store(source);
        new TransactionTemplate(JpaTestDatabase.transactionManager(source)).executeWithoutResult(status -> {
            store.replaceFallbackKeys(TENANT, USER, DEVICE, Map.of("curve:f", "old"), 1);
            store.replaceFallbackKeys(TENANT, USER, DEVICE, Map.of("curve:f", "new"), 2);
            String version = store.createBackupVersion(TENANT, USER, "algo", Map.of());
            store.putBackupKeys(TENANT, USER, version, "room", "session", Map.of("value", "old"));
            store.deleteBackupKeys(TENANT, USER, version, "room", "session");
            store.putBackupKeys(TENANT, USER, version, "room", "session", Map.of("value", "new"));
        });
        assertThat(store.claimOneTimeKey(TENANT, USER, DEVICE, "curve")).get()
                .extracting(MatrixE2eePersistence.ClaimedKey::value).isEqualTo("new");
        assertThat(store.backupKeys(TENANT, USER, "1", "room", "session")).containsEntry("value", "new");
    }

    private DataSource database(String name) { return JpaTestDatabase.entityFirstDataSource(name); }
    private MatrixE2eeRelationalStore store(DataSource source) {
        return JpaTestDatabase.transactional(source, new MatrixE2eeRelationalStore(JpaTestDatabase.entityManager(source), new ObjectMapper()));
    }
}
