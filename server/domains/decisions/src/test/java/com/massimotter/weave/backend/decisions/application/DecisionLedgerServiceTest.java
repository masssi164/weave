package com.massimotter.weave.backend.decisions.application;

import com.massimotter.weave.backend.decisions.api.*;
import com.massimotter.weave.backend.chat.port.ChatConversationAdmission;
import com.massimotter.weave.backend.audit.AuditRequiredException;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionLedgerServiceTest {
    private final Jwt jwt = mock(Jwt.class);
    private final ChatConversationAdmission admission = mock(ChatConversationAdmission.class);

    private void allow() {
        when(admission.authorize(eq(jwt), anyString(), anyString(), any()))
                .thenReturn(new ChatConversationAdmission.AuthorizedContext("organization", "space", "user:member"));
        when(admission.requireConversation(jwt, "conversation")).thenReturn("conversation");
    }

    @Test
    void actualConversationAdmissionPrecedesWriteAndRead() {
        allow();
        var service = new DecisionLedgerService(admission, new InMemoryAuditEventPublisher());
        var created = service.createDecision(jwt, "conversation", new DecisionLedgerCreateRequest("Decision", "accepted", List.of(), List.of(), List.of(), List.of(new DecisionLedgerReferenceRequest("chat-message", "message:one", "Evidence", ""))));
        assertThat(created.conversationId()).isEqualTo("conversation");
        verify(admission).authorize(jwt, "chat.send", "create_decision", ContextPermission.EDIT);
        verify(admission).requireConversation(jwt, "conversation");
    }

    @Test
    void missingAuditLeavesNoMutation() {
        allow();
        var service = new DecisionLedgerService(admission, null);
        assertThatThrownBy(() -> service.createDecision(jwt, "conversation", new DecisionLedgerCreateRequest("Decision", "accepted", List.of(), List.of(), List.of(), List.of(new DecisionLedgerReferenceRequest("chat-message", "message:one", "Evidence", "")))))
                .isInstanceOf(AuditRequiredException.class);
        assertThat(service.decisions(jwt, "conversation").records()).isEmpty();
    }

    @Test
    void deniedCanonicalConversationStopsBeforeMutation() {
        allow();
        doThrow(new IllegalArgumentException("denied")).when(admission).requireConversation(jwt, "conversation");
        var service = new DecisionLedgerService(admission, new InMemoryAuditEventPublisher());
        assertThatThrownBy(() -> service.createDecision(jwt, "conversation", new DecisionLedgerCreateRequest("Decision", "accepted", List.of(), List.of(), List.of(), List.of(new DecisionLedgerReferenceRequest("chat-message", "message:one", "Evidence", "")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalConversationIdsRemainIsolatedByOrganizationAndSpace() {
        allow();
        var service = new DecisionLedgerService(admission, new InMemoryAuditEventPublisher());
        var created = service.createDecision(jwt, "conversation", new DecisionLedgerCreateRequest("Decision", "accepted", List.of(), List.of(), List.of(), List.of(new DecisionLedgerReferenceRequest("chat-message", "message:one", "Evidence", ""))));
        when(admission.authorize(eq(jwt), anyString(), anyString(), any()))
                .thenReturn(new ChatConversationAdmission.AuthorizedContext("other-organization", "space", "user:member"));
        assertThat(service.decisions(jwt, "conversation").records()).isEmpty();
        when(admission.authorize(eq(jwt), anyString(), anyString(), any()))
                .thenReturn(new ChatConversationAdmission.AuthorizedContext("organization", "other-space", "user:member"));
        assertThat(service.decisions(jwt, "conversation").records()).isEmpty();
        allow();
        assertThat(service.decisions(jwt, "conversation").records()).containsExactly(created);
    }
}
