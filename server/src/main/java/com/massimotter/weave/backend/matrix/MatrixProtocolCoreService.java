package com.massimotter.weave.backend.matrix;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MatrixProtocolCoreService {

    public static final String PROTOCOL_SURFACE = "matrix-client-server-facade";
    public static final String OIDC_GATEKEEPER = "spring-boot-resource-server";
    public static final String RUST_PROTOCOL_CORE = "ruma-serde-serde_json-thiserror-tracing";
    public static final String SERVER_JNI_BOUNDARY = "server-jni-wrapper";
    public static final String FLUTTER_BRIDGE_BOUNDARY = "flutter-rust-bridge";

    private static final String SERVER_NAME = "weave.local";
    private static final List<String> SUPPORTED_MATRIX_VERSIONS = List.of("v1.18");

    public Map<String, Object> versions() {
        return Map.of(
                "versions", SUPPORTED_MATRIX_VERSIONS,
                "unstable_features", Map.of(),
                "weaveBoundary", "northbound-matrix-client-server",
                "canonicalDomain", "chat",
                "providerDataPlaneExposed", false,
                "matrixCore", descriptor());
    }

    public Map<String, Object> descriptor() {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("protocolSurface", PROTOCOL_SURFACE);
        descriptor.put("oidcGatekeeper", OIDC_GATEKEEPER);
        descriptor.put("northboundHomeserverDependency", false);
        descriptor.put("rustProtocolCore", RUST_PROTOCOL_CORE);
        descriptor.put("serverJniBoundary", SERVER_JNI_BOUNDARY);
        descriptor.put("flutterBridgeBoundary", FLUTTER_BRIDGE_BOUNDARY);
        descriptor.put("nativeLibrary", NativeMatrixCore.LIBRARY_NAME);
        descriptor.put("nativeMethod", NativeMatrixCore.JNI_METHOD);
        descriptor.put("nativeLinked", false);
        descriptor.put("serverName", SERVER_NAME);
        descriptor.put("supportedMatrixVersions", SUPPORTED_MATRIX_VERSIONS);
        return descriptor;
    }

    public String matrixRoomId(String conversationId) {
        return "!" + sanitize(conversationId, "weave-room") + ":" + SERVER_NAME;
    }

    public String decodeRoomId(String matrixRoomId) {
        if (matrixRoomId != null && matrixRoomId.startsWith("!") && matrixRoomId.contains(":")) {
            return matrixRoomId.substring(1, matrixRoomId.indexOf(':'));
        }
        return matrixRoomId == null ? "weave-room" : matrixRoomId;
    }

    public String matrixSender(String senderRef) {
        String source = senderRef == null || senderRef.isBlank()
                ? "unknown"
                : senderRef.replaceFirst("^[^:]+:", "");
        return "@" + sanitize(source, "unknown") + ":" + SERVER_NAME;
    }

    public String matrixEventId(String id) {
        return "$" + sanitize(id, "weave-event") + ":" + SERVER_NAME;
    }

    private String sanitize(String value, String fallback) {
        String source = value == null || value.isBlank() ? fallback : value;
        String safe = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._=/-]", "_");
        safe = safe.replaceAll("^_+|_+$", "");
        return safe.isBlank() ? fallback : safe;
    }
}
