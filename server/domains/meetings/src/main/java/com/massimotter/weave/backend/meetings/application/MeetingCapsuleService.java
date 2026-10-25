package com.massimotter.weave.backend.meetings.application;

import com.massimotter.weave.backend.chat.port.ChatConversationAdmission;

import com.massimotter.weave.backend.chat.port.ChatConversationAdmission.AuthorizedContext;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.AuditWriteGate;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.meetings.api.MeetingCapsuleCreateRequest;
import com.massimotter.weave.backend.meetings.api.MeetingCapsuleResponse;
import com.massimotter.weave.backend.meetings.api.MeetingCapsulesResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public class MeetingCapsuleService {
    private static final String DOMAIN = "chat";
    private final ChatConversationAdmission admission;
    private final AuditEventPublisher auditEventPublisher;
    private final ConcurrentMap<ConversationScope, CopyOnWriteArrayList<MeetingCapsuleResponse>> meetingCapsules = new ConcurrentHashMap<>();

    public MeetingCapsuleService(ChatConversationAdmission admission,
            AuditEventPublisher auditEventPublisher) {
        this.admission = java.util.Objects.requireNonNull(admission);
        this.auditEventPublisher = auditEventPublisher;
    }

    private ChannelContextState requireChannelContext(Jwt jwt, String id,
            AuthorizedContext principal) {
        // Preserve the pre-existing future-feature seed alias; actual Chat lookup remains membership-gated.
        if ("channel-general".equals(id) && "workspace-default".equals(principal.contextId())) {
            return new ChannelContextState(id, principal.contextId());
        }
        return new ChannelContextState(admission.requireConversation(jwt, id), principal.contextId());
    }

    private record ConversationScope(String organizationId, String spaceId, String conversationId) {}

    private record ChannelContextState(String id, String contextId) {}
    public MeetingCapsulesResponse meetingCapsules(Jwt jwt, String conversationId) {
        var principal = admission.authorize(jwt, "chat.read", "list_meeting_capsules", ContextPermission.VIEW);
        ChannelContextState conversation = requireChannelContext(jwt, conversationId, principal);
        return new MeetingCapsulesResponse(
                conversation.id(),
                conversation.contextId(),
                true,
                List.copyOf(meetingCapsulesFor(principal, conversation.id())));
    }

    public MeetingCapsuleResponse createMeetingCapsule(Jwt jwt, String conversationId, MeetingCapsuleCreateRequest request) {
        var principal = admission.authorize(jwt, "chat.send", "create_meeting_capsule", ContextPermission.EDIT);
        ChannelContextState conversation = requireChannelContext(jwt, conversationId, principal);
        List<String> agendaItems = sanitizeTextList(request.agendaItems(), 160);
        if (agendaItems.isEmpty()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "chat-validation",
                    "Meeting Capsules require at least one agenda item.",
                    Map.of("module", DOMAIN, "field", "agendaItems", "diagnosticsRedacted", true));
        }
        Instant timestamp = Instant.now();
        MeetingCapsuleResponse capsule = new MeetingCapsuleResponse(
                "meeting-" + UUID.randomUUID(),
                conversation.id(),
                conversation.contextId(),
                sanitizeText(request.title(), "title", 160),
                "scheduled",
                agendaItems,
                List.of(principal.principalRef() + ":organizer"),
                sanitizeReferenceList(request.followUpRefs()),
                List.of("join", "start"),
                "meeting-backend-capability-unavailable",
                timestamp,
                false,
                false,
                false,
                false,
                true);
        publishAudit(principal, AuditAction.MEETING_CAPSULE_CREATED, capsule.id(), timestamp, Map.of(
                "command", "create_meeting_capsule",
                "conversationId", conversation.id(),
                "failClosed", true,
                "supportSafe", true));
        meetingCapsulesFor(principal, conversation.id()).add(capsule);
        return capsule;
    }

    private CopyOnWriteArrayList<MeetingCapsuleResponse> meetingCapsulesFor(
            AuthorizedContext principal,
            String conversationId) {
        return meetingCapsules.computeIfAbsent(new ConversationScope(
                principal.tenantId(), principal.contextId(), conversationId), ignored -> new CopyOnWriteArrayList<>());
    }

    private List<String> sanitizeTextList(List<String> values, int maxLength) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(this::hasText)
                .map(value -> sanitizeText(value, "value", maxLength))
                .toList();
    }

    private List<String> sanitizeReferenceList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(this::hasText)
                .map(value -> sanitizeReference(value, "reference"))
                .toList();
    }

    private String sanitizeReference(String value, String field) {
        String sanitized = sanitizeText(value, field, 160);
        String normalized = sanitized.toLowerCase(Locale.ROOT);
        if (normalized.contains("://") || containsSecretHint(normalized)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "chat-validation",
                    "Channel work object references must be Weave-safe and must not contain URLs or credentials.",
                    Map.of("module", DOMAIN, "field", field, "diagnosticsRedacted", true));
        }
        return sanitized;
    }

    private String sanitizeText(String value, String field, int maxLength) {
        if (!hasText(value)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "chat-validation",
                    "Required channel work object text is missing.",
                    Map.of("module", DOMAIN, "field", field, "diagnosticsRedacted", true));
        }
        String sanitized = value.replaceAll("\\s+", " ").trim();
        if (sanitized.length() > maxLength || containsSecretHint(sanitized.toLowerCase(Locale.ROOT))) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "chat-validation",
                    "Channel work object text must be support-safe.",
                    Map.of("module", DOMAIN, "field", field, "diagnosticsRedacted", true));
        }
        return sanitized;
    }

    private String sanitizeOptionalText(String value, int maxLength) {
        if (!hasText(value)) {
            return "";
        }
        return sanitizeText(value, "excerpt", maxLength);
    }

    private boolean containsSecretHint(String value) {
        return value.contains("://")
                || value.contains("token")
                || value.contains("secret")
                || value.contains("password")
                || value.contains("apikey")
                || value.contains("api-key")
                || value.contains("cookie");
    }

    private void publishAudit(
            AuthorizedContext principal,
            AuditAction action,
            String subject,
            Instant timestamp,
            Map<String, Object> payload) {
        Map<String, Object> auditPayload = new LinkedHashMap<>(payload);
        auditPayload.put("domain", DOMAIN);
        auditPayload.put("diagnosticsRedacted", true);
        AuditWriteGate.publishRequired(auditEventPublisher, new AuditEvent(
                principal.tenantId(),
                principal.contextId(),
                principal.principalRef(),
                "weave:chat",
                action,
                timestamp,
                action.wireName() + ":" + subject + ":" + timestamp,
                AuditRedactionLevel.SUPPORT_SAFE,
                auditPayload));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
