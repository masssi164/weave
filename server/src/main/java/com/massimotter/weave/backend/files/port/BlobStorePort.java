package com.massimotter.weave.backend.files.port;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Infrastructure Port for tenant-fenced immutable blob I/O used below Files provider adapters.
 * Provider selection remains at {@link FilesProviderPort}; concrete storage libraries stay behind this boundary.
 */
public interface BlobStorePort {

    boolean configured();

    BlobReceipt put(BlobScope scope, BlobReference reference, byte[] bytes, String expectedDigest);

    byte[] read(BlobScope scope, BlobReference reference);

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
                            || ".".equals(segment) || "..".equals(segment))) {
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

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
