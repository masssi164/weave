package com.massimotter.weave.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.sql.SQLNonTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

class EntityFirstSchemaExportTest {
    @Test
    void annotationsGeneratePostgresDdlWithoutAConnectionOrHandwrittenSchema() {
        String ddl = EntitySchemaExporter.render(types()).toLowerCase(java.util.Locale.ROOT);
        assertThat(ddl).contains("create table schema_export_probe", "display_name", "varchar(80)",
                        "not null", "uk_schema_probe_name", "audit_marker", "version")
                .doesNotContain("drop table", "insert into", "flyway_schema_history");
    }

    @Test
    void repeatedExportsAreIdenticalRatherThanAppended() {
        String first = EntitySchemaExporter.render(types());
        assertThat(EntitySchemaExporter.render(types())).isEqualTo(first);
    }

    @Test
    void unmappedClasspathCannotProduceAnApparentlyValidBaseline() {
        assertThatThrownBy(() -> EntitySchemaExporter.render(PersistenceManagedTypes.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without mapped types");
    }

    @Test
    void bothDataSourceEntryPointsRejectConnectionsEvenWithSuppliedCredentials() {
        var dataSource = new EntitySchemaExporter.OfflineDataSource();
        assertThatThrownBy(dataSource::getConnection)
                .isInstanceOf(SQLNonTransientConnectionException.class);
        assertThatThrownBy(() -> dataSource.getConnection("unused", "unused"))
                .isInstanceOf(SQLNonTransientConnectionException.class);
    }

    private PersistenceManagedTypes types() {
        return PersistenceManagedTypes.of(SchemaExportProbe.class.getName());
    }

    // Outside the backend package: this fixture cannot enter the production entity scan.
    @Entity
    @Table(name = "schema_export_probe", uniqueConstraints =
            @UniqueConstraint(name = "uk_schema_probe_name", columnNames = "display_name"))
    public static class SchemaExportProbe {
        @Id private Long id;
        @Column(name = "display_name", nullable = false, length = 80) private String displayName;
        private String auditMarker;
        @Version private long version;
        protected SchemaExportProbe() {}
    }
}
