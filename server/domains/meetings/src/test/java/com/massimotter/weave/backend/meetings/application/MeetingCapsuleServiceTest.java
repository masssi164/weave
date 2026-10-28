package com.massimotter.weave.backend.meetings.application;

import com.massimotter.weave.backend.meetings.api.*;
import com.massimotter.weave.backend.chat.port.ChatConversationAdmission;
import com.massimotter.weave.backend.audit.AuditRequiredException;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingCapsuleServiceTest {
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
        var service = new MeetingCapsuleService(admission, new InMemoryAuditEventPublisher());
        var created = service.createMeetingCapsule(jwt, "conversation", new MeetingCapsuleCreateRequest("Meeting", List.of("Review"), List.of()));
        assertThat(created.conversationId()).isEqualTo("conversation");
        verify(admission).authorize(jwt, "chat.send", "create_meeting_capsule", ContextPermission.EDIT);
        verify(admission).requireConversation(jwt, "conversation");
    }

    @Test
    void missingAuditLeavesNoMutation() {
        allow();
        var service = new MeetingCapsuleService(admission, null);
        assertThatThrownBy(() -> service.createMeetingCapsule(jwt, "conversation", new MeetingCapsuleCreateRequest("Meeting", List.of("Review"), List.of())))
                .isInstanceOf(AuditRequiredException.class);
        assertThat(service.meetingCapsules(jwt, "conversation").capsules()).isEmpty();
    }

    @Test
    void deniedCanonicalConversationStopsBeforeMutation() {
        allow();
        doThrow(new IllegalArgumentException("denied")).when(admission).requireConversation(jwt, "conversation");
        var service = new MeetingCapsuleService(admission, new InMemoryAuditEventPublisher());
        assertThatThrownBy(() -> service.createMeetingCapsule(jwt, "conversation", new MeetingCapsuleCreateRequest("Meeting", List.of("Review"), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalConversationIdsRemainIsolatedByOrganizationAndSpace() {
        allow();
        var service = new MeetingCapsuleService(admission, new InMemoryAuditEventPublisher());
        var created = service.createMeetingCapsule(jwt, "conversation", new MeetingCapsuleCreateRequest("Meeting", List.of("Review"), List.of()));
        when(admission.authorize(eq(jwt), anyString(), anyString(), any()))
                .thenReturn(new ChatConversationAdmission.AuthorizedContext("other-organization", "space", "user:member"));
        assertThat(service.meetingCapsules(jwt, "conversation").capsules()).isEmpty();
        when(admission.authorize(eq(jwt), anyString(), anyString(), any()))
                .thenReturn(new ChatConversationAdmission.AuthorizedContext("organization", "other-space", "user:member"));
        assertThat(service.meetingCapsules(jwt, "conversation").capsules()).isEmpty();
        allow();
        assertThat(service.meetingCapsules(jwt, "conversation").capsules()).containsExactly(created);
    }
}
