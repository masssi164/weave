package com.massimotter.weave.backend.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerAuthorizationBoundaryTest {

    private static final Path CONTROLLER_SOURCE =
            Path.of("src/main/java/com/massimotter/weave/backend/controller");
    private static final Path MAIN_SOURCE = Path.of("src/main/java");

    private static final Pattern RAW_AUTHORIZATION_ACCESS = Pattern.compile(
            "@RequestHeader\\b[^\\n]*(?:HttpHeaders\\.AUTHORIZATION|[\"']Authorization[\"']|[\"']authorization[\"'])"
                    + "|getHeader\\s*\\(\\s*(?:HttpHeaders\\.AUTHORIZATION|[\"']Authorization[\"']|[\"']authorization[\"'])"
                    + "|HttpHeaders\\.AUTHORIZATION"
                    + "|[\"']Authorization[\"']"
                    + "|[\"']authorization[\"']");

    private static final Pattern DOWNSTREAM_HTTP_CLIENT = Pattern.compile(
            "\\b(RestTemplate|RestClient|WebClient|HttpClient|OkHttpClient|Feign)\\b"
                    + "|\\.exchange\\s*\\("
                    + "|\\.retrieve\\s*\\("
                    + "|\\.send\\s*\\("
                    + "|\\.execute\\s*\\(");

    private static final Pattern CALLER_JWT_TOKEN_FORWARDING = Pattern.compile(
            "\\b\\w*[Jj]wt\\w*\\s*\\.\\s*getTokenValue\\s*\\(");

    @Test
    void controllersDoNotAcceptRawAuthorizationHeaders() throws IOException {
        assertThat(filesMatching(CONTROLLER_SOURCE, RAW_AUTHORIZATION_ACCESS))
                .as("controllers must consume authenticated principals, not raw Authorization headers")
                .isEmpty();
    }

    @Test
    void controllersDoNotMakeDownstreamHttpCalls() throws IOException {
        assertThat(filesMatching(CONTROLLER_SOURCE, DOWNSTREAM_HTTP_CLIENT))
                .as("controllers must not become Matrix, Nextcloud, or Keycloak proxy endpoints")
                .isEmpty();
    }

    @Test
    void callerJwtTokensAreNotPreparedForForwarding() throws IOException {
        assertThat(filesMatching(MAIN_SOURCE, CALLER_JWT_TOKEN_FORWARDING))
                .as("backend integrations must use backend-owned credentials, not caller bearer tokens")
                .isEmpty();
    }

    private List<String> filesMatching(Path root, Pattern pattern) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsPattern(path, pattern))
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
    }

    private boolean containsPattern(Path path, Pattern pattern) {
        try {
            return pattern.matcher(Files.readString(path)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }
}
