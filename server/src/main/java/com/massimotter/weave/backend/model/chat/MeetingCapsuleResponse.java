package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Durable channel Meeting Capsule with fail-closed media controls.")
public record MeetingCapsuleResponse(
        String id,
        String conversationId,
        String contextId,
        String title,
        @Schema(description = "Meeting lifecycle state in Weave vocabulary.", example = "scheduled")
        String state,
        List<String> agendaItems,
        List<String> participants,
        List<String> followUpRefs,
        List<String> disabledControls,
        String disabledReason,
        Instant createdAt,
        @Schema(description = "Whether raw LiveKit/provider setup details are exposed to the client. Always false.", example = "false")
        boolean liveKitProviderDetailsExposed,
        @Schema(description = "Whether Matrix chat E2EE is claimed as media protection. Always false.", example = "false")
        boolean matrixE2eeClaimedForMedia,
        boolean recordingEnabled,
        boolean transcriptionEnabled,
        boolean supportSafe) {
}
