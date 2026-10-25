package com.massimotter.weave.schema;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;

/** Build-only JPA metadata export. This class is not part of the serving executable. */
public final class EntitySchemaExporter {
    private EntitySchemaExporter() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one generated-baseline output path");
        }
        var types = new PersistenceManagedTypesScanner(new DefaultResourceLoader())
                .scan("com.massimotter.weave.backend");
        String baseline = render(types);
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), ".entity-baseline-", ".sql");
        try {
            Files.writeString(temporary, baseline, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        System.out.println("entity-schema-export: generated from ORM metadata; no database connection");
    }

    static String render(PersistenceManagedTypes types) {
        if (types.getManagedClassNames().isEmpty()) {
            throw new IllegalArgumentException("Cannot export a schema without mapped types");
        }
        var dataSource = new OfflineDataSource();
        var manager = new DefaultPersistenceUnitManager();
        manager.setPersistenceXmlLocations(new String[0]);
        manager.setMappingResources(new String[0]);
        manager.setDefaultPersistenceUnitName("weave-entity-first");
        manager.setDefaultDataSource(dataSource);
        manager.setManagedTypes(PersistenceManagedTypes.of(
                types.getManagedClassNames().stream().sorted().toList(),
                types.getManagedPackages().stream().sorted().toList()));
        manager.afterPropertiesSet();

        // Reuse Boot's naming defaults, not a second handwritten table/column model.
        var hibernate = new HibernateProperties();
        hibernate.setDdlAuto("none");
        Map<String, Object> settings = new LinkedHashMap<>(
                hibernate.determineHibernateProperties(Map.of(), new HibernateSettings()));
        var script = new StringWriter();
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        settings.put("hibernate.boot.allow_jdbc_metadata_access", false);
        settings.put("jakarta.persistence.schema-generation.database.action", "none");
        settings.put("jakarta.persistence.schema-generation.create-source", "metadata");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", script);
        settings.put("jakarta.persistence.validation.mode", "NONE");
        settings.put("hibernate.hbm2ddl.schema-generation.script.append", false);
        settings.put("hibernate.hbm2ddl.halt_on_error", true);
        settings.put("hibernate.hbm2ddl.skip_default_import_file", true);
        settings.put("hibernate.hbm2ddl.import_files", "");
        settings.put("hibernate.format_sql", true);
        new HibernatePersistenceProvider().generateSchema(
                manager.obtainDefaultPersistenceUnitInfo(), settings);
        if (dataSource.accessed) {
            throw new IllegalStateException("Schema export attempted a forbidden database connection");
        }
        String generated = script.toString().replace("\r\n", "\n").strip();
        if (generated.isEmpty()) {
            throw new IllegalStateException("ORM metadata produced an empty schema");
        }
        return "-- Generated from compiled Weave JPA mappings; do not edit.\n"
                + "-- Candidate baseline only: generation does not initialize a database.\n"
                + generated + "\n";
    }

    /** Defense in depth: even unexpected provider metadata access cannot reach a database. */
    static final class OfflineDataSource extends AbstractDataSource {
        private boolean accessed;

        @Override
        public Connection getConnection() throws SQLException {
            accessed = true;
            throw new SQLNonTransientConnectionException("Entity schema export is offline-only");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }
}
