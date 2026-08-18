package com.massimotter.weave.backend.chat.provider.synapse;

import java.time.Instant;

final class SynapseProviderException extends RuntimeException {

    private final String supportSafeCode;
    private final Instant retryAt;
    private final String matrixErrcode;

    SynapseProviderException(String supportSafeCode, Instant retryAt) {
        this(supportSafeCode, retryAt, null);
    }

    SynapseProviderException(String supportSafeCode, Instant retryAt, String matrixErrcode) {
        super("The configured Chat provider did not acknowledge the operation.");
        this.supportSafeCode = supportSafeCode;
        this.retryAt = retryAt;
        this.matrixErrcode = matrixErrcode;
    }

    String supportSafeCode() {
        return supportSafeCode;
    }

    Instant retryAt() {
        return retryAt;
    }

    String matrixErrcode() {
        return matrixErrcode;
    }
}
