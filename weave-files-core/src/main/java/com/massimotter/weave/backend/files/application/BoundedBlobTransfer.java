package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReceipt;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.ReplayableFileContent;
import com.massimotter.weave.backend.files.port.ReplayableFileContent.ExactInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Fixed-memory immutable blob copy primitive shared by canonical copy use cases. */
final class BoundedBlobTransfer {

    private BoundedBlobTransfer() {
    }

    static BlobReceipt copy(
            BlobStorePort blobs,
            BlobScope scope,
            BlobReference source,
            BlobReference target,
            long expectedSize,
            String expectedDigest,
            String mediaType) {
        Objects.requireNonNull(blobs, "blobs must not be null");
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        try (PipedInputStream pipeIn = new PipedInputStream(ReplayableFileContent.TRANSFER_BUFFER_BYTES);
                PipedOutputStream pipeOut = new PipedOutputStream(pipeIn)) {
            Thread reader = Thread.ofVirtual()
                    .name("weave-files-copy-source")
                    .start(() -> {
                        try (OutputStream bounded = new ChunkingOutputStream(pipeOut)) {
                            blobs.readStream(scope, source, bounded);
                        } catch (Throwable failure) {
                            readFailure.set(failure);
                            closeQuietly(pipeOut);
                        }
                    });
            BlobReceipt receipt = null;
            RuntimeException publicationFailure = null;
            try {
                ReplayableFileContent content = new ReplayableFileContent(
                        expectedSize,
                        expectedDigest,
                        mediaType,
                        () -> pipeIn);
                try (ExactInputStream verified = content.openStream()) {
                    receipt = blobs.putStream(
                            scope,
                            target,
                            verified,
                            expectedSize,
                            expectedDigest);
                    verified.requireComplete();
                }
            } catch (IOException | RuntimeException failure) {
                publicationFailure = new TransferException("blob copy publication failed", failure);
            } finally {
                closeQuietly(pipeIn);
                join(reader);
            }
            if (publicationFailure != null) {
                throw publicationFailure;
            }
            Throwable sourceFailure = readFailure.get();
            if (sourceFailure != null) {
                throw new TransferException("blob copy source failed", sourceFailure);
            }
            if (receipt == null
                    || receipt.size() != expectedSize
                    || !receipt.reference().equals(target)
                    || !constantTime(receipt.digest(), expectedDigest)) {
                throw new TransferException("blob copy receipt did not match the expected representation");
            }
            return receipt;
        } catch (IOException failure) {
            throw new TransferException("blob copy pipe failed", failure);
        }
    }

    private static boolean constantTime(String left, String right) {
        return left != null
                && right != null
                && MessageDigest.isEqual(
                        left.getBytes(StandardCharsets.US_ASCII),
                        right.getBytes(StandardCharsets.US_ASCII));
    }

    private static void join(Thread reader) {
        try {
            reader.join();
        } catch (InterruptedException interrupted) {
            reader.interrupt();
            Thread.currentThread().interrupt();
            throw new TransferException("blob copy was interrupted", interrupted);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // The primary transfer result determines the support-safe failure.
        }
    }

    private static final class ChunkingOutputStream extends OutputStream {
        private final OutputStream target;

        private ChunkingOutputStream(OutputStream target) {
            this.target = target;
        }

        @Override
        public void write(int value) throws IOException {
            target.write(value);
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, value.length);
            int written = 0;
            while (written < length) {
                int chunk = Math.min(
                        ReplayableFileContent.TRANSFER_BUFFER_BYTES,
                        length - written);
                target.write(value, offset + written, chunk);
                written += chunk;
            }
        }

        @Override
        public void close() throws IOException {
            target.close();
        }
    }

    static final class TransferException extends RuntimeException {
        private TransferException(String message) {
            super(message);
        }

        private TransferException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
