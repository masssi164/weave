package com.massimotter.weave.backend.chat.e2e;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceController;
import com.massimotter.weave.backend.config.ChatE2eProofProperties;
import com.massimotter.weave.backend.config.ChatE2eProofSecurityConfiguration;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Isolated-run-only trigger for replaying one genuine private Synapse callback. */
@RestController
@Hidden
@ConditionalOnProperty(name = "weave.chat.e2e-proof.enabled", havingValue = "true")
@ConditionalOnProperty(name = "weave.chat.provider", havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
public final class ChatE2eCallbackReplayController {

    public static final String PATH = ChatE2eProofSecurityConfiguration.PATH + "/callback-replay";
    private static final int MAX_BODY_BYTES = 512;

    private final ChatE2eCallbackReplayTap callbackReplayTap;
    private final MatrixApplicationServiceController applicationServiceController;
    private final ChatE2eProofProperties properties;
    private final ObjectMapper objectMapper;

    public ChatE2eCallbackReplayController(
            ChatE2eCallbackReplayTap callbackReplayTap,
            MatrixApplicationServiceController applicationServiceController,
            ChatE2eProofProperties properties,
            ObjectMapper objectMapper) {
        this.callbackReplayTap = callbackReplayTap;
        this.applicationServiceController = applicationServiceController;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @GetMapping(PATH + "/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean ready = callbackReplayTap.captured().isPresent();
        return ResponseEntity.ok(Map.of(
                "contractVersion", "chat-provider-callback-replay-readiness-v1",
                "callbackReplayReady", ready,
                "code", ready ? "chat-provider-callback-captured" : "chat-provider-callback-not-captured",
                "supportSafe", true));
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, Object>> replay(HttpServletRequest request) {
        try {
            JsonNode root = objectMapper.readTree(boundedBody(request));
            if (root == null || !root.isObject()
                    || !fieldNames(root).equals(Set.of("runId"))
                    || !constantTimeEquals(properties.requiredRunId(), root.path("runId").asString(null))) {
                return invalidRequest();
            }
            ChatE2eCallbackReplayTap.CapturedCallback captured = callbackReplayTap.captured().orElse(null);
            if (captured == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "code", "chat-provider-callback-not-captured",
                        "supportSafe", true));
            }
            ResponseEntity<Map<String, Object>> replay =
                    applicationServiceController.replayCapturedForIsolatedProof(captured);
            if (!replay.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "code", "chat-provider-callback-replay-failed",
                        "supportSafe", true));
            }
            return ResponseEntity.ok(Map.of(
                    "contractVersion", "chat-provider-callback-replay-v1",
                    "callbackCorrelationHash", sha256(captured.transactionId()),
                    "replayed", true,
                    "supportSafe", true));
        } catch (IOException | RuntimeException exception) {
            return invalidRequest();
        }
    }

    private byte[] boundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new IOException("proof request body is too large");
        }
        try (InputStream input = request.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IOException("proof request body is too large");
            }
            return bytes;
        }
    }

    private Set<String> fieldNames(JsonNode value) {
        java.util.Set<String> names = new java.util.HashSet<>();
        value.properties().forEach(entry -> names.add(entry.getKey()));
        return Set.copyOf(names);
    }

    private boolean constantTimeEquals(String expected, String candidate) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] candidateBytes = candidate == null ? new byte[0] : candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, candidateBytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ResponseEntity<Map<String, Object>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "chat-provider-callback-replay-request-invalid",
                "supportSafe", true));
    }
}
