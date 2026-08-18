package com.massimotter.weave.core.transfer;

import java.util.List;

/** Readback comparison result after a provider target apply. */
public record TargetVerification(
        boolean equivalent,
        List<String> differenceCodes,
        String readbackStateSha256) {

    public TargetVerification {
        differenceCodes = List.copyOf(TransferValidation.require(
                differenceCodes, "differenceCodes"));
        for (String difference : differenceCodes) {
            TransferValidation.requireText(difference, "differenceCode");
        }
        readbackStateSha256 = TransferValidation.requireSha256(
                readbackStateSha256, "readbackStateSha256");
        if (equivalent && !differenceCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "an equivalent verification must not contain differences");
        }
        if (!equivalent && differenceCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "a failed verification requires at least one difference");
        }
    }

    public static TargetVerification equivalent(String readbackStateSha256) {
        return new TargetVerification(true, List.of(), readbackStateSha256);
    }
}
