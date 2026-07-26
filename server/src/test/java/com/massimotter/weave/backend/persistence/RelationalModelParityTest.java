package com.massimotter.weave.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Proves parity between Flyway, the pinned Chen-model manifest, and JPA.
 */
class RelationalModelParityTest {
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?im)^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-zA-Z0-9_]+)");
    private static final Pattern DROP_TABLE = Pattern.compile(
            "(?im)^drop\\s+table\\s+(?:if\\s+exists\\s+)?([a-zA-Z0-9_]+)");
    private static final Pattern ENTITY_DECLARATION = Pattern.compile(
            "@Entity\\b([\\s\\S]*?)\\bclass\\s+([A-Za-z0-9_]+)");
    private static final Pattern TABLE_NAME = Pattern.compile(
            "@Table\\([\\s\\S]*?\\bname\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern JPA_REPOSITORY = Pattern.compile(
            "interface\\s+([A-Za-z0-9_]+)\\s+extends\\s+JpaRepository\\s*<",
            Pattern.MULTILINE);

    @Test
    void flywayManifestEntitiesAndRepositoriesAreInExactParity()
            throws IOException {
        Path repositoryRoot = repositoryRoot();
        Path migrations = repositoryRoot.resolve(
                "weave-persistence-jpa/src/main/resources/db/migration");
        try (var files = Files.list(migrations)) {
            assertThat(files
                    .filter(path -> path.toString().endsWith(".sql"))
                    .map(path -> path.getFileName().toString())
                    .toList())
                    .containsExactly("V001__weave_baseline.sql");
        }
        JsonNode model = relationalModel(repositoryRoot);
        Set<String> flywayTables = currentFlywayTables(repositoryRoot);
        Set<String> manifestTables = new LinkedHashSet<>();
        Map<String, String> expectedEntityTables = new LinkedHashMap<>();
        Set<String> expectedRepositories = new LinkedHashSet<>();

        for (JsonNode entity : model.path("entities")) {
            String lifecycle = entity.path("lifecycle").asText();
            JsonNode storage = entity.path("storage");
            if (Set.of("flyway-table", "migration-table")
                    .contains(storage.path("kind").asText())) {
                manifestTables.add(storage.path("name").asText());
            }
            JsonNode jpa = entity.path("jpa");
            if (Set.of("active", "append-only").contains(lifecycle)) {
                assertThat(jpa.isObject())
                        .as(entity.path("id").asText()
                                + " must declare its JPA mapping")
                        .isTrue();
            } else {
                assertThat(jpa.isNull() || jpa.isMissingNode())
                        .as(entity.path("id").asText()
                                + " must not acquire a JPA repository")
                        .isTrue();
            }
            if (jpa.isObject()) {
                expectedEntityTables.put(
                        jpa.path("entity").asText(),
                        storage.path("name").asText());
                expectedRepositories.add(jpa.path("repository").asText());
            }
        }

        String production = productionJava(repositoryRoot);
        Map<String, String> actualEntityTables = entityTables(production);
        Set<String> actualRepositories = matches(
                JPA_REPOSITORY, production, 1);

        assertThat(manifestTables)
                .as("every current Flyway table is modeled, including migration-only debt")
                .isEqualTo(flywayTables);
        assertThat(actualEntityTables)
                .as("every JPA entity and @Table mapping matches the canonical relational manifest")
                .isEqualTo(expectedEntityTables);
        assertThat(actualRepositories)
                .as("every Spring Data repository is declared by the canonical relational manifest")
                .isEqualTo(expectedRepositories);
        assertThat(actualEntityTables)
                .as("checkpoint chunks remain migration-only and cannot gain a production entity")
                .doesNotContainValue("weave_agent_runtime_state_chunks");
    }

    private JsonNode relationalModel(Path repositoryRoot) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode lock = mapper.readTree(Files.readString(
                repositoryRoot.resolve("specs/weave-specs.lock.json")));
        String configuredRoot = System.getenv("WEAVE_SPEC_CORPUS_ROOT");
        Path corpusRoot = configuredRoot == null || configuredRoot.isBlank()
                ? repositoryRoot.resolve(
                        lock.at("/specCorpus/localPath").asText())
                : Path.of(configuredRoot);
        Path model = corpusRoot.toAbsolutePath().normalize().resolve(
                "architecture/data-model/relational-core-model.json");
        assertThat(model)
                .as("the exact pinned specification corpus must be available")
                .isRegularFile();
        return mapper.readTree(Files.readString(model));
    }

    private Set<String> currentFlywayTables(Path repositoryRoot)
            throws IOException {
        Set<String> created = new LinkedHashSet<>();
        Set<String> dropped = new LinkedHashSet<>();
        Path migrations = repositoryRoot.resolve(
                "weave-persistence-jpa/src/main/resources/db/migration");
        try (var files = Files.list(migrations)) {
            for (Path migration : files
                    .filter(path -> path.toString().endsWith(".sql"))
                    .toList()) {
                String sql = Files.readString(migration);
                created.addAll(matches(CREATE_TABLE, sql, 1));
                dropped.addAll(matches(DROP_TABLE, sql, 1));
            }
        }
        created.removeAll(dropped);
        return created;
    }

    private String productionJava(Path repositoryRoot) throws IOException {
        StringBuilder source = new StringBuilder();
        for (String module : Set.of("server", "weave-persistence-jpa")) {
            Path javaRoot = repositoryRoot.resolve(module + "/src/main/java");
            try (var files = Files.walk(javaRoot)) {
                for (Path file : files
                        .filter(path -> path.toString().endsWith(".java"))
                        .toList()) {
                    source.append(Files.readString(file)).append('\n');
                }
            }
        }
        return source.toString();
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

    private Set<String> matches(
            Pattern pattern,
            String source,
            int group) {
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
        throw new IllegalStateException(
                "Unable to locate the Weave repository root");
    }
}
