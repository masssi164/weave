package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Objects;

/**
 * Binding-free application read handle built from one exact canonical metadata observation.
 *
 * <p>Inspecting this value is metadata-only. Invoking {@link #transferTo(OutputStream)} performs
 * the separately verified content read through an opaque adapter-owned binding.</p>
 */
public final class VerifiedFileRead {

    private final FileObject item;
    private final FileVersion version;
    private final RepresentationHeaders headers;
    private final Instant observedAt;
    private final ContentTransfer contentTransfer;

    public VerifiedFileRead(
            FileObject item,
            FileVersion version,
            RepresentationHeaders headers,
            Instant observedAt,
            ContentTransfer contentTransfer) {
        this.item = Objects.requireNonNull(item, "item must not be null");
        this.version = Objects.requireNonNull(version, "version must not be null");
        this.headers = Objects.requireNonNull(headers, "headers must not be null");
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        this.contentTransfer = Objects.requireNonNull(
                contentTransfer,
                "contentTransfer must not be null");
        if (item.kind() != Kind.FILE
                || item.size() != headers.contentLength()
                || !Objects.equals(item.mediaType(), headers.contentType())) {
            throw new IllegalArgumentException("read headers must match canonical file metadata");
        }
    }

    public FileObject item() {
        return item;
    }

    public FileVersion version() {
        return version;
    }

    public RepresentationHeaders headers() {
        return headers;
    }

    public Instant observedAt() {
        return observedAt;
    }

    public void transferTo(OutputStream target) {
        contentTransfer.transferTo(Objects.requireNonNull(target, "target must not be null"));
    }

    public record RepresentationHeaders(
            long contentLength,
            String contentType,
            String strongEtag,
            String cacheControl) {

        public RepresentationHeaders {
            if (contentLength < 0) {
                throw new IllegalArgumentException("contentLength must not be negative");
            }
            if (contentType == null || contentType.isBlank()) {
                throw new IllegalArgumentException("contentType must not be blank");
            }
            if (strongEtag == null
                    || strongEtag.isBlank()
                    || strongEtag.startsWith("W/")) {
                throw new IllegalArgumentException("strongEtag must be a strong entity tag");
            }
            if (!"no-transform".equals(cacheControl)) {
                throw new IllegalArgumentException("cacheControl must prohibit transformation");
            }
        }
    }

    @FunctionalInterface
    public interface ContentTransfer {
        void transferTo(OutputStream target);
    }
}
