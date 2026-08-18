package com.massimotter.weave.core.transfer;

/** Provider acknowledgement that can be persisted after an idempotent target apply. */
public record TargetApplyReceipt(
        String connectorKey,
        String idempotencyKey,
        String targetBatchRef,
        long appliedObjectCount,
        String targetStateSha256) {

    public TargetApplyReceipt {
        connectorKey = TransferValidation.requireText(connectorKey, "connectorKey");
        idempotencyKey = TransferValidation.requireText(idempotencyKey, "idempotencyKey");
        targetBatchRef = TransferValidation.requireText(targetBatchRef, "targetBatchRef");
        targetStateSha256 = TransferValidation.requireSha256(
                targetStateSha256, "targetStateSha256");
        if (appliedObjectCount < 0) {
            throw new IllegalArgumentException("appliedObjectCount must not be negative");
        }
    }
}
