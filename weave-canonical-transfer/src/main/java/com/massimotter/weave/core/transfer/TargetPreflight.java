package com.massimotter.weave.core.transfer;

import java.util.List;

/** Fail-closed target capability and policy decision before any provider mutation. */
public record TargetPreflight(
        boolean accepted,
        List<String> blockingReasonCodes) {

    public TargetPreflight {
        blockingReasonCodes = List.copyOf(TransferValidation.require(
                blockingReasonCodes, "blockingReasonCodes"));
        for (String reason : blockingReasonCodes) {
            TransferValidation.requireText(reason, "blockingReasonCode");
        }
        if (accepted && !blockingReasonCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "an accepted preflight must not contain blocking reasons");
        }
        if (!accepted && blockingReasonCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "a rejected preflight requires at least one blocking reason");
        }
    }

    public static TargetPreflight accepted() {
        return new TargetPreflight(true, List.of());
    }

    public static TargetPreflight rejected(String reasonCode) {
        return new TargetPreflight(false, List.of(reasonCode));
    }
}
