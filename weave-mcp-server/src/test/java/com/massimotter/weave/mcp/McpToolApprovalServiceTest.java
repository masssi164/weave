package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;

class McpToolApprovalServiceTest {

    private final McpToolApprovalService service = new McpToolApprovalService();

    @Test
    void acceptedElicitationProducesNarrowBackendEvidence() {
        McpSyncRequestContext context = mock(McpSyncRequestContext.class);
        when(context.elicitEnabled()).thenReturn(true);
        when(context.elicit(any(), eq(McpToolApprovalService.ApprovalForm.class)))
                .thenReturn(new StructuredElicitResult<>(
                        McpSchema.ElicitResult.Action.ACCEPT,
                        new McpToolApprovalService.ApprovalForm(true, true),
                        Map.of("openclaw_approval_decision", "allow-always")));

        var evidence = service.requireApproval(
                context,
                "calendar.create_event",
                "Create calendar event",
                "Create one event in calendar:team:engineering.",
                Map.of("calendarRef", "calendar:team:engineering", "title", "Review"));

        assertThat(evidence.protocol()).isEqualTo("mcp-elicitation/v1");
        assertThat(evidence.toolName()).isEqualTo("calendar.create_event");
        assertThat(evidence.scopeRefs()).containsExactly("calendar:team:engineering");
        assertThat(evidence.decision()).isEqualTo("allow-always");
    }

    @Test
    void missingOrDeclinedElicitationFailsClosed() {
        McpSyncRequestContext unavailable = mock(McpSyncRequestContext.class);
        assertThatThrownBy(() -> service.requireApproval(
                        unavailable,
                        "chat.send_message",
                        "Send chat message",
                        "Send one message.",
                        Map.of("threadRef", "thread:general")))
                .isInstanceOf(McpBoundaryException.class)
                .hasMessage("mcp-elicitation-unavailable");

        McpSyncRequestContext declined = mock(McpSyncRequestContext.class);
        when(declined.elicitEnabled()).thenReturn(true);
        when(declined.elicit(any(), eq(McpToolApprovalService.ApprovalForm.class)))
                .thenReturn(new StructuredElicitResult<>(
                        McpSchema.ElicitResult.Action.DECLINE,
                        null,
                        Map.of()));
        assertThatThrownBy(() -> service.requireApproval(
                        declined,
                        "chat.send_message",
                        "Send chat message",
                        "Send one message.",
                        Map.of("threadRef", "thread:general")))
                .isInstanceOf(McpBoundaryException.class)
                .hasMessage("mcp-elicitation-declined");
    }
}
