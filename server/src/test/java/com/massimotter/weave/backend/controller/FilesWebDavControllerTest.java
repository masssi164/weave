package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.files.DownloadedFile;
import com.massimotter.weave.backend.service.files.WebDavLockResult;
import com.massimotter.weave.backend.service.files.WebDavPropfindListing;
import com.massimotter.weave.backend.service.files.WebDavPropfindResource;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = FilesWebDavController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave"
})
class FilesWebDavControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FilesFacadeService filesFacadeService;

    @MockBean
    private DeviceCredentialService deviceCredentialService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void optionsAdvertisesWebdavMethods() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/dav/files")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("DAV", "1"))
                .andExpect(header().string(HttpHeaders.ALLOW, "OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL, MOVE, COPY, LOCK, UNLOCK"));
    }

    @Test
    void propfindDepthZeroReturnsMultistatusForRequestedCollectionOnly() throws Exception {
        // FILES_WEBDAV_PROPFIND_FACADE
        given(filesFacadeService.webDavPropfind("/Team")).willReturn(teamPropfindListing());

        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/files/Team/")
                        .header("Depth", "0")
                        .with(workspaceJwt()))
                .andExpect(status().is(207))
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<d:multistatus xmlns:d=\"DAV:\">")))
                .andExpect(content().string(containsString("<d:href>/dav/files/Team/</d:href>")))
                .andExpect(content().string(containsString("<d:getetag>&quot;etag-team&quot;</d:getetag>")))
                .andExpect(content().string(containsString("<d:supportedlock/>")))
                .andExpect(content().string(containsString("<d:lockdiscovery/>")))
                .andExpect(content().string(not(containsString("readme.md"))))
                .andExpect(content().string(not(containsString("Nextcloud"))))
                .andExpect(content().string(not(containsString("remote.php"))))
                .andExpect(content().string(not(containsString("Bearer"))));
    }

    @Test
    void propfindDepthOneReturnsChildrenAsDavResponses() throws Exception {
        given(filesFacadeService.webDavPropfind("/Team")).willReturn(teamPropfindListing());

        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/files/Team")
                        .header("Depth", "1")
                        .with(workspaceJwt()))
                .andExpect(status().is(207))
                .andExpect(content().string(containsString("<d:href>/dav/files/Team/Design/</d:href>")))
                .andExpect(content().string(containsString("<d:href>/dav/files/Team/readme%20one.md</d:href>")))
                .andExpect(content().string(containsString("<d:getetag>&quot;etag-design&quot;</d:getetag>")))
                .andExpect(content().string(containsString("<d:getetag>&quot;etag-readme&quot;</d:getetag>")))
                .andExpect(content().string(containsString("<d:getcontenttype>text/markdown</d:getcontenttype>")))
                .andExpect(content().string(containsString("<d:getcontentlength>12</d:getcontentlength>")))
                .andExpect(content().string(not(containsString("files.example.test"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void getDownloadsFileThroughFacadePath() throws Exception {
        // FILES_WEBDAV_GET_HEAD_FACADE
        given(filesFacadeService.download("/Team/readme one.md"))
                .willReturn(new DownloadedFile("readme one.md", "text/markdown", "hello".getBytes()));
        given(filesFacadeService.etagFor("/Team/readme one.md")).willReturn("\"etag-readme\"");

        mockMvc.perform(get("/dav/files/Team/readme one.md")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-readme\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-transform"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"readme one.md\""))
                .andExpect(content().bytes("hello".getBytes()));

        mockMvc.perform(request(HttpMethod.HEAD, "/dav/files/Team/readme one.md")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/markdown"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "5"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-readme\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-transform"))
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void requiresOidcBearerWorkspaceScopeForFirstPartyClients() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/files")
                        .header("Depth", "0"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/files")
                        .header("Depth", "0")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mapsForbiddenNotFoundAndLockedWithoutProviderLeakage() throws Exception {
        given(filesFacadeService.webDavPropfind("/Denied"))
                .willThrow(new ApiErrorException(
                        HttpStatus.FORBIDDEN,
                        "files-forbidden",
                        "Files access is not allowed for this Context/Space.",
                        Map.of("module", "files", "operation", "webdav-propfind")));
        given(filesFacadeService.download("/Missing.md"))
                .willThrow(new ApiErrorException(
                        HttpStatus.NOT_FOUND,
                        "file-not-found",
                        "The requested file or folder was not found.",
                        Map.of("module", "files", "operation", "download-file")));
        given(filesFacadeService.download("/Locked.md"))
                .willThrow(new ApiErrorException(
                        HttpStatus.CONFLICT,
                        "file-conflict",
                        "The file operation conflicts with the current storage state.",
                        Map.of("module", "files", "operation", "download-file", "downstreamStatus", 423)));

        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/files/Denied")
                        .header("Depth", "0")
                        .with(workspaceJwt()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Weave-Error-Code", "files-forbidden"))
                .andExpect(content().string(not(containsString("remote.php"))))
                .andExpect(content().string(not(containsString("Bearer"))));
        mockMvc.perform(get("/dav/files/Missing.md")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Weave-Error-Code", "file-not-found"));
        mockMvc.perform(get("/dav/files/Locked.md")
                        .with(workspaceJwt()))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Weave-Error-Code", "file-conflict"));
    }

    @Test
    void putMkcolAndDeleteUseWebDavFacadeWriteUseCases() throws Exception {
        // FILES_WEBDAV_PUT_MKCOL_DELETE_FACADE
        given(filesFacadeService.putWebDavFile(
                "/Team/readme.md",
                "new".getBytes(),
                "text/markdown",
                "\"etag-old\"",
                null,
                null))
                .willReturn(new WebDavMutationResult(
                        file("/Team/readme.md", "text/markdown", 3L),
                        "\"etag-new\"",
                        false));
        given(filesFacadeService.createWebDavFolder("/Team/Design", null, "*", null))
                .willReturn(new WebDavMutationResult(
                        folder("/Team/Design"),
                        "\"etag-folder\"",
                        true));

        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/dav/files/Team/readme.md")
                        .content("new")
                        .contentType("text/markdown")
                        .header(HttpHeaders.IF_MATCH, "\"etag-old\"")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-new\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/dav/files/Team/readme.md"));

        mockMvc.perform(request(HttpMethod.valueOf("MKCOL"), "/dav/files/Team/Design")
                        .header(HttpHeaders.IF_NONE_MATCH, "*")
                        .with(workspaceJwt()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-folder\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/dav/files/Team/Design/"));

        mockMvc.perform(request(HttpMethod.valueOf("DELETE"), "/dav/files/Team/old.md")
                        .header(HttpHeaders.IF_MATCH, "\"etag-old\"")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent());

        then(filesFacadeService).should().putWebDavFile(
                "/Team/readme.md",
                "new".getBytes(),
                "text/markdown",
                "\"etag-old\"",
                null,
                null);
        then(filesFacadeService).should().createWebDavFolder("/Team/Design", null, "*", null);
        then(filesFacadeService).should().deleteWebDavPath("/Team/old.md", "\"etag-old\"", null);
    }

    @Test
    void preconditionFailuresReturnStableWebDavErrorWithoutProviderLeakage() throws Exception {
        // FILES_WEBDAV_PRECONDITION_FACADE
        given(filesFacadeService.putWebDavFile(
                "/Team/readme.md",
                "new".getBytes(),
                "text/markdown",
                null,
                "*",
                null))
                .willThrow(new ApiErrorException(
                        HttpStatus.PRECONDITION_FAILED,
                        "files-precondition-failed",
                        "If-None-Match requires the target path to be absent.",
                        Map.of("module", "files", "operation", "webdav-put")));

        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/dav/files/Team/readme.md")
                        .content("new")
                        .contentType("text/markdown")
                        .header(HttpHeaders.IF_NONE_MATCH, "*")
                        .with(workspaceJwt()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string("X-Weave-Error-Code", "files-precondition-failed"))
                .andExpect(content().string(not(containsString("Nextcloud"))))
                .andExpect(content().string(not(containsString("remote.php"))))
                .andExpect(content().string(not(containsString("Bearer"))));
    }

    @Test
    void copyMoveLockAndUnlockUseWebDavFacadeUseCases() throws Exception {
        // FILES_WEBDAV_COPY_MOVE_LOCK_FACADE
        given(filesFacadeService.copyWebDavPath(
                "/Team/readme.md",
                "/Team/readme-copy.md",
                false,
                "\"etag-readme\"",
                null))
                .willReturn(new WebDavMutationResult(
                        file("/Team/readme-copy.md", "text/markdown", 12L),
                        "\"etag-copy\"",
                        true));
        given(filesFacadeService.moveWebDavPath(
                "/Team/readme.md",
                "/Archive/readme.md",
                true,
                null,
                null))
                .willReturn(new WebDavMutationResult(
                        file("/Archive/readme.md", "text/markdown", 12L),
                        "\"etag-move\"",
                        false));
        given(filesFacadeService.lockWebDavPath("/Team/readme.md", null))
                .willReturn(new WebDavLockResult("/Team/readme.md", "opaquelocktoken:test-lock", 3600));

        mockMvc.perform(request(HttpMethod.valueOf("COPY"), "/dav/files/Team/readme.md")
                        .header("Destination", "https://api.weave.test/dav/files/Team/readme-copy.md")
                        .header("Overwrite", "F")
                        .header(HttpHeaders.IF_MATCH, "\"etag-readme\"")
                        .with(workspaceJwt()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-copy\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/dav/files/Team/readme-copy.md"));

        mockMvc.perform(request(HttpMethod.valueOf("MOVE"), "/dav/files/Team/readme.md")
                        .header("Destination", "/dav/files/Archive/readme.md")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-move\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/dav/files/Archive/readme.md"));

        mockMvc.perform(request(HttpMethod.valueOf("LOCK"), "/dav/files/Team/readme.md")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Lock-Token", "<opaquelocktoken:test-lock>"))
                .andExpect(content().string(containsString("<d:lockdiscovery>")))
                .andExpect(content().string(containsString("opaquelocktoken:test-lock")));

        mockMvc.perform(request(HttpMethod.valueOf("UNLOCK"), "/dav/files/Team/readme.md")
                        .header("Lock-Token", "<opaquelocktoken:test-lock>")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent());

        then(filesFacadeService).should().copyWebDavPath(
                "/Team/readme.md",
                "/Team/readme-copy.md",
                false,
                "\"etag-readme\"",
                null);
        then(filesFacadeService).should().moveWebDavPath(
                "/Team/readme.md",
                "/Archive/readme.md",
                true,
                null,
                null);
        then(filesFacadeService).should().lockWebDavPath("/Team/readme.md", null);
        then(filesFacadeService).should().unlockWebDavPath("/Team/readme.md", "<opaquelocktoken:test-lock>");
    }

    @Test
    void copyAndMoveRejectDestinationsOutsideWeaveWebDavFacade() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("COPY"), "/dav/files/Team/readme.md")
                        .header("Destination", "https://files.example.test/remote.php/dav/files/user/leak.md")
                        .with(workspaceJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Weave-Error-Code", "webdav-destination-outside-facade"))
                .andExpect(content().string(containsString("Weave Files WebDAV facade")))
                .andExpect(content().string(not(containsString("remote.php"))))
                .andExpect(content().string(not(containsString("Bearer"))));

        mockMvc.perform(request(HttpMethod.valueOf("MOVE"), "/dav/files/Team/readme.md")
                        .header("Destination", "/provider/files/leak.md")
                        .with(workspaceJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Weave-Error-Code", "webdav-destination-outside-facade"));
    }

    @Test
    void quotaExceededMapsTo507WithoutProviderLeakage() throws Exception {
        // FILES_WEBDAV_QUOTA_FACADE
        given(filesFacadeService.putWebDavFile(
                "/Team/large.bin",
                new byte[] {1},
                "application/octet-stream",
                null,
                "*",
                null))
                .willThrow(new ApiErrorException(
                        HttpStatus.INSUFFICIENT_STORAGE,
                        "files-quota-exceeded",
                        "There is not enough storage available for this file operation.",
                        Map.of("module", "files", "operation", "webdav-put")));

        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/dav/files/Team/large.bin")
                        .content(new byte[] {1})
                        .contentType("application/octet-stream")
                        .header(HttpHeaders.IF_NONE_MATCH, "*")
                        .with(workspaceJwt()))
                .andExpect(status().isInsufficientStorage())
                .andExpect(header().string("X-Weave-Error-Code", "files-quota-exceeded"))
                .andExpect(content().string(not(containsString("Nextcloud"))))
                .andExpect(content().string(not(containsString("remote.php"))))
                .andExpect(content().string(not(containsString("Bearer"))));
    }

    private FileItemResponse file(String path, String mimeType, long size) {
        return new FileItemResponse(
                "files:" + path,
                path.substring(path.lastIndexOf('/') + 1),
                path,
                "file",
                mimeType,
                size,
                OffsetDateTime.parse("2026-07-04T12:01:00Z"),
                true);
    }

    private FileItemResponse folder(String path) {
        return new FileItemResponse(
                "files:" + path,
                path.substring(path.lastIndexOf('/') + 1),
                path,
                "folder",
                null,
                null,
                OffsetDateTime.parse("2026-07-04T12:01:00Z"),
                false);
    }

    private WebDavPropfindListing teamPropfindListing() {
        return new WebDavPropfindListing(
                new WebDavPropfindResource(folder("/Team"), "\"etag-team\""),
                List.of(
                        new WebDavPropfindResource(folder("/Team/Design"), "\"etag-design\""),
                        new WebDavPropfindResource(
                                new FileItemResponse(
                                        "files:readme",
                                        "readme one.md",
                                        "/Team/readme one.md",
                                        "file",
                                        "text/markdown",
                                        12L,
                                        OffsetDateTime.parse("2026-07-04T12:01:00Z"),
                                        true),
                                "\"etag-readme\"")),
                null);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }

    private ApiErrorException writePolicyRequired(String operation) {
        return new ApiErrorException(
                HttpStatus.NOT_IMPLEMENTED,
                "files-webdav-write-policy-required",
                "Files writes are blocked until the Weave WebDAV write policy is evidenced in #1007.",
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "webDavFacadePath", "/dav/files",
                        "writePolicyIssue", "#1007",
                        "openApiDataPlaneUsed", false,
                        "diagnosticsRedacted", true));
    }
}
