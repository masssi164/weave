package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
        adapter = new NextcloudFilesAdapter(configuredProperties(), builder);
    }

    @Test
    void remainsUnconfiguredUntilBackendActorCredentialsArePresent() {
        NextcloudFilesAdapter unconfigured = new NextcloudFilesAdapter(
                new NextcloudFilesProperties(
                        "https://files.weave.local",
                        "/remote.php/dav/files",
                        "backend-service-account",
                        "",
                        ""),
                RestClient.builder());

        assertThat(unconfigured.isConfigured()).isFalse();
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
                                    </d:prop></d:propstat>
                                  </d:response>
                                  <d:response>
                                    <d:href>/remote.php/dav/files/weave-service/Team/readme%20one.md</d:href>
                                    <d:propstat><d:prop>
                                      <d:resourcetype />
                                      <d:getcontentlength>12</d:getcontentlength>
                                      <d:getcontenttype>text/markdown</d:getcontenttype>
                                      <d:getlastmodified>Sun, 26 Apr 2026 08:01:00 GMT</d:getlastmodified>
                                    </d:prop></d:propstat>
                                  </d:response>
                                </d:multistatus>
                                """));

        FileListResponse response = adapter.list("/Team/");

        assertThat(response.path()).isEqualTo("/Team");
        assertThat(response.quota().usedBytes()).isEqualTo(10);
        assertThat(response.quota().totalBytes()).isEqualTo(100);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).type()).isEqualTo("folder");
        assertThat(response.items().get(0).path()).isEqualTo("/Team/Design");
        assertThat(response.items().get(0).downloadable()).isFalse();
        assertThat(response.items().get(1).type()).isEqualTo("file");
        assertThat(response.items().get(1).name()).isEqualTo("readme one.md");
        assertThat(response.items().get(1).mimeType()).isEqualTo("text/markdown");
        assertThat(response.items().get(1).size()).isEqualTo(12);
        assertThat(response.items().get(1).id()).startsWith("files:");
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

        assertThat(adapter.createFolder(new CreateFolderRequest("/Team", "Design")).path())
                .isEqualTo("/Team/Design");
        FileUploadResponse upload = adapter.upload("/Team", new MockMultipartFile(
                "file", "readme.md", "text/markdown", "hello".getBytes(StandardCharsets.UTF_8)));
        assertThat(upload.item().path()).isEqualTo("/Team/readme.md");
        assertThat(upload.item().downloadable()).isTrue();

        String fileId = FilePathCodec.toId("/Team/readme.md");
        DownloadedFile download = adapter.download(fileId);
        assertThat(download.filename()).isEqualTo("readme.md");
        assertThat(download.mimeType()).isEqualTo("text/plain");
        assertThat(download.content()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

        adapter.delete(fileId);
        server.verify();
    }

    @Test
    void mapsDownstreamNotFoundToStableProductError() {
        server.expect(requestTo("https://files.example.test/remote.php/dav/files/weave-service/Missing"))
                .andExpect(method(HttpMethod.valueOf("PROPFIND")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.list("/Missing"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("file-not-found");
                    assertThat(exception.details()).containsEntry("operation", "list-files");
                });
        server.verify();
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
