package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.files.DownloadedFile;
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
    private JwtDecoder jwtDecoder;

    @Test
    void optionsAdvertisesReadOnlyWebdavMethods() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/dav/files")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("DAV", "1"))
                .andExpect(header().string(HttpHeaders.ALLOW, "OPTIONS, PROPFIND, GET, HEAD"));
    }

    @Test
    void propfindDepthZeroReturnsMultistatusForRequestedCollectionOnly() throws Exception {
        given(filesFacadeService.list("/Team")).willReturn(teamListing());

        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/files/Team/")
                        .header("Depth", "0")
                        .with(workspaceJwt()))
                .andExpect(status().is(207))
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<d:multistatus xmlns:d=\"DAV:\">")))
                .andExpect(content().string(containsString("<d:href>/dav/files/Team/</d:href>")))
                .andExpect(content().string(not(containsString("readme.md"))))
                .andExpect(content().string(not(containsString("Nextcloud"))))
                .andExpect(content().string(not(containsString("remote.php"))))
                .andExpect(content().string(not(containsString("Bearer"))));
    }

    @Test
    void propfindDepthOneReturnsChildrenAsDavResponses() throws Exception {
        given(filesFacadeService.list("/Team")).willReturn(teamListing());

        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/files/Team")
                        .header("Depth", "1")
                        .with(workspaceJwt()))
                .andExpect(status().is(207))
                .andExpect(content().string(containsString("<d:href>/dav/files/Team/Design/</d:href>")))
                .andExpect(content().string(containsString("<d:href>/dav/files/Team/readme%20one.md</d:href>")))
                .andExpect(content().string(containsString("<d:getcontenttype>text/markdown</d:getcontenttype>")))
                .andExpect(content().string(containsString("<d:getcontentlength>12</d:getcontentlength>")))
                .andExpect(content().string(not(containsString("files.example.test"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void getDownloadsFileThroughFacadePath() throws Exception {
        given(filesFacadeService.download("/Team/readme one.md"))
                .willReturn(new DownloadedFile("readme one.md", "text/markdown", "hello".getBytes()));

        mockMvc.perform(get("/dav/files/Team/readme one.md")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"readme one.md\""))
                .andExpect(content().bytes("hello".getBytes()));
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
        given(filesFacadeService.list("/Denied"))
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
    void writeAndLockMethodsAreExplicitlyNotImplementedUntilConflictPolicyExists() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/dav/files/Team/readme.md")
                        .content("new")
                        .with(workspaceJwt()))
                .andExpect(status().isNotImplemented())
                .andExpect(header().string("X-Weave-Error-Code", "webdav-method-not-implemented"))
                .andExpect(content().string(containsString("read-only")))
                .andExpect(content().string(containsString("ETag")))
                .andExpect(content().string(not(containsString("Nextcloud"))));

        mockMvc.perform(request(HttpMethod.valueOf("LOCK"), "/dav/files/Team/readme.md")
                        .with(workspaceJwt()))
                .andExpect(status().isNotImplemented())
                .andExpect(header().string(HttpHeaders.ALLOW, "OPTIONS, PROPFIND, GET, HEAD"));
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
                                "readme one.md",
                                "/Team/readme one.md",
                                "file",
                                "text/markdown",
                                12L,
                                OffsetDateTime.parse("2026-07-04T12:01:00Z"),
                                true)),
                null);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
