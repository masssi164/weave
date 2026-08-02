package com.massimotter.weave.backend.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.chat")
public record ChatRuntimeProperties(
        String provider,
        Matrix matrix) {

    public static final String MATRIX_SYNAPSE_PROVIDER = "matrix-synapse";

    public ChatRuntimeProperties {
        provider = normalized(provider, MATRIX_SYNAPSE_PROVIDER);
        matrix = matrix == null ? Matrix.defaults() : matrix;
    }

    public boolean matrixSynapseSelected() {
        return MATRIX_SYNAPSE_PROVIDER.equals(provider);
    }

    public record Matrix(
            String internalBaseUrl,
            String serverName,
            String appserviceId,
            String virtualUserPrefix,
            String asTokenFile,
            String hsTokenFile,
            Duration connectTimeout,
            Duration requestTimeout,
            Duration readinessCacheTtl,
            int callbackMaxBytes,
            int callbackMaxEvents) {

        public Matrix {
            internalBaseUrl = normalized(internalBaseUrl, "");
            serverName = normalized(serverName, "");
            appserviceId = normalized(appserviceId, "weave-chat-synapse");
            virtualUserPrefix = normalized(virtualUserPrefix, "_weave_");
            asTokenFile = normalized(asTokenFile, "");
            hsTokenFile = normalized(hsTokenFile, "");
            connectTimeout = positive(connectTimeout, Duration.ofSeconds(5));
            requestTimeout = positive(requestTimeout, Duration.ofSeconds(10));
            readinessCacheTtl = atLeast(
                    positive(readinessCacheTtl, Duration.ofSeconds(60)),
                    Duration.ofSeconds(60));
            callbackMaxBytes = bounded(callbackMaxBytes, 1_048_576, 65_536, 4_194_304);
            callbackMaxEvents = bounded(callbackMaxEvents, 100, 1, 500);
        }

        public static Matrix defaults() {
            return new Matrix("", "", "weave-chat-synapse", "_weave_", "", "",
                    Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(60),
                    1_048_576, 100);
        }

        public URI requiredInternalBaseUri() {
            if (internalBaseUrl.isBlank()) {
                throw new IllegalStateException("Matrix/Synapse Chat is selected but its internal base URL is missing.");
            }
            URI uri = URI.create(internalBaseUrl);
            if (uri.getUserInfo() != null || uri.getHost() == null || uri.getScheme() == null
                    || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                throw new IllegalStateException("Matrix/Synapse Chat internal base URL is invalid.");
            }
            return URI.create(internalBaseUrl.replaceAll("/+$", "") + "/");
        }

        public String requiredServerName() {
            if (serverName.isBlank() || serverName.contains("/") || serverName.contains("@")) {
                throw new IllegalStateException("Matrix/Synapse Chat server name is missing or invalid.");
            }
            return serverName;
        }

        public Path requiredAsTokenFile() {
            return requiredTokenFile(asTokenFile, "as_token");
        }

        public Path requiredHsTokenFile() {
            return requiredTokenFile(hsTokenFile, "hs_token");
        }

        private Path requiredTokenFile(String value, String label) {
            if (value.isBlank()) {
                throw new IllegalStateException("Matrix/Synapse Chat " + label + " file is not configured.");
            }
            return Path.of(value).toAbsolutePath().normalize();
        }
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static Duration atLeast(Duration value, Duration minimum) {
        return value.compareTo(minimum) < 0 ? minimum : value;
    }

    private static int bounded(int value, int fallback, int minimum, int maximum) {
        int normalized = value == 0 ? fallback : value;
        if (normalized < minimum || normalized > maximum) {
            throw new IllegalArgumentException("Matrix/Synapse Chat bound is outside the supported range.");
        }
        return normalized;
    }
}
