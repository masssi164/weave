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
                .contains("file:/Team/readme.md", "/dav/files/Team/readme.md", "sizeKnown=true", "size=12")
                .doesNotContain("Nextcloud", "remote.php", "Bearer ");
    }

    @Test
    void filesSearchUsesLocaleStableMatchingAndEncodedDavHrefs() {
        when(filesFacadeService.list("/Team")).thenReturn(new FileListResponse(
                "/Team",
                List.of(new FileItemResponse(
                        "files:quarterly",
                        "İstanbul plan.md",
                        "/Team/Design docs/İstanbul plan.md",
                        "file",
                        "text/markdown",
                        null,
                        OffsetDateTime.parse("2026-07-04T12:01:00Z"),
                        true)),
                null));

        Map<String, Object> result = dispatcher.dispatch("files.search", Map.of(
                "path", "/Team",
                "query", "PLAN",
                "limit", 5));

        assertThat(result.get("items").toString())
                .contains("/dav/files/Team/Design%20docs/%C4%B0stanbul%20plan.md", "sizeKnown=false", "size=-1");
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
                                true)),
                null);
    }
}
