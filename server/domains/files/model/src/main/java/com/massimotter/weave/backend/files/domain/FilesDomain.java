package com.massimotter.weave.backend.files.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FilesDomain {

    private FilesDomain() {
    }

    public enum Kind {
        FILE,
        COLLECTION
    }

    public record FileId(String value) {
        public FileId {
            value = requireText(value, "file id");
        }
    }

    public record FilePath(String value) {
        public FilePath {
            value = normalize(value);
        }

        public boolean root() {
            return "/".equals(value);
        }

        public String name() {
            if (root()) {
                return "Files";
            }
            return value.substring(value.lastIndexOf('/') + 1);
        }

        public FilePath child(String name) {
            String segment = requireText(name, "file name");
            if (segment.contains("/") || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("file name must be one path segment");
            }
            return new FilePath(root() ? "/" + segment : value + "/" + segment);
        }

        private static String normalize(String raw) {
            if (raw == null || raw.isBlank() || "/".equals(raw.trim())) {
                return "/";
            }
            String candidate = raw.trim().replace('\\', '/');
            if (!candidate.startsWith("/")) {
                candidate = "/" + candidate;
            }
            while (candidate.contains("//")) {
                candidate = candidate.replace("//", "/");
            }
            if (candidate.endsWith("/")) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            for (String segment : candidate.substring(1).split("/")) {
                if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                    throw new IllegalArgumentException("file path contains an unsafe segment");
                }
            }
            return candidate;
        }
    }

    public record FileVersion(String value) {
        public FileVersion {
            value = value == null || value.isBlank() ? null : value.trim();
        }

        public static FileVersion unknown() {
            return new FileVersion(null);
        }

        public boolean known() {
            return value != null;
        }
    }

    public record FileObject(
            FileId id,
            FilePath path,
            Kind kind,
            long size,
            String mediaType,
            Instant modifiedAt,
            boolean hidden) {

        public FileObject {
            if (id == null || path == null || kind == null) {
                throw new IllegalArgumentException("file id, path, and kind are required");
            }
            if (size < 0) {
                throw new IllegalArgumentException("file size must not be negative");
            }
            mediaType = mediaType == null || mediaType.isBlank() ? null : mediaType.trim();
            if (kind == Kind.COLLECTION && size != 0) {
                throw new IllegalArgumentException("collections do not have a byte size");
            }
        }

        public String name() {
            return path.name();
        }
    }

    public record FileQuota(Long availableBytes, Long usedBytes) {
        public FileQuota {
            if (availableBytes != null && availableBytes < 0) {
                throw new IllegalArgumentException("available quota must not be negative");
            }
            if (usedBytes != null && usedBytes < 0) {
                throw new IllegalArgumentException("used quota must not be negative");
            }
        }

        public static FileQuota unknown() {
            return new FileQuota(null, null);
        }
    }

    public record FileListing(FilePath requestedPath, List<FileObject> children, FileQuota quota) {
        public FileListing {
            if (requestedPath == null) {
                throw new IllegalArgumentException("requested path is required");
            }
            children = children == null ? List.of() : List.copyOf(children);
            quota = quota == null ? FileQuota.unknown() : quota;
        }
    }

    public record VersionedFile(FileObject item, FileVersion version) {
        public VersionedFile {
            if (item == null) {
                throw new IllegalArgumentException("file item is required");
            }
            version = version == null ? FileVersion.unknown() : version;
        }
    }

    public record VersionedListing(
            FileListing listing,
            FileVersion requestedVersion,
            Map<FilePath, FileVersion> childVersions) {
        public VersionedListing {
            if (listing == null) {
                throw new IllegalArgumentException("file listing is required");
            }
            requestedVersion = requestedVersion == null ? FileVersion.unknown() : requestedVersion;
            childVersions = childVersions == null
                    ? Map.of()
                    : Map.copyOf(new LinkedHashMap<>(childVersions));
        }
    }

    public record FileContent(FileObject item, byte[] bytes) {
        public FileContent {
            if (item == null || item.kind() != Kind.FILE) {
                throw new IllegalArgumentException("file content requires a file item");
            }
            bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    public record FileWrite(FilePath path, byte[] bytes, String mediaType) {
        public FileWrite {
            if (path == null || path.root()) {
                throw new IllegalArgumentException("file write requires a non-root path");
            }
            bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
            mediaType = mediaType == null || mediaType.isBlank()
                    ? "application/octet-stream"
                    : mediaType.trim();
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
