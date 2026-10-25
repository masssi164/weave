package com.massimotter.weave.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Proves the local Flyway/JPA persistence authority without depending on an
 * external specification checkout or treating JPA as the canonical domain.
 *
 * <p>The complete schema/repository/upgrade/recovery contract remains owned by
 * issue #1320. This foundation test prevents the retired code-first and external
 * relational-manifest authorities from returning while those stronger tests are
 * introduced.
 */
class RelationalModelParityTest {
    private static final Pattern ENTITY_DECLARATION = Pattern.compile(
            "@Entity\\b([\\s\\S]*?)\\bclass\\s+([A-Za-z0-9_]+)");
    private static final Pattern TABLE_NAME = Pattern.compile(
            "@Table\\([\\s\\S]*?\\bname\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern JPA_REPOSITORY = Pattern.compile(
            "interface\\s+([A-Za-z0-9_]+)\\s+extends\\s+JpaRepository\\s*<",
            Pattern.MULTILINE);
    private static final Pattern FLYWAY_MIGRATION = Pattern.compile(
            "V([0-9]+(?:[._][0-9]+)*)__[A-Za-z0-9_]+\\.sql");

    @Test
    void localFlywayAndJpaFoundationHasOneExplicitAuthority()
            throws IOException {
        Path repositoryRoot = repositoryRoot();
        List<Path> migrations = migrationFiles(repositoryRoot);

        assertThat(migrations)
                .as("the Server owns committed Flyway migrations")
                .isNotEmpty();

        Set<String> versions = new LinkedHashSet<>();
        for (Path migration : migrations) {
            String fileName = migration.getFileName().toString();
            var matcher = FLYWAY_MIGRATION.matcher(fileName);
            assertThat(matcher.matches())
                    .as("migration uses a reviewable Flyway versioned filename: " + fileName)
                    .isTrue();
            assertThat(versions.add(matcher.group(1)))
                    .as("Flyway migration versions are unique: " + matcher.group(1))
                    .isTrue();
            assertThat(Files.size(migration))
                    .as("migration is not empty: " + fileName)
                    .isGreaterThan(0L);
        }
        assertThat(versions)
                .as("the accepted local baseline begins at Flyway V1")
                .contains("1");

        // Domain ownership does not create a second owner for historical Flyway scripts.
        for (String module : List.of("server/core", "server/domains")) {
            Path root = repositoryRoot.resolve(module);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var files = Files.walk(root)) {
                assertThat(files.filter(Files::isRegularFile)
                        .filter(path -> sourcePath(repositoryRoot, path)
                                .contains("/src/main/resources/db/migration/"))
                        .filter(path -> !sourcePath(repositoryRoot, path).contains("/build/"))
                        .toList())
                        .as("Flyway migrations have one repository location, not a per-domain copy")
                        .isEmpty();
            }
        }

        String production = productionJava(repositoryRoot);
        Map<String, String> entityTables = entityTables(production);
        Set<String> repositoryNames = matches(JPA_REPOSITORY, production, 1);

        assertThat(entityTables)
                .as("the persistence adapters contain explicit JPA entity mappings")
                .isNotEmpty();
        assertThat(repositoryNames)
                .as("the persistence adapters contain explicit Spring Data repositories")
                .isNotEmpty();
        assertThat(entityTables.values())
                .as("the inventory must include domain-owned entities, not only remaining legacy sources")
                .contains("weave_files_objects", "weave_file_locks", "weave_files_user_resources",
                        "weave_calendar_collections", "weave_calendar_event_temporals")
                .allMatch(table -> table != null && !table.isBlank())
                .doesNotContain("weave_agent_runtime_state_chunks");
    }

    private List<Path> migrationFiles(Path repositoryRoot) throws IOException {
        Path migrationRoot = repositoryRoot.resolve(
                "server/src/main/resources/db/migration");
        assertThat(migrationRoot)
                .as("the local Flyway migration root must exist")
                .isDirectory();
        try (var files = Files.walk(migrationRoot)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    private String productionJava(Path repositoryRoot) throws IOException {
        StringBuilder source = new StringBuilder();
        // Walk real nested source roots: the old two fixed /src/main/java roots silently
        // stopped inspecting Files and Calendar entities as they were extracted.
        for (String module : List.of("server")) {
            try (var files = Files.walk(repositoryRoot.resolve(module))) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> sourcePath(repositoryRoot, path).contains("/src/main/java/"))
                        .filter(path -> !sourcePath(repositoryRoot, path).contains("/build/"))
                        .sorted().toList()) {
                    source.append(Files.readString(file)).append('\n');
                }
            }
        }
        return source.toString();
    }

    private String sourcePath(Path repositoryRoot, Path file) {
        return repositoryRoot.relativize(file).toString().replace('\\', '/');
    }

    private Map<String, String> entityTables(String production) {
        Map<String, String> result = new LinkedHashMap<>();
        var matcher = ENTITY_DECLARATION.matcher(production);
        while (matcher.find()) {
            var table = TABLE_NAME.matcher(matcher.group(1));
            assertThat(table.find())
                    .as(matcher.group(2) + " must declare an explicit @Table")
                    .isTrue();
            String previous = result.put(matcher.group(2), table.group(1));
            assertThat(previous)
                    .as("JPA entity names must be unique")
                    .isNull();
        }
        return result;
    }

    private Set<String> matches(Pattern pattern, String source, int group) {
        Set<String> result = new LinkedHashSet<>();
        var matcher = pattern.matcher(source);
        while (matcher.find()) {
            result.add(matcher.group(group));
        }
        return result;
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("server/src/main/java"))) {
            return current;
        }
        if (Files.isDirectory(current.resolve("src/main/java"))) {
            return current.getParent();
        }
        throw new IllegalStateException("Unable to locate the Weave repository root");
    }
}
