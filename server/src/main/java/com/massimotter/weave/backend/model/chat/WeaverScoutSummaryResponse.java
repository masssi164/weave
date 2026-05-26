package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe read-only Weaver scout output.")
public record WeaverScoutSummaryResponse(
        String conversationId,
        String contextId,
        String answer,
        List<WeaverScoutSourceResponse> sources,
        List<WeaverApprovalReceiptResponse> approvalReceipts,
        boolean readOnly,
        boolean proposalOnly,
        boolean backgroundRoomReadingEnabled,
        boolean supportSafe,
        String failureMode) {
}
