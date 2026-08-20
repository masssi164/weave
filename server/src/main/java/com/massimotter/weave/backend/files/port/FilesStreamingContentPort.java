package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.ReplayableFileContent.StreamFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

/**
 * Server-private qualification seam for bounded Files representation transfer.
 *
 * <p>Only an adapter that can prove the complete bounded-content profile implements this port.
 * The handles deliberately expose no spool path, blob binding, provider value, owner marker, or
 * lease token.</p>
 */
public interface FilesStreamingContentPort {

    ContentProfile contentProfile();

    /** Revalidates the runtime-observed streaming capability and private authority generation. */
    void requireStreamingReady();

    Ingress receive(Long declaredLength, String mediaType, StreamFactory requestBody);

    VerifiedFileRead inspect(FilePath path);

    Egress verify(VerifiedFileRead read);

    record ContentProfile(
            long maximumContentBytes,
            int transferBufferBytes,
            int maximumIngressConcurrency,
            int maximumEgressConcurrency) {

        public ContentProfile {
            if (maximumContentBytes < 1
                    || maximumContentBytes > FilesMutationPlan.JSON_SAFE_INTEGER_MAX - 1) {
                throw new IllegalArgumentException("maximumContentBytes is outside the safe range");
            }
            if (transferBufferBytes < 1
                    || transferBufferBytes > ReplayableFileContent.TRANSFER_BUFFER_BYTES) {
                throw new IllegalArgumentException("transferBufferBytes is outside the safe range");
            }
            if (maximumIngressConcurrency < 1 || maximumEgressConcurrency < 1) {
                throw new IllegalArgumentException("content concurrency limits must be positive");
            }
        }
    }

    interface Ingress extends AutoCloseable {

        ReplayableFileContent content();

        /**
         * Binds this verified object to the deterministic operation and holds its owner lock until
         * the supplied Tx1 action has committed or failed.
         */
        <T> T bindThroughPlanCommit(String operationRef, Supplier<T> transaction);

        /** Deletes retained ingress only when the relational plan is now terminal. */
        boolean releaseIfTerminal();

        @Override
        void close();
    }

    interface Egress extends AutoCloseable {

        /** Opens the already-verified private representation exactly once. */
        InputStream openStream() throws IOException;

        @Override
        void close();
    }
}
