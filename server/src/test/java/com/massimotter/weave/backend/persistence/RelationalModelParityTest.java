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
 * Proves parity between the pinned relational-model manifest and JPA.
 */
class RelationalModelParityTest {
    private static final Pattern ENTITY_DECLARATION = Pattern.compile(
            "@Entity\\b([\\s\\S]*?)\\bclass\\s+([A-Za-z0-9_]+)");
    private static final Pattern TABLE_NAME = Pattern.compile(
            "@Table\\([\\s\\S]*?\\bname\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern JPA_REPOSITORY = Pattern.compile(
            "interface\\s+([A-Za-z0-9_]+)\\s+extends\\s+JpaRepository\\s*<",
            Pattern.MULTILINE);

    @Test
    void manifestEntitiesAndRepositoriesAreInExactParity()
            throws IOException {
        Path repositoryRoot = repositoryRoot();
        Path migrations = repositoryRoot.resolve(
                "weave-persistence-jpa/src/main/resources/db/migration");
        if (Files.exists(migrations)) {
            try (var files = Files.walk(migrations)) {
                assertThat(files
                        .filter(Files::isRegularFile)
                        .map(path -> migrations.relativize(path).toString())
                        .toList())
                        .as("code-first persistence has no parallel migration resource")
                        .isEmpty();
            }
        }
        JsonNode model = relationalModel(repositoryRoot);
        Set<String> manifestTables = new LinkedHashSet<>();
        Map<String, String> expectedEntityTables = new LinkedHashMap<>();
        Set<String> expectedRepositories = new LinkedHashSet<>();

        for (JsonNode entity : model.path("entities")) {
            String lifecycle = entity.path("lifecycle").asText();
            JsonNode storage = entity.path("storage");
            if ("jpa-table".equals(storage.path("kind").asText())) {
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

        assertThat(actualEntityTables)
                .as("every JPA entity and @Table mapping matches the canonical relational manifest")
                .isEqualTo(expectedEntityTables);
        assertThat(actualRepositories)
                .as("every Spring Data repository is declared by the canonical relational manifest")
                .isEqualTo(expectedRepositories);
        assertThat(new LinkedHashSet<>(actualEntityTables.values()))
                .as("the complete relational schema is owned by JPA and no retired table remains")
                .isEqualTo(manifestTables)
                .doesNotContain("weave_agent_runtime_state_chunks");
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
