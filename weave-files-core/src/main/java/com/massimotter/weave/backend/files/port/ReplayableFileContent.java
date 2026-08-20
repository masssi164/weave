package com.massimotter.weave.backend.files.port;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Binding-free application-port descriptor for one exact replayable file representation.
 *
 * <p>The source factory remains adapter-owned. Each opened stream is independently bounded and
 * must reproduce the declared size and digest. Paths, file descriptors, leases, and storage
 * bindings are deliberately absent from this contract.</p>
 */
public final class ReplayableFileContent {

    public static final int TRANSFER_BUFFER_BYTES = 65_536;

    private final long sizeBytes;
    private final String sha256Digest;
    private final String mediaType;
    private final StreamFactory sourceFactory;

    public ReplayableFileContent(
            long sizeBytes,
            String sha256Digest,
            String mediaType,
            StreamFactory sourceFactory) {
        if (sizeBytes < 0 || sizeBytes > FilesMutationPlan.JSON_SAFE_INTEGER_MAX) {
            throw new IllegalArgumentException("sizeBytes is outside the supported range");
        }
        if (sha256Digest == null || !sha256Digest.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("sha256Digest must be a lowercase sha256 digest");
        }
        if (!validMediaType(mediaType)) {
            throw new IllegalArgumentException("mediaType must be one deterministic field value");
        }
        this.sizeBytes = sizeBytes;
        this.sha256Digest = sha256Digest;
        this.mediaType = mediaType;
        this.sourceFactory = Objects.requireNonNull(sourceFactory, "sourceFactory must not be null");
    }

    private static boolean validMediaType(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            return false;
        }
        int cursor = tokenEnd(value, 0);
        if (cursor == 0 || cursor >= value.length() || value.charAt(cursor) != '/') {
            return false;
        }
        cursor++;
        int subtypeEnd = tokenEnd(value, cursor);
        if (subtypeEnd == cursor) {
            return false;
        }
        cursor = subtypeEnd;
        while (cursor < value.length()) {
            cursor = skipWhitespace(value, cursor);
            if (cursor >= value.length() || value.charAt(cursor++) != ';') {
                return false;
            }
            cursor = skipWhitespace(value, cursor);
            int nameEnd = tokenEnd(value, cursor);
            if (nameEnd == cursor) {
                return false;
            }
            cursor = skipWhitespace(value, nameEnd);
            if (cursor >= value.length() || value.charAt(cursor++) != '=') {
                return false;
            }
            cursor = skipWhitespace(value, cursor);
            if (cursor >= value.length()) {
                return false;
            }
            if (value.charAt(cursor) == '"') {
                cursor++;
                boolean closed = false;
                while (cursor < value.length()) {
                    char current = value.charAt(cursor++);
                    if (current == '\\') {
                        if (cursor >= value.length() || !quotedFieldCharacter(value.charAt(cursor++))) {
                            return false;
                        }
                    } else if (current == '"') {
                        closed = true;
                        break;
                    } else if (!quotedFieldCharacter(current)) {
                        return false;
                    }
                }
                if (!closed) {
                    return false;
                }
            } else {
                int valueEnd = tokenEnd(value, cursor);
                if (valueEnd == cursor) {
                    return false;
                }
                cursor = valueEnd;
            }
            cursor = skipWhitespace(value, cursor);
        }
        return true;
    }

    private static int tokenEnd(String value, int start) {
        int cursor = start;
        while (cursor < value.length() && tokenCharacter(value.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int skipWhitespace(String value, int start) {
        int cursor = start;
        while (cursor < value.length()
                && (value.charAt(cursor) == ' ' || value.charAt(cursor) == '\t')) {
            cursor++;
        }
        return cursor;
    }

    private static boolean tokenCharacter(char value) {
        return value > 0x20
                && value < 0x7f
                && "()<>@,;:\\\"/[]?={}".indexOf(value) < 0;
    }

    private static boolean quotedFieldCharacter(char value) {
        return value == '\t'
                || (value >= 0x20 && value <= 0x7e)
                || (value >= 0x80 && value <= 0xff);
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String sha256Digest() {
        return sha256Digest;
    }

    public String mediaType() {
        return mediaType;
    }

    /** Opens a fresh exact stream. Merely constructing or inspecting the descriptor opens nothing. */
    public ExactInputStream openStream() throws IOException {
        InputStream source = sourceFactory.openStream();
        if (source == null) {
            throw new IOException("content source returned no stream");
        }
        return new ExactInputStream(source, sizeBytes, sha256Digest);
    }

    @FunctionalInterface
    public interface StreamFactory {
        InputStream openStream() throws IOException;
    }

    /**
     * Fixed-read-size stream that fails closed on a short, oversized, or digest-invalid replay.
     */
    public static final class ExactInputStream extends InputStream {
        private final InputStream delegate;
        private final long expectedSize;
        private final String expectedDigest;
        private final MessageDigest digest;
        private long observedSize;
        private boolean verified;

        private ExactInputStream(
                InputStream delegate,
                long expectedSize,
                String expectedDigest) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
            this.expectedSize = expectedSize;
            this.expectedDigest = expectedDigest;
            this.digest = newSha256();
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : Byte.toUnsignedInt(one[0]);
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, target.length);
            if (length == 0) {
                return 0;
            }
            if (verified) {
                return -1;
            }
            long remaining = expectedSize - observedSize;
            int boundedLength = (int) Math.min(
                    Math.min((long) length, TRANSFER_BUFFER_BYTES),
                    Math.max(1L, remaining));
            int read = delegate.read(target, offset, boundedLength);
            if (read < 0) {
                verifyEndOfStream();
                return -1;
            }
            if (read == 0) {
                return 0;
            }
            if (observedSize + read > expectedSize) {
                throw mismatch("content source exceeded its declared size");
            }
            digest.update(target, offset, read);
            observedSize += read;
            return read;
        }

        /** Requires the consumer to have read the complete representation and its EOF marker. */
        public void requireComplete() throws IOException {
            if (observedSize != expectedSize) {
                throw mismatch("content source ended before its declared size");
            }
            if (!verified && read() != -1) {
                throw mismatch("content source exceeded its declared size");
            }
        }

        public long observedSize() {
            return observedSize;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(
                    Math.min((long) delegate.available(), TRANSFER_BUFFER_BYTES),
                    Math.max(0L, expectedSize - observedSize));
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        private void verifyEndOfStream() throws IOException {
            if (observedSize != expectedSize) {
                throw mismatch("content source ended before its declared size");
            }
            String actual = "sha256:" + HexFormat.of().formatHex(digest.digest());
            if (!MessageDigest.isEqual(
                    expectedDigest.getBytes(StandardCharsets.US_ASCII),
                    actual.getBytes(StandardCharsets.US_ASCII))) {
                throw mismatch("content source did not match its declared digest");
            }
            verified = true;
        }

        private ContentMismatchException mismatch(String message) {
            return new ContentMismatchException(message);
        }
    }

    public static final class ContentMismatchException extends IOException {
        public ContentMismatchException(String message) {
            super(message);
        }
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
