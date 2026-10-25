package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only RuntimeProfile trust adapter for the MCP process.
 *
 * <p>The MCP deployment mounts only the public manifest, never the sibling
 * private signing-key files owned by weave-server.</p>
 */
public final class FileRuntimeProfileTrustKeyProvider implements RuntimeProfileTrustKeyProvider {
    private static final String SCHEMA = "weave.runtime-profile-signing-keys/v1";
    private static final int MAXIMUM_BYTES = 262_144;
    private static final Set<String> MANIFEST_FIELDS = Set.of(
            "schemaVersion",
            "initializationRefHash",
            "activeKeyId",
            "pendingKeyId",
            "activeRotationRefHash",
            "lastCompletedRotationRefHash",
            "keys");
    private static final Set<String> KEY_FIELDS = Set.of(
            "keyId", "status", "publicKeyX509", "privateKeyFile", "validFrom", "validUntil");

    private final Path manifest;
    private final ObjectMapper mapper;

    public FileRuntimeProfileTrustKeyProvider(Path manifest, ObjectMapper mapper) {
        if (manifest == null || !manifest.isAbsolute() || mapper == null) {
            throw new IllegalArgumentException("an absolute public trust manifest and ObjectMapper are required");
        }
        this.manifest = manifest.normalize();
        this.mapper = mapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    @Override
    public Optional<TrustKey> resolve(String keyId, Instant now) {
        if (keyId == null || keyId.isBlank() || now == null) {
            return Optional.empty();
        }
        return publishedKeys(now).stream().filter(key -> key.keyId().equals(keyId)).findFirst();
    }

    @Override
    public List<TrustKey> publishedKeys(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("trust publication time is required");
        }
        JsonNode root = read();
        if (!root.isObject()
                || !SCHEMA.equals(text(root, "schemaVersion"))
                || !Set.copyOf(root.propertyNames()).equals(MANIFEST_FIELDS)
                || !root.path("keys").isArray()
                || root.path("keys").isEmpty()) {
            throw unavailable("The RuntimeProfile public trust manifest is invalid", null);
        }
        List<TrustKey> keys = new java.util.ArrayList<>();
        for (JsonNode node : root.path("keys")) {
            if (!node.isObject() || !Set.copyOf(node.propertyNames()).equals(KEY_FIELDS)) {
                throw unavailable("The RuntimeProfile public trust key projection is invalid", null);
            }
            String keyId = text(node, "keyId");
            Instant validFrom = instant(node, "validFrom");
            Instant validUntil = instant(node, "validUntil");
            PublicKey publicKey = publicKey(text(node, "publicKeyX509"));
            if (!keyId.equals(keyId(publicKey))) {
                throw unavailable("The RuntimeProfile public trust key identifier is invalid", null);
            }
            TrustKey key = new TrustKey(keyId, publicKey, validFrom, validUntil);
            if (key.validAt(now)) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.comparing(TrustKey::keyId));
        if (keys.stream().map(TrustKey::keyId).distinct().count() != keys.size()) {
            throw unavailable("The RuntimeProfile public trust manifest is ambiguous", null);
        }
        return List.copyOf(keys);
    }

    private JsonNode read() {
        try {
            if (Files.isSymbolicLink(manifest)
                    || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(manifest)
                    || Files.size(manifest) < 2
                    || Files.size(manifest) > MAXIMUM_BYTES) {
                throw unavailable("The RuntimeProfile public trust manifest is unavailable", null);
            }
            return mapper.readTree(Files.readAllBytes(manifest));
        } catch (IOException failure) {
            throw unavailable("The RuntimeProfile public trust manifest is unavailable", failure);
        }
    }

    private static PublicKey publicKey(String encoded) {
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getUrlDecoder().decode(encoded)));
        } catch (Exception invalid) {
            throw unavailable("The RuntimeProfile public trust key is invalid", invalid);
        }
    }

    private static String keyId(PublicKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return "rpk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception impossible) {
            throw unavailable("The RuntimeProfile public trust key cannot be identified", impossible);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).isString() ? node.path(field).stringValue() : "";
        if (value.isBlank() || value.length() > 1000) {
            throw unavailable("The RuntimeProfile public trust manifest field is invalid", null);
        }
        return value;
    }

    private static Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(text(node, field));
        } catch (RuntimeException invalid) {
            throw unavailable("The RuntimeProfile public trust lifetime is invalid", invalid);
        }
    }

    private static RuntimeProfileSigningKeyException unavailable(String message, Throwable cause) {
        return cause == null
                ? new RuntimeProfileSigningKeyException(message)
                : new RuntimeProfileSigningKeyException(message, cause);
    }
}
