package com.massimotter.weave.core.transfer;

/** Explicit outcome for one source field or object that crosses a provider boundary. */
public record FidelityFinding(
        String fieldPath,
        Classification classification,
        String reasonCode,
        ArchiveReference archiveReference) {

    public FidelityFinding {
        fieldPath = TransferValidation.requireText(fieldPath, "fieldPath");
        classification = TransferValidation.require(classification, "classification");
        reasonCode = TransferValidation.requireText(reasonCode, "reasonCode");
        if (classification == Classification.ARCHIVE_ONLY && archiveReference == null) {
            throw new IllegalArgumentException("ARCHIVE_ONLY requires an archiveReference");
        }
    }

    public enum Classification {
        PORTABLE,
        LOSSY,
        UNSUPPORTED,
        MANUAL_REVIEW,
        VENDOR_LOCKED,
        ARCHIVE_ONLY
    }
}
