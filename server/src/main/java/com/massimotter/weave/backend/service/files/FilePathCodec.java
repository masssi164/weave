package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.exception.ApiErrorException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriUtils;

final class FilePathCodec {

    private static final String ID_PREFIX = "files:";

    private FilePathCodec() {
    }

    static String toId(String normalizedPath) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalizedPath.getBytes(StandardCharsets.UTF_8));
        return ID_PREFIX + encoded;
    }

    static String pathFromId(String id) {
        if (id == null || id.isBlank()) {
            throw invalidPath("File identifier is required.");
        }
        String trimmed = id.trim();
        if (trimmed.startsWith(ID_PREFIX)) {
            String encoded = trimmed.substring(ID_PREFIX.length());
            try {
                return normalizeProductPath(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException exception) {
                throw invalidPath("File identifier is not valid.");
            }
        }
        if (trimmed.startsWith("/")) {
            return normalizeProductPath(trimmed);
        }
        throw invalidPath("File identifier is not valid.");
    }

    static String normalizeProductPath(String path) {
        if (path == null || path.isBlank()) {
            throw invalidPath("Path is required.");
        }
        String normalized = path.trim().replaceAll("/{2,}", "/");
        if (!normalized.startsWith("/")) {
            throw invalidPath("Path must start with /.");
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.indexOf('\0') >= 0 || normalized.contains("\\")) {
            throw invalidPath("Path contains unsupported characters.");
        }
        for (String segment : segments(normalized)) {
            validatePathSegment(segment, "Path contains unsupported segments.");
        }
        return normalized;
    }

    static String validateFileName(String name) {
        if (name == null || name.isBlank()) {
            throw invalidPath("File name is required.");
        }
        String trimmed = name.trim();
        validatePathSegment(trimmed, "File name contains unsupported characters.");
        return trimmed;
    }

    static String childPath(String parentPath, String name) {
        String parent = normalizeProductPath(parentPath);
        String childName = validateFileName(name);
        return "/".equals(parent) ? "/" + childName : parent + "/" + childName;
    }

    static String encodeWebdavPath(String normalizedProductPath) {
        String path = normalizeProductPath(normalizedProductPath);
        if ("/".equals(path)) {
            return "";
        }
        return Arrays.stream(segments(path))
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .reduce("", (left, right) -> left + "/" + right);
    }

    private static String[] segments(String normalizedProductPath) {
        if ("/".equals(normalizedProductPath)) {
            return new String[0];
        }
        return normalizedProductPath.substring(1).split("/");
    }

    private static void validatePathSegment(String segment, String message) {
        if (segment.isBlank()
                || ".".equals(segment)
                || "..".equals(segment)
                || segment.contains("/")
                || segment.contains("\\")
                || segment.indexOf('\0') >= 0) {
            throw invalidPath(message);
        }
    }

    private static ApiErrorException invalidPath(String message) {
        return new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "invalid-file-path",
                message,
                Map.of("module", "files"));
    }
}
