package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.FilesFacadeService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberDomainToolDispatcherTest {

    private final FilesFacadeService filesFacadeService = mock(FilesFacadeService.class);
    private final CalendarFacadeService calendarFacadeService = mock(CalendarFacadeService.class);
    private final MemberDomainToolDispatcher dispatcher = new MemberDomainToolDispatcher(filesFacadeService, calendarFacadeService);

    @Test
    void filesSearchUsesFilesFacadeAndProjectsWebdavBackedMcpMetadata() {
        when(filesFacadeService.list("/Team")).thenReturn(teamListing());

        Map<String, Object> result = dispatcher.dispatch("files.search", Map.of(
                "path", "/Team",
                "query", "readme",
                "limit", 5));

        assertThat(result)
                .containsEntry("status", "ok")
                .containsEntry("supportSafe", true)
                .containsEntry("dataPlane", "weave-webdav-facade")
                .containsEntry("webDavFacadePath", "/dav/files")
                .containsEntry("openApiDataPlaneUsed", false)
                .containsEntry("rawProviderPayload", "redacted");
        assertThat(result.get("items").toString())
                .contains("file:/Team/readme.md", "/dav/files/Team/readme.md")
                .doesNotContain("Nextcloud", "remote.php", "Bearer ");
    }

    @Test
    void filesSearchEncodesWebdavHrefAndKeepsProjectionSizeNumericOrNull() {
        when(filesFacadeService.list("/Team")).thenReturn(teamListing());

        Map<String, Object> result = dispatcher.dispatch("files.search", Map.of("path", "/Team", "limit", 10));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");

        Map<String, Object> folder = items.stream()
                .filter(item -> "Design".equals(item.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> spacedName = items.stream()
                .filter(item -> "Design Notes.md".equals(item.get("name")))
                .findFirst()
                .orElseThrow();

        assertThat(folder).containsEntry("size", null);
        assertThat(spacedName)
                .containsEntry("size", 24L)
                .containsEntry("webDavHref", "/dav/files/Team/Design%20Notes.md");
    }

    @Test
    void filesReadRequiresCanonicalWeaveFileRefAndReturnsMetadataOnly() {
        when(filesFacadeService.list("/Team")).thenReturn(teamListing());

        Map<String, Object> result = dispatcher.dispatch("files.read", Map.of("fileRef", "file:/Team/readme.md"));

        assertThat(result)
                .containsEntry("status", "ok")
                .containsEntry("dataPlane", "weave-webdav-facade")
                .containsEntry("openApiDataPlaneUsed", false);
        assertThat(result.get("item").toString())
                .contains("file:/Team/readme.md", "readme.md")
                .doesNotContain("content=", "Nextcloud", "remote.php", "Bearer ");
    }

    @Test
    void filesReadRejectsProviderShapedRefsBeforeProviderAccess() {
        Map<String, Object> result = dispatcher.dispatch("files.read", Map.of("fileRef", "https://files.example.invalid/remote.php/dav/files/readme.md"));

        assertThat(result)
                .containsEntry("status", "blocked")
                .containsEntry("supportSafe", true)
                .containsEntry("rawProviderPayload", "redacted");
        assertThat(result.toString()).contains("files_file_ref_requires_weave_webdav_facade_path");
    }

    private FileListResponse teamListing() {
        return new FileListResponse(
                "/Team",
                List.of(
                        new FileItemResponse(
                                "files:folder",
                                "Design",
                                "/Team/Design",
                                "folder",
                                null,
                                null,
                                OffsetDateTime.parse("2026-07-04T12:00:00Z"),
                                false),
                        new FileItemResponse(
                                "files:readme",
                                "readme.md",
                                "/Team/readme.md",
                                "file",
                                "text/markdown",
                                12L,
                                OffsetDateTime.parse("2026-07-04T12:01:00Z"),
                                true),
                        new FileItemResponse(
                                "files:design-notes",
                                "Design Notes.md",
                                "/Team/Design Notes.md",
                                "file",
                                "text/markdown",
                                24L,
                                OffsetDateTime.parse("2026-07-04T12:02:00Z"),
                                true)),
                null);
    }
}
