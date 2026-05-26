package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Approval receipt for future Weaver write/action paths.")
public record WeaverApprovalReceiptResponse(
        String id,
        String actorRef,
        String requestedAction,
        String approvedAction,
        String targetRef,
        Instant timestamp,
        @Schema(description = "Receipt result category.", allowableValues = {"proposed", "approved", "denied", "blocked"})
        String resultCategory) {
}
