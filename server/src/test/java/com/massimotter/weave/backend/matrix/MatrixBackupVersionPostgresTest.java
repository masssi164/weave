package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.util.Map;
import java.util.Set;
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
class MatrixBackupVersionPostgresTest {
    @Test
    void concurrentFirstVersionsRemainDistinctWithOneCurrentVersion() throws Exception {
        var dataSource = JpaTestDatabase.dataSource("matrix-backup-version-race");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var first = new MatrixE2eeRelationalStore(jdbc, new ObjectMapper());
        var second = new MatrixE2eeRelationalStore(jdbc, new ObjectMapper());
        String tenant = "org-one";
        String user = "@member:api.weave.test";
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var one = workers.submit(() -> {
                start.await();
                return transaction.execute(ignored -> first.createBackupVersion(
                        tenant, user, "m.megolm_backup.v1.curve25519-aes-sha2", Map.of("owner", "first")));
            });
            var two = workers.submit(() -> {
                start.await();
                return transaction.execute(ignored -> second.createBackupVersion(
                        tenant, user, "m.megolm_backup.v1.curve25519-aes-sha2", Map.of("owner", "second")));
            });
            start.countDown();
            assertThat(Set.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("1", "2");
            assertThat(jdbc.queryForObject("select count(*) from weave_matrix_key_backup_versions where tenant_id=? and user_id=? and current_version", Integer.class, tenant, user))
                    .isEqualTo(1);
            assertThat(second.currentBackupVersion(tenant, user)).isPresent()
                    .get().extracting(MatrixE2eePersistence.BackupVersionRecord::version).isEqualTo("2");
            String otherOrganizationVersion = transaction.execute(ignored -> first.createBackupVersion(
                    "org-two", user, "m.megolm_backup.v1.curve25519-aes-sha2", Map.of()));
            assertThat(otherOrganizationVersion).isEqualTo("1");
        } finally {
            workers.shutdownNow();
        }
    }
}
