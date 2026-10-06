package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
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
}
