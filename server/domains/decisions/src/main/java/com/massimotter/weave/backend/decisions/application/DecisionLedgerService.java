package com.massimotter.weave.backend.decisions.application;

import com.massimotter.weave.backend.chat.port.ChatConversationAdmission;

import com.massimotter.weave.backend.chat.port.ChatConversationAdmission.AuthorizedContext;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.AuditWriteGate;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.decisions.api.DecisionLedgerCreateRequest;
import com.massimotter.weave.backend.decisions.api.DecisionLedgerEvidencePostureResponse;
import com.massimotter.weave.backend.decisions.api.DecisionLedgerRecordResponse;
import com.massimotter.weave.backend.decisions.api.DecisionLedgerRecordsResponse;
import com.massimotter.weave.backend.decisions.api.DecisionLedgerReferenceRequest;
import com.massimotter.weave.backend.decisions.api.DecisionLedgerReferenceResponse;
import java.time.Instant;
import java.util.ArrayList;
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

public class DecisionLedgerService {
    private static final String DOMAIN = "chat";
    private final ChatConversationAdmission admission;
    private final AuditEventPublisher auditEventPublisher;
    private final ConcurrentMap<ConversationScope, CopyOnWriteArrayList<DecisionLedgerRecordResponse>> decisions = new ConcurrentHashMap<>();

    public DecisionLedgerService(ChatConversationAdmission admission,
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
    public DecisionLedgerRecordsResponse decisions(Jwt jwt, String conversationId) {
        var principal = admission.authorize(jwt, "chat.read", "list_decisions", ContextPermission.VIEW);
        ChannelContextState conversation = requireChannelContext(jwt, conversationId, principal);
        return new DecisionLedgerRecordsResponse(
                conversation.id(),
                conversation.contextId(),
                false,
                new DecisionLedgerEvidencePostureResponse(
                        "Decisions keep Weave-owned provenance through support-safe source refs and actor labels.",
                        List.of(
                                "audit://chat/decision-ledger/" + conversation.id(),
                                "audit://chat/decision-evidence/" + conversation.contextId()),
                        "Export decision records, source refs, and audit refs through the Weave decisions/evidence contract; raw provider secrets stay backend-only.",
                        true),
                List.copyOf(decisionsFor(principal, conversation.id())));
    }

    public DecisionLedgerRecordResponse createDecision(Jwt jwt, String conversationId, DecisionLedgerCreateRequest request) {
        var principal = admission.authorize(jwt, "chat.send", "create_decision", ContextPermission.EDIT);
        ChannelContextState conversation = requireChannelContext(jwt, conversationId, principal);
        List<DecisionLedgerReferenceResponse> references = sanitizeDecisionReferences(request.references());
        if (references.isEmpty()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "chat-validation",
                    "Decision Ledger records require at least one Weave source reference.",
                    Map.of("module", DOMAIN, "field", "references", "diagnosticsRedacted", true));
        }
        Instant timestamp = Instant.now();
        DecisionLedgerRecordResponse decision = new DecisionLedgerRecordResponse(
                "decision-" + UUID.randomUUID(),
                conversation.id(),
                conversation.contextId(),
                sanitizeText(request.title(), "title", 160),
                request.status(),
                principal.principalRef(),
                timestamp,
                references,
                sanitizeTextList(request.risks(), 120),
                sanitizeTextList(request.openQuestions(), 120),
                sanitizeReferenceList(request.followUpRefs()),
                true);
        publishAudit(principal, AuditAction.DECISION_LEDGER_RECORD_CREATED, decision.id(), timestamp, Map.of(
                "command", "create_decision",
                "conversationId", conversation.id(),
                "status", decision.status(),
                "referenceCount", references.size(),
                "supportSafe", true));
        decisionsFor(principal, conversation.id()).add(decision);
        return decision;
    }

    private List<DecisionLedgerReferenceResponse> sanitizeDecisionReferences(List<DecisionLedgerReferenceRequest> references) {
        if (references == null) {
            return List.of();
        }
        List<DecisionLedgerReferenceResponse> sanitized = new ArrayList<>();
        for (DecisionLedgerReferenceRequest reference : references) {
            if (reference == null) {
                continue;
            }
            String type = sanitizeText(reference.type(), "reference.type", 32);
            String ref = sanitizeReference(reference.ref(), "reference.ref");
            sanitized.add(new DecisionLedgerReferenceResponse(
                    type,
                    ref,
                    sanitizeText(reference.label(), "reference.label", 120),
                    sanitizeOptionalText(reference.excerpt(), 280)));
        }
        return List.copyOf(sanitized);
    }

    private CopyOnWriteArrayList<DecisionLedgerRecordResponse> decisionsFor(
            AuthorizedContext principal,
            String conversationId) {
        return decisions.computeIfAbsent(new ConversationScope(
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
