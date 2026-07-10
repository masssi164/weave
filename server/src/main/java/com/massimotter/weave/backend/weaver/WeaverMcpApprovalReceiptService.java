package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
import com.massimotter.weave.contract.mcp.MemberMcpToolDefinition;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalEvidence;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WeaverMcpApprovalReceiptService {

    private static final Duration EVIDENCE_TTL = Duration.ofMinutes(2);
    private static final Duration RECEIPT_TTL = Duration.ofSeconds(30);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;
    private final ConcurrentMap<String, Instant> consumedEvidenceRefs = new ConcurrentHashMap<>();

    @Autowired
    public WeaverMcpApprovalReceiptService(AuditEventPublisher auditEventPublisher) {
        this(auditEventPublisher, Clock.systemUTC());
    }

    WeaverMcpApprovalReceiptService(AuditEventPublisher auditEventPublisher, Clock clock) {
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public Resolution issue(
            ApprovalEvidence evidence,
            String actorRef,
            String runtimeProfileHash,
            MemberMcpToolDefinition definition,
            Map<String, Object> arguments,
            boolean trustedMcpBoundary) {
        List<String> requiredScopes = canonicalScopeRefs(arguments);
        String auditRef = "audit://weaver-approval/" + UUID.randomUUID();
        if (!trustedMcpBoundary) {
            return denied("mcp_boundary_untrusted", auditRef, actorRef, runtimeProfileHash, definition, requiredScopes);
        }
        if (evidence == null) {
            return denied("mcp_elicitation_missing", auditRef, actorRef, runtimeProfileHash, definition, requiredScopes);
        }
        if (!"mcp-elicitation/v1".equals(evidence.protocol())
                || !evidence.evidenceRef().startsWith("elicitation://openclaw/")
                || !List.of("allow-once", "allow-always").contains(evidence.decision())) {
            return denied("mcp_elicitation_untrusted", auditRef, actorRef, runtimeProfileHash, definition, requiredScopes);
        }
        if (!evidence.toolName().equals(definition.name()) || !evidence.scopeRefs().equals(requiredScopes)) {
            return denied("mcp_elicitation_binding_mismatch", auditRef, actorRef, runtimeProfileHash, definition, requiredScopes);
        }
        Instant now = clock.instant();
        if (!decisionTimeValid(evidence.decidedAt(), now)) {
            return denied("mcp_elicitation_expired", auditRef, actorRef, runtimeProfileHash, definition, requiredScopes);
        }
        discardExpiredEvidence(now);
        if (consumedEvidenceRefs.putIfAbsent(evidence.evidenceRef(), now.plus(EVIDENCE_TTL)) != null) {
            return denied("mcp_elicitation_replayed", auditRef, actorRef, runtimeProfileHash, definition, requiredScopes);
        }
        WeaverApprovalReceipt receipt = new WeaverApprovalReceipt(
                "approval://weaver/" + UUID.randomUUID(),
                actorRef,
                runtimeProfileHash,
                definition.domain(),
                definition.name(),
                requiredScopes,
                WeaverApprovalReceipt.argumentDigest(arguments),
                MemberMcpDomainDefinition.CONTRACT_VERSION,
                WeaverToolRegistry.APPROVAL_POLICY_VERSION,
                "approved",
                evidence.decision(),
                evidence.evidenceRef(),
                now.toString(),
                now.plus(RECEIPT_TTL).toString(),
                auditRef);
        audit("mcp_elicitation_receipt_issued", auditRef, actorRef, runtimeProfileHash, definition, requiredScopes);
        return new Resolution("approved", receipt, auditRef);
    }

    public static List<String> canonicalScopeRefs(Map<String, Object> arguments) {
        List<String> refs = new ArrayList<>();
        for (String key : List.of(
                "spaceRef",
                "channelRef",
                "threadRef",
                "decisionRef",
                "boardTaskRef",
                "taskRef",
                "calendarRef",
                "eventRef",
                "messageRef",
                "fileRef")) {
            Object value = arguments == null ? null : arguments.get(key);
            if (value instanceof String ref && canonicalRef(ref)) {
                refs.add(ref);
            }
        }
        return refs.stream().distinct().sorted().toList();
    }

    private static boolean canonicalRef(String ref) {
        return ref.startsWith("space:")
                || ref.startsWith("channel:")
                || ref.startsWith("thread:")
                || ref.startsWith("decision:")
                || ref.startsWith("board-task:")
                || ref.startsWith("task:")
                || ref.startsWith("calendar:")
                || ref.startsWith("event:")
                || ref.startsWith("message:")
                || ref.startsWith("file:");
    }

    private boolean decisionTimeValid(String decidedAt, Instant now) {
        try {
            Instant decisionTime = Instant.parse(decidedAt);
            return !decisionTime.isBefore(now.minus(EVIDENCE_TTL))
                    && !decisionTime.isAfter(now.plus(CLOCK_SKEW));
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private void discardExpiredEvidence(Instant now) {
        consumedEvidenceRefs.forEach((evidenceRef, expiresAt) -> {
            if (!expiresAt.isAfter(now)) {
                consumedEvidenceRefs.remove(evidenceRef, expiresAt);
            }
        });
    }

    private Resolution denied(
            String status,
            String auditRef,
            String actorRef,
            String runtimeProfileHash,
            MemberMcpToolDefinition definition,
            List<String> scopes) {
        audit(status, auditRef, actorRef, runtimeProfileHash, definition, scopes);
        return new Resolution(status, null, auditRef);
    }

    private void audit(
            String status,
            String auditRef,
            String actorRef,
            String runtimeProfileHash,
            MemberMcpToolDefinition definition,
            List<String> scopes) {
        auditEventPublisher.publish(new AuditEvent(
                "tenant:workspace",
                null,
                actorRef,
                "weaver-mcp-elicitation",
                AuditAction.WEAVER_TOOL_INVOCATION_RECORDED,
                clock.instant(),
                auditRef,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "action", definition.name(),
                        "domain", definition.domain(),
                        "runtimeProfileHash", runtimeProfileHash,
                        "canonicalRefs", scopes,
                        "auditRef", auditRef,
                        "status", status,
                        "supportSafe", true)));
    }

    public record Resolution(String status, WeaverApprovalReceipt receipt, String auditRef) {
        public boolean approved() {
            return receipt != null && "approved".equals(status);
        }
    }
}
