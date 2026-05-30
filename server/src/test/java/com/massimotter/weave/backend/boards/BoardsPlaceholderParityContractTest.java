package com.massimotter.weave.backend.boards;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.boards.domain.BoardCapability;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.local.PlaceholderBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsMapper;
import com.massimotter.weave.backend.boards.openproject.OpenProjectProjectSnapshot;
import com.massimotter.weave.backend.boards.openproject.OpenProjectStatusSnapshot;
import com.massimotter.weave.backend.boards.openproject.OpenProjectWorkPackageSnapshot;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.TaskQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardsPlaceholderParityContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void placeholderAdapterReturnsSameBoardsContractWithoutProviderPayloads() {
        var repository = new PlaceholderBoardsRepository();

        var projects = repository.listProjects(null).items();
        var boards = repository.listBoards(projects.getFirst().id(), null).items();
        var columns = repository.listColumns(boards.getFirst().id(), null).items();
        var tasks = repository.listTasks(boards.getFirst().id(), TaskQuery.all()).items();

        assertThat(repository.capabilities().provider()).isEqualTo(ProviderKind.PLACEHOLDER);
        assertThat(repository.capabilities().supported()).contains(
                BoardCapability.STATUS_UPDATES,
                BoardCapability.ACCESSIBLE_NON_DRAG_MOVES,
                BoardCapability.COMMENTS,
                BoardCapability.ATTACHMENTS);
        assertThat(boards).singleElement().satisfies(board -> {
            assertThat(board.columns()).hasSameSizeAs(columns);
            assertThat(board.providerRefs()).isEmpty();
            assertThat(board.toString().toLowerCase()).doesNotContain("openproject", "planner", "raw");
        });
        assertThat(tasks).isNotEmpty().allSatisfy(task -> {
            assertThat(task.assigneeRefs()).isNotEmpty();
            assertThat(task.providerRefs()).allSatisfy(ref -> assertThat(ref.provider()).isEqualTo(ProviderKind.IN_MEMORY));
            assertThat(task.toString()).doesNotContain("https://", "Bearer ", "access_token");
        });
    }

    @Test
    void openProjectMappingClassifiesStatusListDifferenceAndProviderRefsAsDiagnosticsOnly() {
        var mapper = new OpenProjectBoardsMapper();
        var project = new OpenProjectProjectSnapshot(42L, "apollo", "Apollo", "Launch work", false, null, Instant.parse("2026-05-30T10:00:00Z"));
        var statuses = List.of(
                new OpenProjectStatusSnapshot(1L, "New", 0, false, null),
                new OpenProjectStatusSnapshot(2L, "In progress", 1, false, null),
                new OpenProjectStatusSnapshot(3L, "Closed", 2, true, null));
        var columns = statuses.stream().map(status -> mapper.toColumn(project.id(), status)).toList();
        var task = mapper.toTask(new OpenProjectWorkPackageSnapshot(
                99L,
                42L,
                3L,
                "Ship provider-neutral board",
                "OpenProject fields normalize into Weave Boards.",
                7,
                "High",
                List.of("person:ada"),
                List.of("label:release"),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-05-30T11:00:00Z"),
                Instant.parse("2026-05-30T12:00:00Z"),
                null,
                "17"), Map.of(3L, statuses.get(2)));

        var board = mapper.toBoard(project, columns);

        assertThat(board.columns()).extracting("name").containsExactly("New", "In progress", "Closed");
        assertThat(task.status().name()).isEqualTo("COMPLETED");
        assertThat(task.providerRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
            assertThat(ref.externalId()).isEqualTo("work-package:99");
        });
        assertThat(task.assigneeRefs()).containsExactly("person:ada");
        assertThat(task.labelRefs()).containsExactly("label:release");
    }

    @Test
    void lossyAndConflictFixturesStaySupportSafeAndProveGuardedWrites() throws Exception {
        var lossy = OBJECT_MAPPER.readTree(Files.readString(Path.of("src/test/resources/boards-portability/lossy-mapping-report.json")));
        var conflict = OBJECT_MAPPER.readTree(Files.readString(Path.of("src/test/resources/boards-portability/conflict-report.json")));

        assertThat(lossy.path("domain").asText()).isEqualTo("boards");
        assertThat(lossy.path("field_classifications").findValuesAsText("classification"))
                .contains("lossless_canonical", "lossless_extension", "lossy_with_report", "blocked_nonportable");
        assertThat(conflict.path("write_guard").path("apply_enabled").asBoolean()).isFalse();
        assertThat(conflict.path("write_guard").path("required_gates").findValuesAsText("gate"))
                .contains("rbac", "audit", "dry_run_preview", "redaction", "rollback_notes");
        assertThat(lossy.path("raw_provider_payloads_returned").asBoolean()).isFalse();
        assertThat(conflict.path("raw_provider_payloads_returned").asBoolean()).isFalse();
        assertThat(lossy.toString() + conflict.toString())
                .doesNotContain("Bearer ", "access_token", "client-secret");
    }
}
