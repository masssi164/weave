package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Egress;
import com.massimotter.weave.backend.files.domain.FilesSearch.ScopeDepth;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.files.WebDavFileRead;
import com.massimotter.weave.backend.service.files.WebDavLockResult;
import com.massimotter.weave.backend.service.files.WebDavPropfindListing;
import com.massimotter.weave.backend.service.files.WebDavPropfindResource;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import com.massimotter.weave.backend.service.files.WebDavPutRequest;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest;
import com.massimotter.weave.backend.service.files.WebDavSearchResult;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @MockitoBean
    private FilesFacadeService filesFacadeService;

    @MockitoBean
    private DeviceCredentialService deviceCredentialService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void optionsAdvertisesWebdavMethods() throws Exception {
        given(filesFacadeService.webDavSearchQualified()).willReturn(true);

        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/dav/files")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("DAV", "1"))
                .andExpect(header().string("DASL", "<DAV:basicsearch>"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.ALLOW, "OPTIONS, PROPFIND, SEARCH, GET, HEAD, PUT, DELETE, MKCOL, MOVE, COPY, LOCK, UNLOCK"));
    }

    @Test
    void optionsOmitsSearchAndDaslWhenSelectedAdapterIsUnqualified() throws Exception {
        given(filesFacadeService.webDavSearchQualified()).willReturn(false);

        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/dav/files")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("DASL"))
                .andExpect(header().string(HttpHeaders.ALLOW,
                        "OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL, MOVE, COPY, LOCK, UNLOCK"));
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
                .andExpect(content().string(containsString(
                        "<d:multistatus xmlns:d=\"DAV:\" xmlns:w=\"urn:weave:files\">")))
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
    void searchUsesBoundedBasicsearchAndReturnsCanonicalIds() throws Exception {
        given(filesFacadeService.webDavSearch(any()))
                .willReturn(new WebDavSearchResult(List.of(
                        new WebDavPropfindResource(
                                file("/Team/readme.md", "text/markdown", 12L),
                                "\"etag-readme\"")), false));

        mockMvc.perform(request(HttpMethod.valueOf("SEARCH"), "/dav/files/Team")
                        .contentType("application/xml")
                        .content("""
                                <?xml version="1.0" encoding="UTF-8"?>
                                <d:searchrequest xmlns:d="DAV:" xmlns:w="urn:weave:files" xmlns:x="urn:unknown">
                                  <d:basicsearch>
                                    <d:select><d:prop><d:displayname/><d:getcontenttype/><x:portable-note/></d:prop></d:select>
                                    <d:from><d:scope><d:href>/dav/files/Team</d:href><d:depth>infinity</d:depth></d:scope></d:from>
                                    <d:where><d:like><d:prop><d:displayname/></d:prop><d:literal>%readme%</d:literal></d:like></d:where>
                                    <d:limit><d:nresults>10</d:nresults></d:limit>
                                  </d:basicsearch>
                                </d:searchrequest>
                                """)
                        .with(workspaceJwt()))
                .andExpect(status().is(207))
                .andExpect(content().contentType("application/xml;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(containsString("<w:canonical-id>files:/Team/readme.md</w:canonical-id>")))
                .andExpect(content().string(containsString("HTTP/1.1 404 Not Found")))
                .andExpect(content().string(containsString("portable-note")))
                .andExpect(content().string(not(containsString("remote.php"))));

        ArgumentCaptor<WebDavSearchRequest> requestCaptor = ArgumentCaptor.forClass(WebDavSearchRequest.class);
        then(filesFacadeService).should().webDavSearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().arbiterPath()).isEqualTo("/Team");
        assertThat(requestCaptor.getValue().scopePath()).isEqualTo("/Team");
        assertThat(requestCaptor.getValue().scopeDepth()).isEqualTo(ScopeDepth.INFINITY);
        assertThat(requestCaptor.getValue().limit()).isEqualTo(10);
    }

    @Test
    void searchAuthenticationFailuresUseSupportSafeDavXml() throws Exception {
        String body = """
                <d:searchrequest xmlns:d="DAV:"><d:basicsearch>
                  <d:select><d:allprop/></d:select>
                  <d:from><d:scope><d:href>/dav/files</d:href><d:depth>0</d:depth></d:scope></d:from>
                </d:basicsearch></d:searchrequest>
                """;

        mockMvc.perform(request(HttpMethod.valueOf("SEARCH"), "/dav/files")
                        .contentType("application/xml")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/xml;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Weave-Error-Code", "unauthorized"))
                .andExpect(content().string(containsString("<d:error xmlns:d=\"DAV:\">")))
                .andExpect(content().string(not(containsString("Bearer "))));

        mockMvc.perform(request(HttpMethod.valueOf("SEARCH"), "/dav/files")
                        .contentType("application/xml")
                        .content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_other"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/xml;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Weave-Error-Code", "forbidden"))
                .andExpect(content().string(containsString("<d:error xmlns:d=\"DAV:\">")))
                .andExpect(content().string(not(containsString("SCOPE_other"))));

        then(filesFacadeService).shouldHaveNoInteractions();
    }

    @Test
    void searchSupportsExactCanonicalIdPredicateForResourceResolution() throws Exception {
        given(filesFacadeService.webDavSearch(any()))
                .willReturn(new WebDavSearchResult(List.of(
                        new WebDavPropfindResource(
                                file("/Team/readme.md", "text/markdown", 12L),
                                "\"etag-readme\"")), true));

        mockMvc.perform(request(HttpMethod.valueOf("SEARCH"), "/dav/files")
                        .contentType("application/xml")
                        .content("""
                                <d:searchrequest xmlns:d="DAV:" xmlns:w="urn:weave:files">
                                  <d:basicsearch>
                                    <d:select><d:prop><w:canonical-id/></d:prop></d:select>
                                    <d:from><d:scope><d:href>/dav/files</d:href><d:depth>infinity</d:depth></d:scope></d:from>
                                    <d:where><d:eq><d:prop><w:canonical-id/></d:prop><d:literal>files:/Team/readme.md</d:literal></d:eq></d:where>
                                    <d:limit><d:nresults>2</d:nresults></d:limit>
                                  </d:basicsearch>
                                </d:searchrequest>
                                """)
                        .with(workspaceJwt()))
                .andExpect(status().is(207))
                .andExpect(content().string(containsString("<w:canonical-id>files:/Team/readme.md</w:canonical-id>")))
                .andExpect(content().string(containsString("HTTP/1.1 507 Insufficient Storage")))
                .andExpect(content().string(containsString("<d:href>/dav/files</d:href>")));
    }

    @Test
    void searchRejectsCanonicalIdPredicateWithoutExactEq() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("SEARCH"), "/dav/files")
                        .contentType("application/xml")
                        .content("""
                                <d:searchrequest xmlns:d="DAV:" xmlns:w="urn:weave:files">
                                  <d:basicsearch>
                                    <d:select><d:prop><w:canonical-id/></d:prop></d:select>
                                    <d:from><d:scope><d:href>/dav/files</d:href><d:depth>infinity</d:depth></d:scope></d:from>
                                    <d:where><d:like><d:prop><w:canonical-id/></d:prop><d:literal>files:</d:literal></d:like></d:where>
                                  </d:basicsearch>
                                </d:searchrequest>
                                """)
                        .with(workspaceJwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Weave-Error-Code", "webdav-search-invalid"));
    }

    @Test
    void searchRejectsExternalEntitiesAndUnboundedInput() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("SEARCH"), "/dav/files")
                        .contentType("application/xml")
                        .content("""
                                <!DOCTYPE x [<!ENTITY leak SYSTEM "file:///etc/passwd">]>
                                <d:searchrequest xmlns:d="DAV:"><d:basicsearch>
                                  <d:from><d:scope><d:href>/dav/files</d:href></d:scope></d:from>
                                  <d:where><d:literal>&leak;</d:literal></d:where>
                                </d:basicsearch></d:searchrequest>
                                """)
                        .with(workspaceJwt()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Weave-Error-Code", "webdav-search-invalid"))
                .andExpect(content().string(containsString("<d:no-external-entities/>")))
                .andExpect(content().string(not(containsString("root:"))));
    }

    @Test
    void getDownloadsFileThroughFacadePath() throws Exception {
        // FILES_WEBDAV_GET_HEAD_FACADE
        given(filesFacadeService.openWebDavPath("/Team/readme one.md"))
                .willReturn(webDavRead(
                        "readme one.md",
                        "text/markdown",
                        "\"etag-readme\"",
                        "hello".getBytes()));

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
    void readConditionsPrecedeTheSnapshotBoundAndExactMediaTypeIsPreserved() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean prepared = new java.util.concurrent.atomic.AtomicBoolean();
        WebDavFileRead oversized = new WebDavFileRead(
                "mixed.txt",
                5,
                "Text/Plain; Charset=\"UTF-8\"",
                "\"etag-mixed\"",
                "no-transform",
                4,
                () -> {
                    prepared.set(true);
                    throw new AssertionError("oversized or conditional reads must not prepare egress");
                });
        given(filesFacadeService.openWebDavPath("/Team/mixed.txt")).willReturn(oversized);

        mockMvc.perform(get("/dav/files/Team/mixed.txt")
                        .header(HttpHeaders.IF_NONE_MATCH, "W/\"etag-mixed\"")
                        .with(workspaceJwt()))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-mixed\""));
        mockMvc.perform(request(HttpMethod.HEAD, "/dav/files/Team/mixed.txt")
                        .header(HttpHeaders.IF_MATCH, "\"different\"")
                        .with(workspaceJwt()))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(request(HttpMethod.HEAD, "/dav/files/Team/mixed.txt")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(
                        "X-Weave-Error-Code", "files-streaming-capacity-unavailable"));
        org.assertj.core.api.Assertions.assertThat(prepared.get()).isFalse();

        given(filesFacadeService.openWebDavPath("/Team/exact-media.txt"))
                .willReturn(webDavRead(
                        "exact-media.txt",
                        "Text/Plain; Charset=\"UTF-8\"",
                        "\"etag-exact-media\"",
                        "hello".getBytes()));
        mockMvc.perform(get("/dav/files/Team/exact-media.txt")
                        .header(HttpHeaders.IF_MATCH, "\"other\"", "\"etag-exact-media\"")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "Text/Plain; Charset=\"UTF-8\""));
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
        given(filesFacadeService.openWebDavPath("/Missing.md"))
                .willThrow(new ApiErrorException(
                        HttpStatus.NOT_FOUND,
                        "file-not-found",
                        "The requested file or folder was not found.",
                        Map.of("module", "files", "operation", "download-file")));
        given(filesFacadeService.openWebDavPath("/Locked.md"))
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
                eq("/Team/readme.md"),
                any(WebDavPutRequest.class),
                eq("\"etag-older\",\"etag-old\""),
                isNull(),
                isNull(),
                eq("files-put-idempotency-0001")))
                .willReturn(new WebDavMutationResult(
                        file("/Team/readme.md", "text/markdown", 3L),
                        "\"etag-new\"",
                        false));
        given(filesFacadeService.createWebDavFolder(
                "/Team/Design", null, "\"missing-a\",\"missing-b\"", null, null))
                .willReturn(new WebDavMutationResult(
                        folder("/Team/Design"),
                        "\"etag-folder\"",
                        true));

        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/dav/files/Team/readme.md")
                        .content("new")
                        .contentType("text/markdown")
                        .header(HttpHeaders.IF_MATCH, "\"etag-older\"", "\"etag-old\"")
                        .header("Idempotency-Key", "files-put-idempotency-0001")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-new\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/dav/files/Team/readme.md"));

        mockMvc.perform(request(HttpMethod.valueOf("MKCOL"), "/dav/files/Team/Design")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"missing-a\"")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"missing-b\"")
                        .with(workspaceJwt()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-folder\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/dav/files/Team/Design/"));

        mockMvc.perform(request(HttpMethod.valueOf("DELETE"), "/dav/files/Team/old.md")
                        .header(HttpHeaders.IF_MATCH, "\"etag-older\"")
                        .header(HttpHeaders.IF_MATCH, "\"etag-old\"")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<WebDavPutRequest> putRequest = ArgumentCaptor.forClass(WebDavPutRequest.class);
        then(filesFacadeService).should().putWebDavFile(
                eq("/Team/readme.md"),
                putRequest.capture(),
                eq("\"etag-older\",\"etag-old\""),
                isNull(),
                isNull(),
                eq("files-put-idempotency-0001"));
        org.assertj.core.api.Assertions.assertThat(putRequest.getValue().contentTypeFields())
                .singleElement()
                .asString()
                .startsWith("text/markdown");
        try (var source = putRequest.getValue().requestBody().openStream()) {
            org.assertj.core.api.Assertions.assertThat(source.readAllBytes())
                    .isEqualTo("new".getBytes());
        }
        then(filesFacadeService).should().createWebDavFolder(
                "/Team/Design", null, "\"missing-a\",\"missing-b\"", null, null);
        then(filesFacadeService).should().deleteWebDavPath(
                "/Team/old.md", "\"etag-older\",\"etag-old\"", null, null, null);
    }

    @Test
    void preconditionFailuresReturnStableWebDavErrorWithoutProviderLeakage() throws Exception {
        // FILES_WEBDAV_PRECONDITION_FACADE
        given(filesFacadeService.putWebDavFile(
                eq("/Team/readme.md"),
                any(WebDavPutRequest.class),
                isNull(),
                eq("*"),
                isNull(),
                isNull()))
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
                "\"etag-readme-older\",\"etag-readme\"",
                null,
                null,
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
                "\"missing-a\",\"missing-b\"",
                null,
                null))
                .willReturn(new WebDavMutationResult(
                        file("/Archive/readme.md", "text/markdown", 12L),
                        "\"etag-move\"",
                        false));
        given(filesFacadeService.lockWebDavPath("/Team/readme.md", null, null))
                .willReturn(new WebDavLockResult("/Team/readme.md", "opaquelocktoken:test-lock", 3600));

        mockMvc.perform(request(HttpMethod.valueOf("COPY"), "/dav/files/Team/readme.md")
                        .header("Destination", "https://api.weave.test/dav/files/Team/readme-copy.md")
                        .header("Overwrite", "F")
                        .header(HttpHeaders.IF_MATCH, "\"etag-readme-older\"")
                        .header(HttpHeaders.IF_MATCH, "\"etag-readme\"")
                        .with(workspaceJwt()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-copy\""))
                .andExpect(header().string(HttpHeaders.LOCATION, "/dav/files/Team/readme-copy.md"));

        mockMvc.perform(request(HttpMethod.valueOf("MOVE"), "/dav/files/Team/readme.md")
                        .header("Destination", "/dav/files/Archive/readme.md")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"missing-a\"")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"missing-b\"")
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
                "\"etag-readme-older\",\"etag-readme\"",
                null,
                null,
                null);
        then(filesFacadeService).should().moveWebDavPath(
                "/Team/readme.md",
                "/Archive/readme.md",
                true,
                null,
                "\"missing-a\",\"missing-b\"",
                null,
                null);
        then(filesFacadeService).should().lockWebDavPath("/Team/readme.md", null, null);
        then(filesFacadeService).should().unlockWebDavPath(
                "/Team/readme.md", "<opaquelocktoken:test-lock>", null);
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
                eq("/Team/large.bin"),
                any(WebDavPutRequest.class),
                isNull(),
                eq("*"),
                isNull(),
                isNull()))
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

    @Test
    void unsupportedPutContentCodingAdvertisesIdentityOnly() throws Exception {
        given(filesFacadeService.putWebDavFile(
                eq("/Team/encoded.bin"),
                any(WebDavPutRequest.class),
                isNull(),
                isNull(),
                isNull(),
                isNull()))
                .willThrow(new ApiErrorException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "files-content-coding-unsupported",
                        "Files PUT accepts only the identity content coding.",
                        Map.of("module", "files", "diagnosticsRedacted", true)));

        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/dav/files/Team/encoded.bin")
                        .content("encoded")
                        .contentType("application/octet-stream")
                        .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                        .with(workspaceJwt()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT_ENCODING, "identity"))
                .andExpect(header().string(
                        "X-Weave-Error-Code", "files-content-coding-unsupported"));
    }

    private WebDavFileRead webDavRead(
            String filename,
            String mediaType,
            String etag,
            byte[] content) {
        return new WebDavFileRead(
                filename,
                content.length,
                mediaType,
                etag,
                "no-transform",
                () -> new Egress() {
                    @Override public java.io.InputStream openStream() {
                        return new ByteArrayInputStream(content);
                    }

                    @Override public void close() {
                    }
                });
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
        return jwt().jwt(jwt -> jwt.claim(
                        "organization",
                        HumanJwtTestSupport
                                .organizationWithRole("member")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
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
