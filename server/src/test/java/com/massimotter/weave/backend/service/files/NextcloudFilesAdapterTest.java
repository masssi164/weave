package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.service.files.UnqualifiedLegacyFilesContentAdapter.LegacyFileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NextcloudFilesAdapterTest {

    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder()
            .encodeToString("weave-service:app-password".getBytes(StandardCharsets.UTF_8));

    private MockRestServiceServer server;
    private NextcloudFilesAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new NextcloudFilesAdapter(configuredProperties(), builder.build());
    }

    @Test
    void remainsUnconfiguredUntilBackendActorCredentialsArePresent() {
        NextcloudFilesAdapter unconfigured = new NextcloudFilesAdapter(
                new NextcloudFilesProperties(
                        "https://files.weave.test",
                        "/remote.php/dav/files",
                        "backend-service-account",
                        "",
                        ""),
                RestClient.builder());

        assertThat(unconfigured.configured()).isFalse();
        assertThat(unconfigured.healthProbe().state().value()).isEqualTo("unavailable");
    }

    @Test
    void runtimeClientSupportsWebdavMethodsIndependentOfClasspathHttpFactories() throws Exception {
        HttpServer davServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        davServer.createContext("/remote.php/dav/files/weave-service/", exchange -> {
            byte[] response = """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <d:multistatus xmlns:d="DAV:">
                      <d:response>
                        <d:href>/remote.php/dav/files/weave-service/</d:href>
                        <d:propstat><d:prop>
                          <d:resourcetype><d:collection /></d:resourcetype>
                          <d:getetag>"etag-root"</d:getetag>
                        </d:prop></d:propstat>
                      </d:response>
                    </d:multistatus>
                    """.getBytes(StandardCharsets.UTF_8);
            if (!"PROPFIND".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(HttpStatus.METHOD_NOT_ALLOWED.value(), -1);
            } else {
                exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE);
                exchange.sendResponseHeaders(HttpStatus.MULTI_STATUS.value(), response.length);
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        davServer.start();
        try {
            NextcloudFilesAdapter runtimeAdapter = new NextcloudFilesAdapter(
                    new NextcloudFilesProperties(
                            "http://127.0.0.1:" + davServer.getAddress().getPort(),
                            "/remote.php/dav/files",
                            "backend-service-account",
                            "weave-service",
                            "app-password"),
                    RestClient.builder());

            assertThat(runtimeAdapter.list(new FilePath("/")).requestedVersion().value())
                    .isEqualTo("\"etag-root\"");
        } finally {
            davServer.stop(0);
        }
    }

    @Test
    void healthProbeNormalizesRateLimitingAndHonorsRetryAfterWithoutLeakingTheResponse() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(header("Depth", "0"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "180")
                        .body("blocked actor weave-service using app-password at https://files.example.test"));

        var result = adapter.healthProbe();

        assertThat(result.state().value()).isEqualTo("degraded");
        assertThat(result.supportSafeCode()).isEqualTo("files-storage-rate-limited");
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(180));
        assertThat(result.toString())
                .doesNotContain("weave-service")
                .doesNotContain("app-password")
                .doesNotContain("files.example.test");
        server.verify();
    }

    @Test
    void listsFolderContentsAndQuotaFromWebdavPropfind() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(header("Depth", "1"))
                .andRespond(withStatus(HttpStatus.MULTI_STATUS)
                        .contentType(MediaType.APPLICATION_XML)
                        .body("""
                                <?xml version=\"1.0\" encoding=\"utf-8\" ?>
                                <d:multistatus xmlns:d=\"DAV:\">
                                  <d:response>
                                    <d:href>/remote.php/dav/files/weave-service/Team/</d:href>
                                    <d:propstat><d:prop>
                                      <d:resourcetype><d:collection /></d:resourcetype>
                                      <d:quota-used-bytes>10</d:quota-used-bytes>
                                      <d:quota-available-bytes>90</d:quota-available-bytes>
                                    </d:prop></d:propstat>
                                  </d:response>
                                  <d:response>
                                    <d:href>/remote.php/dav/files/weave-service/Team/Design/</d:href>
                                    <d:propstat><d:prop>
                                      <d:resourcetype><d:collection /></d:resourcetype>
                                      <d:getlastmodified>Sun, 26 Apr 2026 08:00:00 GMT</d:getlastmodified>
                                      <d:getetag>\"etag-design\"</d:getetag>
                                    </d:prop></d:propstat>
                                  </d:response>
                                  <d:response>
                                    <d:href>/remote.php/dav/files/weave-service/Team/readme%20one.md</d:href>
                                    <d:propstat><d:prop>
                                      <d:resourcetype />
                                      <d:getcontentlength>12</d:getcontentlength>
                                      <d:getcontenttype>text/markdown</d:getcontenttype>
                                      <d:getlastmodified>Sun, 26 Apr 2026 08:01:00 GMT</d:getlastmodified>
                                      <d:getetag>\"etag-readme\"</d:getetag>
                                    </d:prop></d:propstat>
                                  </d:response>
                                </d:multistatus>
                                """));

        var response = adapter.list(new FilePath("/Team/")).listing();

        assertThat(response.requestedPath().value()).isEqualTo("/Team");
        assertThat(response.quota().usedBytes()).isEqualTo(10);
        assertThat(response.quota().availableBytes()).isEqualTo(90);
        assertThat(response.children()).hasSize(2);
        assertThat(response.children().get(0).kind()).isEqualTo(Kind.COLLECTION);
        assertThat(response.children().get(0).path().value()).isEqualTo("/Team/Design");
        assertThat(response.children().get(1).kind()).isEqualTo(Kind.FILE);
        assertThat(response.children().get(1).name()).isEqualTo("readme one.md");
        assertThat(response.children().get(1).mediaType()).isEqualTo("text/markdown");
        assertThat(response.children().get(1).size()).isEqualTo(12);
        assertThat(response.children().get(1).id().value()).startsWith("files:");
        server.verify();
    }

    @Test
    void normalizesNextcloudNonFiniteQuotaSentinelsAtTheAdapterBoundary() {
        for (String sentinel : new String[] {"-1", "-2", "-3"}) {
            server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/"))
                    .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                    .andExpect(header("Depth", "1"))
                    .andRespond(withStatus(HttpStatus.MULTI_STATUS)
                            .contentType(MediaType.APPLICATION_XML)
                            .body("""
                                    <?xml version="1.0" encoding="utf-8" ?>
                                    <d:multistatus xmlns:d="DAV:">
                                      <d:response>
                                        <d:href>/remote.php/dav/files/weave-service/</d:href>
                                        <d:propstat><d:prop>
                                          <d:resourcetype><d:collection /></d:resourcetype>
                                          <d:quota-used-bytes>10</d:quota-used-bytes>
                                          <d:quota-available-bytes>%s</d:quota-available-bytes>
                                        </d:prop></d:propstat>
                                      </d:response>
                                    </d:multistatus>
                                    """.formatted(sentinel)));
        }

        for (int index = 0; index < 3; index++) {
            var quota = adapter.list(new FilePath("/")).listing().quota();

            assertThat(quota.usedBytes()).isEqualTo(10);
            assertThat(quota.availableBytes()).isNull();
        }
        server.verify();
    }

    @Test
    void exposesVersionTokensFromTheSameWebdavPropfindResponse() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andExpect(header("Depth", "1"))
                .andRespond(withStatus(HttpStatus.MULTI_STATUS)
                        .contentType(MediaType.APPLICATION_XML)
                        .body("""
                                <?xml version=\"1.0\" encoding=\"utf-8\" ?>
                                <d:multistatus xmlns:d=\"DAV:\">
                                  <d:response>
                                    <d:href>/remote.php/dav/files/weave-service/Team/</d:href>
                                    <d:propstat><d:prop>
                                      <d:resourcetype><d:collection /></d:resourcetype>
                                      <d:getetag>\"etag-team\"</d:getetag>
                                    </d:prop></d:propstat>
                                  </d:response>
                                  <d:response>
                                    <d:href>/remote.php/dav/files/weave-service/Team/readme.md</d:href>
                                    <d:propstat><d:prop>
                                      <d:resourcetype />
                                      <d:getetag>\"etag-readme\"</d:getetag>
                                    </d:prop></d:propstat>
                                  </d:response>
                                </d:multistatus>
                                """));

        VersionedListing response = adapter.list(new FilePath("/Team/"));

        assertThat(response.requestedVersion().value()).isEqualTo("\"etag-team\"");
        assertThat(response.childVersions())
                .containsEntry(new FilePath("/Team/readme.md"), new FileVersion("\"etag-readme\""));
        server.verify();
    }

    @Test
    void createsUploadsDownloadsAndDeletesThroughBackendActorWebdavCalls() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/Design"))
                .andExpect(method(HttpMethod.valueOf("MKCOL")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/readme.md"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/readme.md"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andRespond(withSuccess("hello", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/readme.md"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(adapter.createCollection(new FilePath("/Team/Design")).path().value())
                .isEqualTo("/Team/Design");
        var upload = adapter.writeLegacy(new LegacyFileWrite(
                new FilePath("/Team/readme.md"),
                "hello".getBytes(StandardCharsets.UTF_8),
                "text/markdown"));
        assertThat(upload.path().value()).isEqualTo("/Team/readme.md");
        assertThat(upload.kind()).isEqualTo(Kind.FILE);

        String fileId = FilePathCodec.toId("/Team/readme.md");
        var download = adapter.readLegacy(new FileId(fileId));
        assertThat(download.item().name()).isEqualTo("readme.md");
        assertThat(download.item().mediaType()).isEqualTo("text/plain");
        assertThat(download.bytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

        adapter.delete(new FilePath("/Team/readme.md"), FileVersion.unknown());
        server.verify();
    }

    @Test
    void mapsDownstreamNotFoundToStableProductError() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Missing"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.list(new FilePath("/Missing")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("file-not-found");
                    assertThat(exception.details()).containsEntry("operation", "list-files");
                });
        server.verify();
    }

    @Test
    void rejectsTraversalBeforeWebdavRequestLeavesBackend() {
        assertThatThrownBy(() -> adapter.list(new FilePath("/Team/../Secrets")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file path contains an unsafe segment");
        server.verify();
    }

    @Test
    void mapsAuthAndQuotaFailuresWithoutLeakingProviderSecrets() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("app-password rejected for weave-service at https://files.example.test"));
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/large.bin"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.INSUFFICIENT_STORAGE)
                        .body("quota exceeded on /remote.php/dav/files/weave-service"));

        assertThatThrownBy(() -> adapter.writeLegacy(new LegacyFileWrite(
                new FilePath("/Team"), new byte[] {1}, "application/octet-stream")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("nextcloud-auth-failed");
                    assertThat(exception.details()).containsEntry("downstreamStatus", 401);
                    assertSupportSafe(exception);
                });

        assertThatThrownBy(() -> adapter.writeLegacy(new LegacyFileWrite(
                new FilePath("/Team/large.bin"), new byte[1024 * 1024], "application/octet-stream")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.INSUFFICIENT_STORAGE);
                    assertThat(exception.code()).isEqualTo("files-quota-exceeded");
                    assertThat(exception.details()).containsEntry("downstreamStatus", 507);
                    assertSupportSafe(exception);
                });
        server.verify();
    }

    @Test
    void mapsPermissionAndDeletionConflictsToStableProductErrors() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/private.md"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("raw provider permission body"));
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/locked.md"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.LOCKED).body("locked by downstream provider"));

        assertThatThrownBy(() -> adapter.readLegacy(new FileId(FilePathCodec.toId("/Team/private.md"))))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.code()).isEqualTo("files-permission-denied");
                    assertSupportSafe(exception);
                });
        assertThatThrownBy(() -> adapter.delete(new FilePath("/Team/locked.md"), FileVersion.unknown()))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("file-conflict");
                    assertThat(exception.details()).containsEntry("downstreamStatus", 423);
                    assertSupportSafe(exception);
                });
        server.verify();
    }

    private void assertSupportSafe(ApiErrorException exception) {
        String rendered = exception.getMessage() + " " + exception.details();
        assertThat(rendered)
                .doesNotContain("app-password")
                .doesNotContain("files.example.test")
                .doesNotContain("/remote.php/dav")
                .doesNotContain("raw provider")
                .doesNotContain("weave-service:");
    }

    private NextcloudFilesProperties configuredProperties() {
        return new NextcloudFilesProperties(
                "https://files.example.test",
                "/remote.php/dav/files",
                "backend-service-account",
                "weave-service",
                "app-password");
    }
}
