package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.AuditWriteGate;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.chat.ChatAttachmentPolicyResponse;
import com.massimotter.weave.backend.model.chat.ChatConversationResponse;
import com.massimotter.weave.backend.model.chat.ChatConversationsResponse;
import com.massimotter.weave.backend.model.chat.ChatHistoryPolicyResponse;
import com.massimotter.weave.backend.model.chat.ChatMembershipResponse;
import com.massimotter.weave.backend.model.chat.ChatMessageResponse;
import com.massimotter.weave.backend.model.chat.ChatMessagesResponse;
import com.massimotter.weave.backend.model.chat.ChatProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.model.chat.ChatProviderReplacementDryRunResponse;
import com.massimotter.weave.backend.model.chat.ChatReadinessResponse;
import com.massimotter.weave.backend.model.chat.ChatSendMessageRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerCreateRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerRecordResponse;
import com.massimotter.weave.backend.model.chat.DecisionLedgerRecordsResponse;
import com.massimotter.weave.backend.model.chat.DecisionLedgerReferenceRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerReferenceResponse;
import com.massimotter.weave.backend.model.chat.MeetingCapsuleCreateRequest;
import com.massimotter.weave.backend.model.chat.MeetingCapsuleResponse;
import com.massimotter.weave.backend.model.chat.MeetingCapsulesResponse;
import com.massimotter.weave.backend.model.chat.WeaverApprovalReceiptResponse;
import com.massimotter.weave.backend.model.chat.WeaverScoutSourceResponse;
import com.massimotter.weave.backend.model.chat.WeaverScoutSummaryRequest;
import com.massimotter.weave.backend.model.chat.WeaverScoutSummaryResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class ChatFacadeService {

    private static final String DEFAULT_CONTEXT_ID = "workspace-default";
    private static final String DOMAIN = "chat";
    private static final String SOURCE = "weave-chat-domain-facade";

    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final AuditEventPublisher auditEventPublisher;
    private final ConcurrentMap<String, ConversationState> conversations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<DecisionLedgerRecordResponse>> decisions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<MeetingCapsuleResponse>> meetingCapsules = new ConcurrentHashMap<>();

    public ChatFacadeService(
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties) {
        this(
                workspaceCapabilityProperties,
                workspaceCapabilityService,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                new InMemoryAuditEventPublisher());
    }

    @Autowired
    public ChatFacadeService(
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            AuditEventPublisher auditEventPublisher) {
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.auditEventPublisher = auditEventPublisher;
        seedConversations();
    }

    public ChatReadinessResponse readiness(Jwt jwt) {
        List<String> granted = grantedChatCapabilities(jwt);
        WorkspaceCapabilityProperties.Capability chat = workspaceCapabilityProperties.chat();
        if (!chat.enabled()) {
            return new ChatReadinessResponse(
                    "disabled",
                    "Chat is disabled by workspace policy.",
                    granted,
                    true);
        }
        if (jwt != null && !granted.contains("chat.read")) {
            return new ChatReadinessResponse(
                    "policy-blocked",
                    "Chat is blocked by your role or group policy. Ask an admin if you need access.",
                    granted,
                    true);
        }
        WorkspaceCapabilityReadiness configured = chat.readiness();
        if (configured == WorkspaceCapabilityReadiness.READY || (configured == null && hasText(chat.dependencyUrl()))) {
            return new ChatReadinessResponse(
                    "usable",
                    "Weave Chat is available through the workspace Chat domain.",
                    granted,
                    true);
        }
        if (configured == WorkspaceCapabilityReadiness.UNAVAILABLE) {
            return new ChatReadinessResponse(
                    "disabled",
                    "Chat is not available in this workspace. Ask an admin to review Workspace Health.",
                    granted,
                    true);
        }
        return new ChatReadinessResponse(
                "degraded",
                "Chat is degraded or missing a ready backend facade. Ask an admin to inspect Workspace Health.",
                granted,
                true);
    }

    public ChatConversationsResponse conversations(Jwt jwt) {
        requireChatReady(jwt, "chat.read", "list_conversations");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.VIEW);
        List<ChatConversationResponse> response = conversations.values().stream()
                .filter(conversation -> conversation.contextId().equals(principal.contextId()))
                .map(conversation -> conversation.toResponse(principal.principalRef()))
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
        return new ChatConversationsResponse(DOMAIN, "canonical-domain-facade", SOURCE, readiness(jwt), response);
    }

    public ChatMessagesResponse messages(Jwt jwt, String conversationId) {
        requireChatReady(jwt, "chat.read", "list_messages");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.VIEW);
        ConversationState conversation = requireConversation(conversationId, principal.contextId());
        return new ChatMessagesResponse(conversation.id(), readiness(jwt), conversation.messagesFor(principal.principalRef()));
    }

    public ChatMessageResponse sendMessage(Jwt jwt, String conversationId, ChatSendMessageRequest request) {
        requireChatReady(jwt, "chat.send", "send_message");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.EDIT);
        ConversationState conversation = requireConversation(conversationId, principal.contextId());
        List<String> attachmentRefs = sanitizeAttachmentRefs(request.attachmentRefs());
        String text = normalizeMessageText(request.text());
        Instant timestamp = Instant.now();
        ChatMessageResponse message = new ChatMessageResponse(
                "msg-" + UUID.randomUUID(),
                conversation.id(),
                principal.principalRef(),
                text,
                attachmentRefs,
                true,
                false,
                timestamp);
        publishAudit(principal, AuditAction.CHAT_MESSAGE_SENT, "message:" + conversation.id(), timestamp, Map.of(
                "command", "send_message",
                "conversationId", conversation.id(),
                "attachmentRefCount", attachmentRefs.size(),
                "supportSafe", true));
        conversation.add(message);
        return message;
    }

    public DecisionLedgerRecordsResponse decisions(Jwt jwt, String conversationId) {
        requireChatReady(jwt, "chat.read", "list_decisions");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.VIEW);
        ConversationState conversation = requireConversation(conversationId, principal.contextId());
        return new DecisionLedgerRecordsResponse(
                conversation.id(),
                conversation.contextId(),
                false,
                List.copyOf(decisionsFor(conversation.id())));
    }

    public DecisionLedgerRecordResponse createDecision(Jwt jwt, String conversationId, DecisionLedgerCreateRequest request) {
        requireChatReady(jwt, "chat.send", "create_decision");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.EDIT);
        ConversationState conversation = requireConversation(conversationId, principal.contextId());
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
        decisionsFor(conversation.id()).add(decision);
        return decision;
    }

    public MeetingCapsulesResponse meetingCapsules(Jwt jwt, String conversationId) {
        requireChatReady(jwt, "chat.read", "list_meeting_capsules");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.VIEW);
        ConversationState conversation = requireConversation(conversationId, principal.contextId());
        return new MeetingCapsulesResponse(
                conversation.id(),
                conversation.contextId(),
                true,
                List.copyOf(meetingCapsulesFor(conversation.id())));
    }

    public MeetingCapsuleResponse createMeetingCapsule(Jwt jwt, String conversationId, MeetingCapsuleCreateRequest request) {
        requireChatReady(jwt, "chat.send", "create_meeting_capsule");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.EDIT);
        ConversationState conversation = requireConversation(conversationId, principal.contextId());
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
        meetingCapsulesFor(conversation.id()).add(capsule);
        return capsule;
    }

    public WeaverScoutSummaryResponse weaverScoutSummary(Jwt jwt, String conversationId, WeaverScoutSummaryRequest request) {
        requireChatReady(jwt, "chat.read", "weaver_scout_summary");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.VIEW);
        ConversationState conversation = requireConversation(conversationId, principal.contextId());
        String question = sanitizeText(request.question(), "question", 240);
        List<WeaverScoutSourceResponse> sources = allowedWeaverSources(conversation, principal.principalRef());
        List<WeaverApprovalReceiptResponse> receipts = approvalReceiptsForScoutRequest(
                principal,
                conversation,
                request.requestedAction());
        Instant timestamp = Instant.now();
        publishAudit(principal, AuditAction.WEAVER_SCOUT_SUMMARY_REQUESTED, "weaver-scout:" + conversation.id(), timestamp, Map.of(
                "command", "weaver_scout_summary",
                "conversationId", conversation.id(),
                "sourceCount", sources.size(),
                "readOnly", true,
                "supportSafe", true));
        String answer = sources.isEmpty()
                ? "I can only summarize explicit allowed channel context, and no readable sources are available yet."
                : "Allowed context for " + conversation.title() + " includes " + sources.size()
                        + " citable source(s). Question: " + question
                        + ". I can summarize and propose next steps, but I cannot mutate the room.";
        return new WeaverScoutSummaryResponse(
                conversation.id(),
                conversation.contextId(),
                answer,
                sources,
                receipts,
                true,
                true,
                false,
                true,
                sources.isEmpty() ? "no-allowed-sources" : "none");
    }

    public ChatProviderReplacementDryRunResponse dryRunProviderReplacement(
            Jwt jwt,
            ChatProviderReplacementDryRunRequest request) {
        requireChatReady(jwt, "chat.read", "provider_replacement_dry_run");
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.ADMIN);
        requireAdminReadinessCapability(jwt);
        String sourceAdapter = sanitizeAdapterKey(request.sourceAdapter());
        String targetAdapter = sanitizeAdapterKey(request.targetAdapter());
        Instant timestamp = Instant.now();
        String dryRunId = "chat-dry-run-" + UUID.randomUUID();
        publishAudit(principal, AuditAction.CHAT_PROVIDER_REPLACEMENT_DRY_RUN, dryRunId, timestamp, Map.of(
                "command", "provider_replacement_dry_run",
                "category", DOMAIN,
                "sourceAdapter", sourceAdapter,
                "targetAdapter", targetAdapter,
                "supportSafe", true));

        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("conversations", request.conversationCount());
        inventory.put("messages", request.messageCount());
        inventory.put("attachments", request.attachmentCount());
        inventory.put("encryptedConversations", request.encryptedRoomCount());
        inventory.put("identityConflicts", request.identityConflictCount());

        List<String> warnings = new ArrayList<>();
        warnings.add("Provider-specific reactions, pins, bot metadata, and thread semantics may require lossy canonical mapping.");
        if (request.attachmentCount() > 0) {
            warnings.add("Attachments must be re-linked through Weave file/attachment facades; raw provider media URLs stay redacted.");
        }
        if (request.encryptedRoomCount() > 0) {
            warnings.add("Encrypted provider history is not backend-readable unless users export or re-share it through an approved migration path.");
        }
        if (request.messageCount() == 0 && request.conversationCount() > 0) {
            warnings.add("Conversation shells can migrate before history import, but members must see the history gap clearly.");
        }

        List<String> conflicts = new ArrayList<>();
        if (request.identityConflictCount() > 0) {
            conflicts.add("Membership identity conflicts require admin resolution against the IDM/RBAC mapping before cutover.");
        }
        if (sourceAdapter.equals(targetAdapter)) {
            conflicts.add("Source and target adapters are identical; no provider replacement should be scheduled.");
        }

        String status = conflicts.isEmpty() ? "dry-run-ready" : "requires-admin-review";
        return new ChatProviderReplacementDryRunResponse(
                dryRunId,
                DOMAIN,
                status,
                sourceAdapter,
                targetAdapter,
                inventory,
                List.of(
                        "Context/Space authorization checked before provider access.",
                        "Capability policy checked before provider access.",
                        "No provider credentials, URLs, usernames, room ids, channel ids, or raw downstream errors included."),
                List.copyOf(warnings),
                List.copyOf(conflicts),
                List.of(
                        "Store canonical conversation/message mapping counts.",
                        "Record cutover timestamp and support-safe conflict summary.",
                        "Keep rollback evidence outside member responses and redact provider identifiers from support bundles."),
                true,
                true);
    }

    private void requireChatReady(Jwt jwt, String capability, String operation) {
        workspaceCapabilityService.requireCapability(jwt, capability, DOMAIN, operation);
        WorkspaceCapabilityProperties.Capability chat = workspaceCapabilityProperties.chat();
        if (!chat.enabled()) {
            throw chatUnavailable("disabled", "Chat is disabled by workspace policy.", operation);
        }
        WorkspaceCapabilityReadiness configured = chat.readiness();
        if (configured == WorkspaceCapabilityReadiness.READY || (configured == null && hasText(chat.dependencyUrl()))) {
            return;
        }
        String impact = configured == WorkspaceCapabilityReadiness.UNAVAILABLE ? "disabled" : "degraded";
        throw chatUnavailable(impact, "Chat is not ready through the Weave Chat facade.", operation);
    }

    private PrincipalContext requireContextPermission(Jwt jwt, ContextPermission permission) {
        PrincipalContext principal = principalContext(jwt);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                principal.tenantId(),
                principal.contextId(),
                principal.principalRef(),
                permission));
        if (!decision.allowed()) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "chat-forbidden",
                    "Chat access is not allowed for this Context/Space.",
                    Map.of(
                            "module", DOMAIN,
                            "contextId", principal.contextId(),
                            "permission", permission.name().toLowerCase(Locale.ROOT),
                            "policyState", "policy-blocked",
                            "reason", decision.reason(),
                            "diagnosticsRedacted", true));
        }
        return principal;
    }

    private PrincipalContext principalContext(Jwt jwt) {
        if (jwt == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Chat access requires an authenticated principal.",
                    Map.of("module", DOMAIN));
        }
        String tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantClaim());
        if (tenantId == null) {
            tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantFallbackClaim());
        }
        if (tenantId == null) {
            tenantId = contextAuthorizationProperties.defaultTenantId();
        }
        String configuredClaim = jwtClaim(jwt, contextAuthorizationProperties.principalClaim());
        String principalRef = contextAuthorizationProperties.principalRef(configuredClaim != null ? configuredClaim : jwt.getSubject());
        if (principalRef == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Chat access requires an authenticated principal.",
                    Map.of("module", DOMAIN, "reason", "principal claim is missing"));
        }
        String contextId = claimOrDefault(jwt, "weave_context_id", "context_id", DEFAULT_CONTEXT_ID);
        return new PrincipalContext(tenantId, contextId, principalRef);
    }

    private String claimOrDefault(Jwt jwt, String primaryClaim, String fallbackClaim, String defaultValue) {
        String primary = jwtClaim(jwt, primaryClaim);
        if (primary != null) {
            return primary;
        }
        String fallback = jwtClaim(jwt, fallbackClaim);
        if (fallback != null) {
            return fallback;
        }
        return defaultValue;
    }

    private String jwtClaim(Jwt jwt, String claimName) {
        if (jwt == null || claimName == null || claimName.isBlank()) {
            return null;
        }
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ConversationState requireConversation(String conversationId, String contextId) {
        ConversationState conversation = conversations.get(conversationId);
        if (conversation == null || !conversation.contextId().equals(contextId)) {
            throw new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "chat-not_found",
                    "Chat conversation was not found in this Context/Space.",
                    Map.of(
                            "module", DOMAIN,
                            "resource", "conversation",
                            "diagnosticsRedacted", true));
        }
        return conversation;
    }

    private ApiErrorException chatUnavailable(String impactState, String message, String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "chat-unavailable",
                message,
                Map.of(
                        "module", DOMAIN,
                        "operation", operation,
                        "impactState", impactState,
                        "diagnosticsRedacted", true));
    }

    private void requireAdminReadinessCapability(Jwt jwt) {
        workspaceCapabilityService.requireCapability(
                jwt,
                "admin_control_plane.readiness_read",
                DOMAIN,
                "provider_replacement_dry_run");
    }

    private List<String> grantedChatCapabilities(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        return workspaceCapabilityService.grantedCapabilities(jwt).stream()
                .filter(capability -> capability.startsWith("chat."))
                .toList();
    }

    private String normalizeMessageText(String text) {
        if (text == null || text.isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "chat-validation",
                    "Chat message text is required.",
                    Map.of("module", DOMAIN, "field", "text"));
        }
        return text.trim();
    }

    private List<String> sanitizeAttachmentRefs(List<String> attachmentRefs) {
        if (attachmentRefs == null) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String ref : attachmentRefs) {
            if (!hasText(ref)) {
                continue;
            }
            String value = ref.trim();
            String normalized = value.toLowerCase(Locale.ROOT);
            if (value.length() > 256
                    || normalized.contains("://")
                    || normalized.contains("token")
                    || normalized.contains("secret")
                    || normalized.contains("password")
                    || normalized.contains("apikey")
                    || normalized.contains("api_key")) {
                throw new ApiErrorException(
                        HttpStatus.BAD_REQUEST,
                        "chat-validation",
                        "Chat attachment references must use Weave-safe references, not raw provider URLs or credentials.",
                        Map.of("module", DOMAIN, "field", "attachmentRefs", "diagnosticsRedacted", true));
            }
            sanitized.add(value);
        }
        return List.copyOf(sanitized);
    }

    private String sanitizeAdapterKey(String adapterKey) {
        if (!hasText(adapterKey)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "chat-validation",
                    "Provider adapter key is required for Chat provider replacement dry-run.",
                    Map.of("module", DOMAIN, "field", "adapterKey"));
        }
        String value = adapterKey.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9-]{1,63}") || containsSecretHint(value)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "chat-validation",
                    "Provider adapter key must be support-safe and must not contain URLs, credentials, or provider object identifiers.",
                    Map.of("module", DOMAIN, "field", "adapterKey", "diagnosticsRedacted", true));
        }
        return value;
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

    private CopyOnWriteArrayList<DecisionLedgerRecordResponse> decisionsFor(String conversationId) {
        return decisions.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>());
    }

    private CopyOnWriteArrayList<MeetingCapsuleResponse> meetingCapsulesFor(String conversationId) {
        return meetingCapsules.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>());
    }

    private List<WeaverScoutSourceResponse> allowedWeaverSources(ConversationState conversation, String principalRef) {
        List<WeaverScoutSourceResponse> sources = new ArrayList<>();
        conversation.messagesFor(principalRef).stream()
                .filter(message -> hasText(message.text()))
                .limit(3)
                .map(message -> new WeaverScoutSourceResponse(
                        "message",
                        "message:" + message.id(),
                        "Message in " + conversation.title(),
                        truncate(message.text(), 160)))
                .forEach(sources::add);
        decisionsFor(conversation.id()).stream()
                .limit(3)
                .map(decision -> new WeaverScoutSourceResponse(
                        "decision",
                        "decision:" + decision.id(),
                        decision.title(),
                        decision.status()))
                .forEach(sources::add);
        meetingCapsulesFor(conversation.id()).stream()
                .limit(3)
                .map(capsule -> new WeaverScoutSourceResponse(
                        "meeting",
                        "meeting:" + capsule.id(),
                        capsule.title(),
                        String.join("; ", capsule.agendaItems())))
                .forEach(sources::add);
        return List.copyOf(sources);
    }

    private List<WeaverApprovalReceiptResponse> approvalReceiptsForScoutRequest(
            PrincipalContext principal,
            ConversationState conversation,
            String requestedAction) {
        if (!hasText(requestedAction)) {
            return List.of();
        }
        Instant timestamp = Instant.now();
        return List.of(new WeaverApprovalReceiptResponse(
                "receipt-" + UUID.randomUUID(),
                principal.principalRef(),
                sanitizeText(requestedAction, "requestedAction", 160),
                "none - Sprint 4 Weaver scout is read-only",
                "conversation:" + conversation.id(),
                timestamp,
                "blocked"));
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
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
            PrincipalContext principal,
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

    private void seedConversations() {
        if (!conversations.isEmpty()) {
            return;
        }
        Instant seedTime = Instant.parse("2026-05-25T10:00:00Z");
        ConversationState general = new ConversationState(
                "channel-general",
                DEFAULT_CONTEXT_ID,
                "channel",
                "General workspace channel",
                new ChatHistoryPolicyResponse(
                        "workspace-default-history",
                        "joined-members",
                        true,
                        true),
                new ChatAttachmentPolicyResponse(true, 8, false));
        general.add(new ChatMessageResponse(
                "msg-seed-welcome",
                general.id(),
                "user:system",
                "Welcome to Weave Chat. Provider details stay behind the backend facade.",
                List.of(),
                false,
                false,
                seedTime));
        conversations.put(general.id(), general);
    }

    private record PrincipalContext(String tenantId, String contextId, String principalRef) {
    }

    private static final class ConversationState {
        private final String id;
        private final String contextId;
        private final String kind;
        private final String title;
        private final ChatHistoryPolicyResponse historyPolicy;
        private final ChatAttachmentPolicyResponse attachmentPolicy;
        private final CopyOnWriteArrayList<ChatMessageResponse> messages = new CopyOnWriteArrayList<>();

        private ConversationState(
                String id,
                String contextId,
                String kind,
                String title,
                ChatHistoryPolicyResponse historyPolicy,
                ChatAttachmentPolicyResponse attachmentPolicy) {
            this.id = id;
            this.contextId = contextId;
            this.kind = kind;
            this.title = title;
            this.historyPolicy = historyPolicy;
            this.attachmentPolicy = attachmentPolicy;
        }

        String id() {
            return id;
        }

        String contextId() {
            return contextId;
        }

        String title() {
            return title;
        }

        void add(ChatMessageResponse message) {
            messages.add(message);
        }

        List<ChatMessageResponse> messagesFor(String principalRef) {
            return messages.stream()
                    .map(message -> new ChatMessageResponse(
                            message.id(),
                            message.conversationId(),
                            message.senderRef(),
                            message.text(),
                            message.attachmentRefs(),
                            message.senderRef().equals(principalRef),
                            message.encryptedProviderContentRedacted(),
                            message.sentAt()))
                    .toList();
        }

        ChatConversationResponse toResponse(String principalRef) {
            Instant lastMessageAt = messages.stream()
                    .map(ChatMessageResponse::sentAt)
                    .max(Instant::compareTo)
                    .orElse(null);
            return new ChatConversationResponse(
                    id,
                    contextId,
                    kind,
                    title,
                    new ChatMembershipResponse(principalRef, "joined", "member"),
                    historyPolicy,
                    attachmentPolicy,
                    lastMessageAt);
        }
    }
}
