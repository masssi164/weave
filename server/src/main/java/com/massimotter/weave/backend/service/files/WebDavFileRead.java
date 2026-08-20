package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Egress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** One coherent, binding-free WebDAV file snapshot with deferred verified-body preparation. */
public final class WebDavFileRead {

    private final String filename;
    private final long contentLength;
    private final String contentType;
    private final String strongEtag;
    private final String cacheControl;
    private final long maximumContentBytes;
    private final Supplier<Egress> bodyPreparation;
    private final AtomicBoolean prepared = new AtomicBoolean();

    public WebDavFileRead(
            String filename,
            long contentLength,
            String contentType,
            String strongEtag,
            String cacheControl,
            Supplier<Egress> bodyPreparation) {
        this(
                filename,
                contentLength,
                contentType,
                strongEtag,
                cacheControl,
                Long.MAX_VALUE,
                bodyPreparation);
    }

    public WebDavFileRead(
            String filename,
            long contentLength,
            String contentType,
            String strongEtag,
            String cacheControl,
            long maximumContentBytes,
            Supplier<Egress> bodyPreparation) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (strongEtag == null || strongEtag.isBlank() || strongEtag.startsWith("W/")) {
            throw new IllegalArgumentException("strongEtag must be a strong entity tag");
        }
        if (!"no-transform".equals(cacheControl)) {
            throw new IllegalArgumentException("cacheControl must prohibit transformation");
        }
        if (maximumContentBytes < 1) {
            throw new IllegalArgumentException("maximumContentBytes must be positive");
        }
        this.filename = filename;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.strongEtag = strongEtag;
        this.cacheControl = cacheControl;
        this.maximumContentBytes = maximumContentBytes;
        this.bodyPreparation = Objects.requireNonNull(
                bodyPreparation,
                "bodyPreparation must not be null");
    }

    public String filename() {
        return filename;
    }

    public long contentLength() {
        return contentLength;
    }

    public String contentType() {
        return contentType;
    }

    public String strongEtag() {
        return strongEtag;
    }

    public String cacheControl() {
        return cacheControl;
    }

    public boolean withinContentProfile() {
        return contentLength <= maximumContentBytes;
    }

    /** HEAD deliberately never calls this method. */
    public Egress prepareBody() {
        if (!prepared.compareAndSet(false, true)) {
            throw new IllegalStateException("the WebDAV response body was already prepared");
        }
        return Objects.requireNonNull(
                bodyPreparation.get(),
                "bodyPreparation returned no egress handle");
    }
}
