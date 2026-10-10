package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@EnabledIfSystemProperty(named = "weave.test.postgres", matches = "true")
class MatrixDeviceProofPostgresTest {
    @Test
    void verifierPersistsAcrossInstancesAndCannotAdoptKeyedOrRevokedDevice() {
        var dataSource = JpaTestDatabase.dataSource("matrix-device-proof");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var first = new MatrixE2eeRelationalStore(jdbc, new ObjectMapper());
        var restarted = new MatrixE2eeRelationalStore(jdbc, new ObjectMapper());
        String trusted = "a".repeat(64);
        String wrong = "b".repeat(64);

        assertThat(first.bindDeviceProof("org-one", "@member:api.weave.test", "DEVICE1", trusted)).isTrue();
        assertThat(restarted.bindDeviceProof("org-one", "@member:api.weave.test", "DEVICE1", wrong)).isFalse();
        assertThat(restarted.bindDeviceProof("org-one", "@member:api.weave.test", "DEVICE1", trusted)).isTrue();
        assertThat(restarted.bindDeviceProof("org-two", "@member:api.weave.test", "DEVICE1", wrong)).isTrue();

        jdbc.update("insert into weave_matrix_devices(tenant_id,user_id,device_id,device_keys_json) values (?,?,?,?)",
                "org-one", "@member:api.weave.test", "LEGACY", "{\"keys\":{}}");
        assertThat(first.bindDeviceProof("org-one", "@member:api.weave.test", "LEGACY", trusted)).isFalse();
        jdbc.update("insert into weave_matrix_devices(tenant_id,user_id,device_id,device_keys_json,revoked) values (?,?,?,?,true)",
                "org-one", "@member:api.weave.test", "DEVICE1", "{}");
        assertThat(restarted.bindDeviceProof("org-one", "@member:api.weave.test", "DEVICE1", trusted)).isFalse();
    }

    @Test
    void signedContinuityChallengeBindsOnceAcrossStoreInstances() {
        var dataSource = JpaTestDatabase.dataSource("matrix-device-recovery");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var first = new MatrixE2eeRelationalStore(jdbc, new ObjectMapper());
        var restarted = new MatrixE2eeRelationalStore(jdbc, new ObjectMapper());
        String user = "@member:api.weave.test";
        String device = "LEGACY";
        String publicKey = "public-device-key";
        String hash = "a".repeat(64);
        String challengeId = UUID.randomUUID().toString();
        String challenge = "weave.matrix-device-continuity.v1\nnonce\n" + hash;
        jdbc.update("insert into weave_matrix_devices(tenant_id,user_id,device_id,device_keys_json) values (?,?,?,?)",
                "org-one", user, device, "{\"keys\":{\"ed25519:LEGACY\":\"" + publicKey + "\"}}");

        assertThat(first.issueDeviceRecoveryChallenge("org-one", user, device, challengeId,
                challenge, hash, Instant.now().plusSeconds(300))).isTrue();
        assertThat(restarted.issueDeviceRecoveryChallenge("org-one", user, device, UUID.randomUUID().toString(),
                challenge, "b".repeat(64), Instant.now().plusSeconds(300))).isFalse();
        assertThat(restarted.deviceRecoveryChallenge("org-two", user, device, challengeId)).isEmpty();
        assertThat(restarted.deviceRecoveryChallenge("org-one", user, device, challengeId))
                .get().extracting(MatrixE2eePersistence.DeviceRecoveryChallenge::text).isEqualTo(challenge);
        assertThat(restarted.completeDeviceRecoveryChallenge("org-one", user, device, challengeId,
                hash, "wrong-public-key")).isFalse();
        assertThat(restarted.completeDeviceRecoveryChallenge("org-one", user, device, challengeId,
                hash, publicKey)).isTrue();
        assertThat(first.deviceProofHash("org-one", user, device)).contains(hash);
        assertThat(first.deviceRecoveryChallenge("org-one", user, device, challengeId)).isEmpty();
        assertThat(first.completeDeviceRecoveryChallenge("org-one", user, device, challengeId,
                hash, publicKey)).isFalse();
        assertThat(first.issueDeviceRecoveryChallenge("org-one", user, device, UUID.randomUUID().toString(),
                challenge, hash, Instant.now().plusSeconds(300))).isFalse();
        assertThat(restarted.bindDeviceProof("org-one", user, device, "b".repeat(64))).isFalse();
        assertThat(restarted.bindDeviceProof("org-one", user, device, hash)).isTrue();

        String expiredId = UUID.randomUUID().toString();
        jdbc.update("insert into weave_matrix_devices(tenant_id,user_id,device_id,device_keys_json) values (?,?,?,?)",
                "org-one", user, "EXPIRED", "{\"keys\":{\"ed25519:EXPIRED\":\"" + publicKey + "\"}}");
        assertThat(first.issueDeviceRecoveryChallenge("org-one", user, "EXPIRED", expiredId,
                challenge, hash, Instant.now().minusSeconds(1))).isTrue();
        assertThat(restarted.deviceRecoveryChallenge("org-one", user, "EXPIRED", expiredId)).isEmpty();
        assertThat(restarted.completeDeviceRecoveryChallenge("org-one", user, "EXPIRED", expiredId,
                hash, publicKey)).isFalse();
    }

    @Test
    void concurrentCompletionConsumesOnlyOneLegacyChallenge() throws Exception {
        var dataSource = JpaTestDatabase.dataSource("matrix-device-recovery-race");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var first = new MatrixE2eeRelationalStore(jdbc, new ObjectMapper());
        var second = new MatrixE2eeRelationalStore(jdbc, new ObjectMapper());
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        String user = "@member:api.weave.test";
        String device = "RACE";
        String publicKey = "public-device-key";
        String hash = "c".repeat(64);
        String challengeId = UUID.randomUUID().toString();
        jdbc.update("insert into weave_matrix_devices(tenant_id,user_id,device_id,device_keys_json) values (?,?,?,?)",
                "org-one", user, device, "{\"keys\":{\"ed25519:RACE\":\"" + publicKey + "\"}}");
        assertThat(first.issueDeviceRecoveryChallenge("org-one", user, device, challengeId,
                "weave.matrix-device-continuity.v1\nnonce\n" + hash,
                hash, Instant.now().plusSeconds(300))).isTrue();

        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var one = workers.submit(() -> {
                start.await();
                return transaction.execute(ignored -> first.completeDeviceRecoveryChallenge(
                        "org-one", user, device, challengeId, hash, publicKey));
            });
            var two = workers.submit(() -> {
                start.await();
                return transaction.execute(ignored -> second.completeDeviceRecoveryChallenge(
                        "org-one", user, device, challengeId, hash, publicKey));
            });
            start.countDown();
            assertThat(Boolean.TRUE.equals(one.get(20, TimeUnit.SECONDS))
                    ^ Boolean.TRUE.equals(two.get(20, TimeUnit.SECONDS))).isTrue();
            assertThat(first.deviceProofHash("org-one", user, device)).contains(hash);
        } finally {
            workers.shutdownNow();
        }
    }
}
