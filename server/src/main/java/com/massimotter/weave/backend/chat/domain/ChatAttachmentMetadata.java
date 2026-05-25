package com.massimotter.weave.backend.chat.domain;

public record ChatAttachmentMetadata(
        String attachmentId,
        String displayName,
        String mediaType,
        long sizeBytes,
        String checksumRef,
        boolean downloadable) {
}
