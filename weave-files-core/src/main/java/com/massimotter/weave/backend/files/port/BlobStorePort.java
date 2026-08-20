package com.massimotter.weave.backend.files.port;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Infrastructure port for tenant-fenced immutable blob I/O below canonical Files use cases.
 * Concrete storage libraries, paths, credentials, and provider DTOs remain outside this contract.
 *
 * <p>Streaming is the only data-plane contract. Complete representations never cross this port as
 * byte arrays.</p>
 */
public interface BlobStorePort {

    /**
     * Marks a failure of the caller-owned content destination, distinct from a corrupt or
     * unreadable stored blob. Adapters must preserve this marker across the read boundary.
     */
    final class ContentTargetUnavailableException extends RuntimeException {
        public ContentTargetUnavailableException(Throwable cause) {
            super("blob content target is unavailable", Objects.requireNonNull(cause, "cause"));
        }
    }

    boolean configured();

    BlobReceipt putStream(
            BlobScope scope,
            BlobReference reference,
            InputStream source,
            long expectedSize,
            String expectedDigest);

    void readStream(BlobScope scope, BlobReference reference, OutputStream target);

    /** Returns a verified immutable receipt when the exact opaque binding is already durable. */
    default Optional<BlobReceipt> receipt(BlobScope scope, BlobReference reference) {
        return Optional.empty();
    }

    void delete(BlobScope scope, BlobReference reference);

    List<BlobReference> inventory(BlobScope scope, int limit);

    record BlobScope(String organizationRef, String spaceRef) {
        public BlobScope {
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
        }
    }

    record BlobReference(String value) {
        private static final Pattern SAFE = Pattern.compile("[a-z0-9][a-z0-9/_-]{0,1023}");

        public BlobReference {
            value = required(value, "blob reference");
            if (!SAFE.matcher(value).matches()
                    || Arrays.stream(value.split("/", -1)).anyMatch(segment -> segment.isBlank()
                    || ".".equals(segment)
                    || "..".equals(segment))) {
                throw new IllegalArgumentException("blob reference is not a safe opaque key");
            }
        }
    }

    record BlobReceipt(BlobReference reference, String digest, long size) {
        public BlobReceipt {
            reference = Objects.requireNonNull(reference, "reference must not be null");
            if (digest == null || !digest.matches("sha256:[a-f0-9]{64}")) {
                throw new IllegalArgumentException("digest must be a sha256 digest");
            }
            if (size < 0) {
                throw new IllegalArgumentException("size must not be negative");
            }
        }
    }

    static long transferBounded(
            InputStream source,
            OutputStream target,
            long maximumBytes) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("maximumBytes must not be negative");
        }
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        for (int read; (read = source.read(buffer)) >= 0; ) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > maximumBytes) {
                throw new IOException("stream exceeds configured bound");
            }
            target.write(buffer, 0, read);
        }
        return total;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
