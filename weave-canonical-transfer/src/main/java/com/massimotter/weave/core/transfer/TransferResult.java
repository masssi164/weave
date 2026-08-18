package com.massimotter.weave.core.transfer;

/** Successful verified transfer of one bounded canonical source page. */
public record TransferResult<T>(
        CanonicalTransferBatch<T> batch,
        String idempotencyKey,
        TargetApplyReceipt receipt,
        TargetVerification verification,
        TransferCheckpoint nextCheckpoint) {

    public TransferResult {
        batch = TransferValidation.require(batch, "batch");
        idempotencyKey = TransferValidation.requireText(idempotencyKey, "idempotencyKey");
        receipt = TransferValidation.require(receipt, "receipt");
        verification = TransferValidation.require(verification, "verification");
        nextCheckpoint = TransferValidation.require(nextCheckpoint, "nextCheckpoint");
        if (!verification.equivalent()) {
            throw new IllegalArgumentException("a successful transfer result requires equivalent readback");
        }
        if (!idempotencyKey.equals(receipt.idempotencyKey())) {
            throw new IllegalArgumentException("receipt idempotency key does not match transfer result");
        }
    }
}
