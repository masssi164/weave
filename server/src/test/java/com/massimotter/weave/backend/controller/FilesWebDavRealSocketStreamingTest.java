package com.massimotter.weave.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Egress;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.files.WebDavFileRead;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import com.massimotter.weave.backend.service.files.WebDavPutRequest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Real-socket proof for the bounded WebDAV controller projection. */
@SpringBootTest(
        classes = FilesWebDavRealSocketStreamingTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilesWebDavRealSocketStreamingTest {

    private static final byte[] LARGE_CONTENT = patternedBytes(2 * 1024 * 1024 + 37);

    @LocalServerPort
    private int port;

    @Autowired
    private FilesFacadeService facade;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void resetFacade() {
        reset(facade);
    }

    @Test
    void realSocketGetStreamsVerifiedContentWhileHeadAndNotModifiedOpenNoBody() throws Exception {
        // FILES_WEBDAV_REAL_SOCKET_BOUNDED_STREAMING
        AtomicInteger preparations = new AtomicInteger();
        when(facade.openWebDavPath("/Team/large.bin"))
                .thenAnswer(ignored -> read(preparations));

        HttpResponse<byte[]> get = client.send(
                request("/dav/files/Team/large.bin").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.headers().firstValue("Content-Length")).contains(
                Integer.toString(LARGE_CONTENT.length));
        assertThat(get.headers().firstValue("ETag")).contains("\"streaming-etag\"");
        assertThat(get.body()).isEqualTo(LARGE_CONTENT);
        assertThat(preparations).hasValue(1);

        HttpResponse<byte[]> head = client.send(
                request("/dav/files/Team/large.bin")
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.body()).isEmpty();
        assertThat(preparations).hasValue(1);

        HttpResponse<byte[]> notModified = client.send(
                request("/dav/files/Team/large.bin")
                        .header("If-None-Match", "W/\"streaming-etag\"")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(notModified.statusCode()).isEqualTo(304);
        assertThat(notModified.body()).isEmpty();
        assertThat(preparations).hasValue(1);
    }

    @Test
    void realSocketChunkedPutReachesFacadeAsUnknownLengthAndBoundedReads() throws Exception {
        // FILES_WEBDAV_REAL_SOCKET_CHUNKED_PUT
        AtomicLong observedBytes = new AtomicLong();
        AtomicInteger maximumReadRequest = new AtomicInteger();
        when(facade.putWebDavFile(
                anyString(), any(WebDavPutRequest.class), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    WebDavPutRequest put = invocation.getArgument(1);
                    assertThat(put.contentLengthFields()).isEmpty();
                    assertThat(put.transferEncodingFields())
                            .singleElement()
                            .asString()
                            .isEqualToIgnoringCase("chunked");
                    byte[] buffer = new byte[65_536];
                    try (var source = put.requestBody().openStream()) {
                        int read;
                        while ((read = source.read(buffer)) >= 0) {
                            if (read == 0) {
                                continue;
                            }
                            maximumReadRequest.accumulateAndGet(read, Math::max);
                            observedBytes.addAndGet(read);
                        }
                    }
                    return createdResult();
                });

        HttpResponse<byte[]> response = client.send(
                request("/dav/files/Team/chunked.bin")
                        .header("Content-Type", "application/octet-stream")
                        .header("Idempotency-Key", "real-socket-chunked-put-0001")
                        .PUT(HttpRequest.BodyPublishers.ofInputStream(
                                () -> new ByteArrayInputStream(LARGE_CONTENT)))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(observedBytes).hasValue(LARGE_CONTENT.length);
        assertThat(maximumReadRequest.get()).isBetween(1, 65_536);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20));
    }

    private static WebDavFileRead read(AtomicInteger preparations) {
        return new WebDavFileRead(
                "large.bin",
                LARGE_CONTENT.length,
                "application/octet-stream",
                "\"streaming-etag\"",
                "no-transform",
                () -> {
                    preparations.incrementAndGet();
                    return new Egress() {
                        @Override
                        public ByteArrayInputStream openStream() {
                            return new ByteArrayInputStream(LARGE_CONTENT);
                        }

                        @Override
                        public void close() {
                        }
                    };
                });
    }

    private static WebDavMutationResult createdResult() {
        return new WebDavMutationResult(
                new FileItemResponse(
                        "file-1",
                        "chunked.bin",
                        "/Team/chunked.bin",
                        "file",
                        "application/octet-stream",
                        (long) LARGE_CONTENT.length,
                        OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                        true),
                "\"chunked-etag\"",
                true);
    }

    private static byte[] patternedBytes(int size) {
        byte[] value = new byte[size];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (index % 251);
        }
        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(FilesWebDavController.class)
    static class TestApplication {

        @Bean
        FilesFacadeService filesFacadeService() {
            return mock(FilesFacadeService.class);
        }

        @Bean
        @Order(0)
        SecurityFilterChain permitTestSocketTraffic(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/dav/files", "/dav/files/**")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .build();
        }
    }
}
