package com.massimotter.weave.backend.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "weave.agent-runtime.state-store")
public class AgentRuntimeStateStoreProperties {
    private boolean enabled;
    private Path wrappingKeyRoot;
    private DataSize maximumGenerationSize = DataSize.ofMegabytes(64);
    private String endpoint;
    private String region = "us-east-1";
    private String bucket;
    private String credentialRef;
    private Path accessKeyFile;
    private Path secretKeyFile;
    private boolean pathStyleAccess = true;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path wrappingKeyRoot() {
        return wrappingKeyRoot;
    }

    public void setWrappingKeyRoot(Path wrappingKeyRoot) {
        this.wrappingKeyRoot = wrappingKeyRoot;
    }

    public DataSize maximumGenerationSize() {
        return maximumGenerationSize;
    }

    public void setMaximumGenerationSize(DataSize maximumGenerationSize) {
        this.maximumGenerationSize = maximumGenerationSize;
    }

    public String endpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String region() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String bucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String credentialRef() { return credentialRef; }
    public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }
    public Path accessKeyFile() { return accessKeyFile; }
    public void setAccessKeyFile(Path accessKeyFile) { this.accessKeyFile = accessKeyFile; }
    public Path secretKeyFile() { return secretKeyFile; }
    public void setSecretKeyFile(Path secretKeyFile) { this.secretKeyFile = secretKeyFile; }
    public boolean pathStyleAccess() { return pathStyleAccess; }
    public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }

    public Path requiredWrappingKeyRoot() {
        if (wrappingKeyRoot == null) {
            throw new IllegalStateException(
                    "RuntimeStateStore requires an explicit operator-mounted wrapping-key SecretRef root");
        }
        return wrappingKeyRoot;
    }

    public long requiredMaximumGenerationBytes() {
        long value = maximumGenerationSize == null ? 0 : maximumGenerationSize.toBytes();
        if (value < 4_096 || value > 1024L * 1024L * 1024L) {
            throw new IllegalStateException(
                    "RuntimeStateStore maximum generation size must be between 4KiB and 1GiB");
        }
        return value;
    }

    public URI requiredEndpoint() {
        try {
            URI value = URI.create(required(endpoint, "endpoint"));
            if (value.getScheme() == null || value.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("RuntimeStateStore object endpoint must be an absolute URI", failure);
        }
    }

    public String requiredRegion() { return required(region, "region"); }
    public String requiredBucket() { return required(bucket, "bucket"); }
    public String requiredCredentialRef() {
        String value = required(credentialRef, "credential-ref");
        if (!value.matches("secretref:[A-Za-z0-9][A-Za-z0-9._:/-]{2,255}")) {
            throw new IllegalStateException("RuntimeStateStore object credential-ref is invalid");
        }
        return value;
    }

    public String readAccessKey() { return readMountedSecret(accessKeyFile, "access-key-file"); }
    public String readSecretKey() { return readMountedSecret(secretKeyFile, "secret-key-file"); }

    private static String readMountedSecret(Path configuredPath, String field) {
        if (configuredPath == null || !configuredPath.isAbsolute()) {
            throw new IllegalStateException("RuntimeStateStore object " + field + " must be an absolute mounted SecretRef file");
        }
        Path path = configuredPath.normalize();
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) < 1
                    || Files.size(path) > 4_096) {
                throw new IllegalStateException("RuntimeStateStore object " + field + " is unavailable or unsafe");
            }
            requirePrivatePermissions(path, field);
            String value = Files.readString(path, StandardCharsets.UTF_8).strip();
            if (value.isEmpty() || value.length() > 4_096
                    || value.chars().anyMatch(character -> Character.isISOControl(character))) {
                throw new IllegalStateException("RuntimeStateStore object " + field + " is empty or malformed");
            }
            return value;
        } catch (IOException failure) {
            throw new IllegalStateException("RuntimeStateStore object " + field + " could not be read", failure);
        }
    }

    private static void requirePrivatePermissions(Path path, String field) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!permissions.contains(PosixFilePermission.OWNER_READ)
                    || permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
                            || permission.name().startsWith("OTHERS_"))) {
                throw new IllegalStateException(
                        "RuntimeStateStore object " + field + " permissions are not private");
            }
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX hosts still receive no-follow, regular-file, absolute-path and size validation.
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("RuntimeStateStore object " + field + " is required");
        }
        return value.trim();
    }
}
