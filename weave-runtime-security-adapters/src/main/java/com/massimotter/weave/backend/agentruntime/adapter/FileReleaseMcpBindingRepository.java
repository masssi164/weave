package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.ReleaseMcpBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.port.ReleaseMcpBindingRepository;
import tools.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;

/** Reads a private, atomically replaceable release binding file on every MCP invocation. */
public final class FileReleaseMcpBindingRepository implements ReleaseMcpBindingRepository {
    private static final int MAXIMUM_BYTES = 65_536;
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "bindings");
    private static final Set<String> BINDING_FIELDS = Set.of(
            "bindingRef", "workloadIssuer", "workloadSubject", "workloadClientId",
            "organizationRef", "personRef", "memberIssuer", "memberSubject",
            "expiresAt", "allowedToolClasses", "active");
    private static final Set<PosixFilePermission> UNSAFE_PERMISSIONS = Set.of(
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);

    private final Path path;
    private final ObjectMapper mapper;

    public FileReleaseMcpBindingRepository(Path path, ObjectMapper mapper) {
        this.path = Objects.requireNonNull(path, "path");
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("the release MCP binding file path must be absolute");
        }
        this.mapper = Objects.requireNonNull(mapper, "mapper").rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    @Override
    public Optional<ReleaseMcpBinding> findByWorkload(String issuer, String subject) {
        try {
            BasicFileAttributes before = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile() || before.size() < 2 || before.size() > MAXIMUM_BYTES
                    || Files.isSymbolicLink(path)
                    || Files.isSymbolicLink(path.getParent())
                    || Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
                            .stream().anyMatch(UNSAFE_PERMISSIONS::contains)
                    || Files.getPosixFilePermissions(path.getParent(), LinkOption.NOFOLLOW_LINKS)
                            .contains(PosixFilePermission.GROUP_WRITE)
                    || Files.getPosixFilePermissions(path.getParent(), LinkOption.NOFOLLOW_LINKS)
                            .contains(PosixFilePermission.OTHERS_WRITE)) {
                throw unavailable();
            }
            byte[] bytes = Files.readAllBytes(path);
            BasicFileAttributes after = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (bytes.length > MAXIMUM_BYTES || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw unavailable();
            }
            return parse(bytes, issuer, subject);
        } catch (RuntimeException | IOException failure) {
            throw unavailable();
        }
    }

    private Optional<ReleaseMcpBinding> parse(byte[] bytes, String issuer, String subject) {
        JsonNode root = mapper.readTree(bytes);
        if (!root.isObject() || !Set.copyOf(root.propertyNames()).equals(ROOT_FIELDS)
                || !root.path("schemaVersion").isIntegralNumber()
                || root.path("schemaVersion").intValue() != 1
                || !root.path("bindings").isArray()
                || root.path("bindings").size() > 256) {
            throw unavailable();
        }
        String revision = digest(bytes);
        Set<String> seenSubjects = new HashSet<>();
        Set<String> seenReferences = new HashSet<>();
        ReleaseMcpBinding matching = null;
        for (JsonNode node : root.path("bindings")) {
            if (!node.isObject() || !Set.copyOf(node.propertyNames()).equals(BINDING_FIELDS)) {
                throw unavailable();
            }
            JsonNode classes = node.path("allowedToolClasses");
            if (!classes.isArray() || classes.size() == 0 || classes.size() > 3) {
                throw unavailable();
            }
            Set<String> tools = new HashSet<>();
            for (JsonNode tool : classes) {
                if (!tool.isString() || !tools.add(tool.stringValue())) {
                    throw unavailable();
                }
            }
            if (!node.path("active").isBoolean()) {
                throw unavailable();
            }
            ReleaseMcpBinding binding = new ReleaseMcpBinding(
                    text(node, "bindingRef"), text(node, "workloadIssuer"),
                    text(node, "workloadSubject"), text(node, "workloadClientId"),
                    text(node, "organizationRef"), text(node, "personRef"),
                    new RuntimeMemberBinding(text(node, "memberIssuer"), text(node, "memberSubject")),
                    revision, Instant.parse(text(node, "expiresAt")), tools,
                    node.path("active").booleanValue());
            if (!seenReferences.add(binding.bindingRef())
                    || !seenSubjects.add(binding.workloadIssuer() + "\u0000" + binding.workloadSubject())) {
                throw unavailable();
            }
            if (binding.workloadIssuer().equals(issuer) && binding.workloadSubject().equals(subject)) {
                matching = binding;
            }
        }
        return Optional.ofNullable(matching);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isString() || value.stringValue().isBlank()) {
            throw unavailable();
        }
        return value.stringValue();
    }

    private static String digest(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("The release MCP binding authority is unavailable");
    }
}
