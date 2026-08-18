package com.massimotter.weave.core.transfer;

/** Reference to bounded provider data retained without making it active canonical state. */
public record ArchiveReference(
        String archiveId,
        String mediaType,
        String sha256,
        long byteCount) {

    public ArchiveReference {
        archiveId = TransferValidation.requireText(archiveId, "archiveId");
        mediaType = TransferValidation.requireText(mediaType, "mediaType");
        sha256 = TransferValidation.requireSha256(sha256, "sha256");
        if (byteCount < 0) {
            throw new IllegalArgumentException("byteCount must not be negative");
        }
    }
}
