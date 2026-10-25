package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NextcloudFilesAdapterTest {

    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder()
            .encodeToString("weave-service:app-password".getBytes(StandardCharsets.UTF_8));

    private MockRestServiceServer server;
    private NextcloudFilesAdapter adapter;

    private static final String ACCESS_DAV = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response><d:href>/remote.php/dav/files/weave-service/</d:href>
                <d:propstat><d:prop><d:getetag>"revision-1"</d:getetag>
                  <oc:fileid>42</oc:fileid><oc:owner-id>weave-service</oc:owner-id>
                  <oc:permissions>RGDNVW</oc:permissions></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
            """;

    private static final String EMPTY_SHARES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ocs><meta><statuscode>100</statuscode></meta><data/></ocs>
            """;

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
    void accessInspectionReadsDavAndOcsSharesButBlocksUnsupportedNativeGrantParity() {
        expectAccessDav(ACCESS_DAV);
        server.expect(requestTo("https://files.example.test/ocs/v2.php/apps/files_sharing/api/v1/shares"
                        + "?path=/&reshares=true&subfiles=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(header("OCS-APIRequest", "true"))
                .andRespond(withSuccess("""
                        <ocs><meta><statuscode>100</statuscode></meta><data>
                          <element><share_type>0</share_type><permissions>1</permissions></element>
                          <element><share_type>1</share_type><permissions>31</permissions></element>
                          <element><share_type>3</share_type><expiration>2026-10-30</expiration>
                            <password>redacted-value</password></element>
                        </data></ocs>
                        """, MediaType.APPLICATION_XML));

        FilesAccessInspection inspection = adapter.inspectAccessRoot(new FilePath("/"));

        assertThat(inspection.davObserved()).isTrue();
        assertThat(inspection.sharesObserved()).isTrue();
        assertThat(inspection.ownerObserved()).isTrue();
        assertThat(inspection.actorPermissionsObserved()).isTrue();
        assertThat(inspection.versionObserved()).isTrue();
        assertThat(inspection.providerFileIdObserved()).isTrue();
        assertThat(inspection.observedShareCount()).isEqualTo(3);
        assertThat(inspection.userShareCount()).isEqualTo(1);
        assertThat(inspection.groupShareCount()).isEqualTo(1);
        assertThat(inspection.linkShareCount()).isEqualTo(1);
        assertThat(inspection.conditionedShareCount()).isEqualTo(1);
        assertThat(inspection.blocked()).isTrue();
        assertThat(inspection.blockingReasons()).contains(
                "source-shares-require-target-enforcement",
                "source-effective-access-unverified",
                "source-full-inventory-unverified");
        assertThat(inspection.toString()).doesNotContain("redacted-value", "weave-service", "2026-10-30");
        server.verify();
    }

    @Test
    void zeroSharesStillBlocksUntilEveryEffectiveRightIsProven() {
        expectAccessDav(ACCESS_DAV);
        server.expect(requestTo("https://files.example.test/ocs/v2.php/apps/files_sharing/api/v1/shares"
                        + "?path=/&reshares=true&subfiles=true"))
                .andRespond(withSuccess(EMPTY_SHARES, MediaType.APPLICATION_XML));

        FilesAccessInspection inspection = adapter.inspectAccessRoot(new FilePath("/"));

        assertThat(inspection.sharesObserved()).isTrue();
        assertThat(inspection.observedShareCount()).isZero();
        assertThat(inspection.blockingReasons()).contains("source-effective-access-unverified");
        assertThat(inspection.blocked()).isTrue();
        server.verify();
    }

    @Test
    void accessCapabilityProfileDoesNotClaimSharesOrGrantsArePortable() {
        assertThat(adapter.conformanceProfile().fieldMappings())
                .containsEntry("share", MappingClass.LOSSY)
                .containsEntry("sourceOwner", MappingClass.MANUAL_REVIEW)
                .containsEntry("userGrant", MappingClass.LOSSY)
                .containsEntry("groupGrant", MappingClass.LOSSY)
                .containsEntry("inheritedGrant", MappingClass.UNSUPPORTED)
                .containsEntry("shareCondition", MappingClass.LOSSY)
                .containsEntry("effectiveAccess", MappingClass.MANUAL_REVIEW);
    }

    @Test
    void unavailableOcsInventoryCannotBeMistakenForNoShares() {
        expectAccessDav(ACCESS_DAV);
        server.expect(requestTo("https://files.example.test/ocs/v2.php/apps/files_sharing/api/v1/shares"
                        + "?path=/&reshares=true&subfiles=true"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        FilesAccessInspection inspection = adapter.inspectAccessRoot(new FilePath("/"));

        assertThat(inspection.sharesObserved()).isFalse();
        assertThat(inspection.observedShareCount()).isNull();
        assertThat(inspection.blockingReasons()).contains("source-share-inventory-unavailable");
        assertThat(inspection.blocked()).isTrue();
        server.verify();
    }

    @Test
    void davShareIndicatorCannotBeClearedByEmptyOcsResponse() {
        expectAccessDav(ACCESS_DAV.replace(
                "<oc:permissions>RGDNVW</oc:permissions>",
                "<oc:permissions>RGDNVW</oc:permissions>"
                        + "<oc:share-types><oc:share-type>3</oc:share-type></oc:share-types>"));
        server.expect(requestTo("https://files.example.test/ocs/v2.php/apps/files_sharing/api/v1/shares"
                        + "?path=/&reshares=true&subfiles=true"))
                .andRespond(withSuccess(EMPTY_SHARES, MediaType.APPLICATION_XML));

        FilesAccessInspection inspection = adapter.inspectAccessRoot(new FilePath("/"));

        assertThat(inspection.davShareIndicatorObserved()).isTrue();
        assertThat(inspection.blockingReasons()).contains("source-dav-share-indicator-unresolved");
        assertThat(inspection.blocked()).isTrue();
        server.verify();
    }

    @Test
    void malformedShareInventoryDoesNotReportObservedZeroShares() {
        expectAccessDav(ACCESS_DAV);
        server.expect(requestTo("https://files.example.test/ocs/v2.php/apps/files_sharing/api/v1/shares"
                        + "?path=/&reshares=true&subfiles=true"))
                .andRespond(withSuccess("""
                        <ocs><meta><statuscode>100</statuscode></meta>
                        <data><unexpected>unknown shape</unexpected></data></ocs>
                        """, MediaType.APPLICATION_XML));

        FilesAccessInspection inspection = adapter.inspectAccessRoot(new FilePath("/"));

        assertThat(inspection.sharesObserved()).isFalse();
        assertThat(inspection.observedShareCount()).isNull();
        assertThat(inspection.blockingReasons()).contains("source-share-inventory-unavailable");
        server.verify();
    }

    @Test
    void failedDavPropstatIsNotTreatedAsOwnerOrPermissionEvidence() {
        expectAccessDav(ACCESS_DAV.replace("HTTP/1.1 200 OK", "HTTP/1.1 403 Forbidden"));
        server.expect(requestTo("https://files.example.test/ocs/v2.php/apps/files_sharing/api/v1/shares"
                        + "?path=/&reshares=true&subfiles=true"))
                .andRespond(withSuccess(EMPTY_SHARES, MediaType.APPLICATION_XML));

        FilesAccessInspection inspection = adapter.inspectAccessRoot(new FilePath("/"));

        assertThat(inspection.davObserved()).isFalse();
        assertThat(inspection.ownerObserved()).isFalse();
        assertThat(inspection.actorPermissionsObserved()).isFalse();
        assertThat(inspection.blockingReasons()).contains("source-dav-access-unavailable");
        server.verify();
    }

    private void expectAccessDav(String xml) {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(header("Depth", "0"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<oc:owner-id")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<oc:permissions")))
                .andRespond(withStatus(HttpStatus.MULTI_STATUS)
                        .contentType(MediaType.APPLICATION_XML).body(xml));
    }

    @Test
    void scopedRequestsUseOnlyTheirOrganizationAndConfigurationAccount() {
        NextcloudFilesProperties accountA = configuredProperties();
        NextcloudFilesProperties accountB = new NextcloudFilesProperties(
                "https://files.example.test", "/remote.php/dav/files",
                "backend-service-account", "service-b", "password-b");
        Map<String, NextcloudFilesProperties> accounts = Map.of(
                "org-a/profile:a", accountA,
                "org-b/profile:b", accountB);
        NextcloudFilesAccountResolver resolver = new NextcloudFilesAccountResolver() {
            @Override
            public Optional<NextcloudFilesProperties> resolve(String organizationRef, String configurationRef) {
                return Optional.ofNullable(accounts.get(organizationRef + "/" + configurationRef));
            }

            @Override
            public boolean available() {
                return true;
            }
        };
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer scopedServer = MockRestServiceServer.bindTo(builder).build();
        NextcloudFilesAdapter staged = new NextcloudFilesAdapter(accountA, resolver, builder.build());
        scopedServer.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/report.md"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andRespond(withSuccess("a", MediaType.TEXT_PLAIN));
        scopedServer.expect(requestTo("https://files.example.test/remote.php/dav/files/service-b/report.md"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                        .encodeToString("service-b:password-b".getBytes(StandardCharsets.UTF_8))))
                .andRespond(withSuccess("b", MediaType.TEXT_PLAIN));

        var scopedA = staged.scoped(new FilesRequestScope("org-a", "workspace-default", 1, "profile:a"));
        var scopedB = staged.scoped(new FilesRequestScope("org-b", "workspace-default", 2, "profile:b"));
        assertThatThrownBy(() -> staged.inspectAccessRoot(new FilePath("/")))
                .isInstanceOf(ApiErrorException.class);
        assertThat(new String(scopedA.read(new FileId(FilePathCodec.toId("/report.md"))).bytes(), StandardCharsets.UTF_8))
                .isEqualTo("a");
        assertThat(new String(scopedB.read(new FileId(FilePathCodec.toId("/report.md"))).bytes(), StandardCharsets.UTF_8))
                .isEqualTo("b");
        assertThatThrownBy(() -> staged.scoped(
                new FilesRequestScope("org-b", "workspace-default", 2, "profile:a")))
                .isInstanceOf(ApiErrorException.class);
        scopedServer.verify();
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
        var upload = adapter.write(new FileWrite(
                new FilePath("/Team/readme.md"),
                "hello".getBytes(StandardCharsets.UTF_8),
                "text/markdown"));
        assertThat(upload.path().value()).isEqualTo("/Team/readme.md");
        assertThat(upload.kind()).isEqualTo(Kind.FILE);

        String fileId = FilePathCodec.toId("/Team/readme.md");
        var download = adapter.read(new FileId(fileId));
        assertThat(download.item().name()).isEqualTo("readme.md");
        assertThat(download.item().mediaType()).isEqualTo("text/plain");
        assertThat(download.bytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

        adapter.delete(new FilePath("/Team/readme.md"), FileVersion.unknown());
        server.verify();
    }

    @Test
    void boundedDownloadRejectsOversizedBodyAfterAtMostLimitPlusOneBytes() {
        String path = "https://files.example.test/remote.php/dav/files/weave-service/large.bin";
        server.expect(requestTo(path))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[] {0, 1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));
        assertThatThrownBy(() -> adapter.readBounded(new FileId(FilePathCodec.toId("/large.bin")), 3))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(exception.code()).isEqualTo("files-download-too-large");
                });
        server.verify();
    }

    @Test
    void conditionalBoundedDownloadRequiresTheObservedStrongProviderVersion() {
        String path = "https://files.example.test/remote.php/dav/files/weave-service/locked.bin";
        server.expect(requestTo(path))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"v1\""))
                .andRespond(withSuccess(new byte[] {0, 1}, MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.ETAG, "\"v1\""));
        server.expect(requestTo(path))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"v1\""))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        FileId id = new FileId(FilePathCodec.toId("/locked.bin"));
        assertThat(adapter.readBoundedIfVersion(id, 2, new FileVersion("\"v1\"")).bytes())
                .containsExactly((byte) 0, (byte) 1);
        assertThatThrownBy(() -> adapter.readBoundedIfVersion(id, 2, new FileVersion("\"v1\"")))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED));
        server.verify();
    }

    @Test
    void conditionalWritesSendAtomicProviderPreconditions() {
        String target = "https://files.example.test/remote.php/dav/files/weave-service/new.txt";
        server.expect(requestTo(target))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.IF_NONE_MATCH, "*"))
                .andRespond(withStatus(HttpStatus.CREATED).header("OC-FileId", "00000042ocabc"));
        server.expect(requestTo(target))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.IF_MATCH, "\"provider-v1\""))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        FileWrite write = new FileWrite(new FilePath("/new.txt"), new byte[] {0, 1}, "text/plain");
        var created = adapter.writeIfAbsent(write);
        assertThat(created.item().path()).isEqualTo(write.path());
        assertThat(created.providerObjectRef()).isEqualTo("nextcloud-object:00000042ocabc");
        assertThatThrownBy(() -> adapter.writeIfVersion(write, new FileVersion("\"provider-v1\"")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                    assertThat(exception.code()).isEqualTo("files-precondition-failed");
                });
        server.verify();
    }

    @Test
    void atomicFolderCreateRequiresDavCreatedStatus() {
        String target = "https://files.example.test/remote.php/dav/files/weave-service/new-folder";
        server.expect(requestTo(target))
                .andExpect(method(HttpMethod.valueOf("MKCOL")))
                .andRespond(withStatus(HttpStatus.CREATED).header("OC-FileId", "00000042ocabc"));
        server.expect(requestTo(target))
                .andExpect(method(HttpMethod.valueOf("MKCOL")))
                .andRespond(withStatus(HttpStatus.OK));

        assertThat(adapter.createCollectionIfAbsent(new FilePath("/new-folder")).item().kind())
                .isEqualTo(Kind.COLLECTION);
        assertThatThrownBy(() -> adapter.createCollectionIfAbsent(new FilePath("/new-folder")))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
        server.verify();
    }

    @Test
    void resolvesStableNextcloudFileIdInsteadOfPathDerivedAdapterId() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/readme.md"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andExpect(header("Depth", "0"))
                .andRespond(withStatus(HttpStatus.MULTI_STATUS).contentType(MediaType.APPLICATION_XML).body("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
                          <d:response><d:href>/remote.php/dav/files/weave-service/Team/readme.md</d:href>
                            <d:propstat><d:prop><oc:fileid>42</oc:fileid>
                              <oc:id>00000042ocabc</oc:id></d:prop>
                              <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                          </d:response>
                        </d:multistatus>
                        """));
        assertThat(adapter.providerObjectRef(new FilePath("/Team/readme.md")))
                .contains("nextcloud-object:00000042ocabc");
        server.verify();
    }

    @Test
    void missingNextcloudFileIdBlocksStableUserIdentity() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Team/readme.md"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andRespond(withStatus(HttpStatus.MULTI_STATUS).contentType(MediaType.APPLICATION_XML).body("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <d:multistatus xmlns:d="DAV:">
                          <d:response><d:href>/remote.php/dav/files/weave-service/Team/readme.md</d:href>
                            <d:propstat><d:prop></d:prop>
                              <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                          </d:response>
                        </d:multistatus>
                        """));
        assertThatThrownBy(() -> adapter.providerObjectRef(new FilePath("/Team/readme.md")))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("nextcloud-access-response-invalid"));
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

        assertThatThrownBy(() -> adapter.write(new FileWrite(
                new FilePath("/Team"), new byte[] {1}, "application/octet-stream")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("nextcloud-auth-failed");
                    assertThat(exception.details()).containsEntry("downstreamStatus", 401);
                    assertSupportSafe(exception);
                });

        assertThatThrownBy(() -> adapter.write(new FileWrite(
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

        assertThatThrownBy(() -> adapter.read(new FileId(FilePathCodec.toId("/Team/private.md"))))
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
