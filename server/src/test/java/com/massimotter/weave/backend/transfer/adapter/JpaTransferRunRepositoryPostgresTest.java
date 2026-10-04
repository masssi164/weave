package com.massimotter.weave.backend.transfer.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import com.massimotter.weave.backend.transfer.domain.CanonicalObjectId;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossClass;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferCheckpoint;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferFormatVersion;
import com.massimotter.weave.backend.transfer.domain.TransferRun;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgres")
class JpaTransferRunRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void flywayStateSurvivesAdapterRestartAndRejectsStaleRevision() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        JpaTestDatabase.validateSchema(dataSource);

        JpaTransferRunRepository firstAdapter = TransferRunJpaTestFactory.create(dataSource);
        Instant now = Instant.parse("2026-08-18T14:00:00Z");
        LossRecord loss = new LossRecord(
                new CanonicalObjectId("file-1"),
                "permissions",
                LossClass.LOSSY,
                "target preserves read-only permission intent");
        TransferRun initial = TransferRun.initial(
                new TransferRun.Id("transfer-1"),
                "org-1",
                "core-v1",
                new TransferFormatVersion(1),
                now);
        TransferRun firstBatch = initial.advance(
                new TransferCheckpoint("after-first", 1),
                2,
                List.of(loss),
                "digest-first",
                false,
                now.plusSeconds(1));

        firstAdapter.save(firstBatch, 0);
        assertThat(firstAdapter.findById(firstBatch.id())).contains(firstBatch);

        JpaTransferRunRepository restartedAdapter = TransferRunJpaTestFactory.create(dataSource);
        assertThat(restartedAdapter.findById(firstBatch.id())).contains(firstBatch);

        TransferRun completed = firstBatch.advance(
                null,
                1,
                List.of(loss),
                "digest-complete",
                true,
                now.plusSeconds(2));
        restartedAdapter.save(completed, 1);

        assertThat(firstAdapter.findById(completed.id())).contains(completed);
        assertThatThrownBy(() -> restartedAdapter.save(completed, 1))
                .isInstanceOf(JpaTransferRunRepository.StaleTransferRunException.class)
                .hasMessageContaining("expected revision 1 but found 2");

        JpaTestDatabase.validateSchema(dataSource);
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
