package com.massimotter.weave.backend.chat;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightReport;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightRequest;
import com.massimotter.weave.backend.chat.domain.ChatProviderMappingRecord;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.chat.domain.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatReadReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatTypingIndicator;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.provider.ProviderCapabilityContracts;
import com.massimotter.weave.backend.provider.ProviderCategoryCatalog;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class ChatDomainFacadeService {

    public static final String CONTRACT_VERSION = "chat-domain-facade-v1";

    private final ProviderRegistry providerRegistry;
    private final ProviderSelectionRepository providerSelectionRepository;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final AuditEventPublisher auditEventPublisher;
    private final ChatProviderPort chatProviderPort;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final Clock clock;

    @Autowired
    public ChatDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher,
            ChatProviderPort chatProviderPort,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties) {
        this(
                providerRegistry,
                providerSelectionRepository,
                workspaceCapabilityService,
                auditEventPublisher,
                chatProviderPort,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                Clock.systemUTC());
    }

    ChatDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher,
            ChatProviderPort chatProviderPort,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            Clock clock) {
        this.providerRegistry = providerRegistry;
        this.providerSelectionRepository = providerSelectionRepository;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.auditEventPublisher = auditEventPublisher;
        this.chatProviderPort = chatProviderPort;
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.clock = clock;
    }

    public ChatReadiness memberReadiness(Jwt jwt) {
        return readiness(jwt, false);
    }

    public ChatReadiness adminReadiness(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "chat", "admin-readiness");
        return readiness(jwt, true);
    }

    public ChatConversations conversations(Jwt jwt) {
        requireRead(jwt, "list-conversations");
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            return new ChatConversations(readiness, List.of());
        }
        ChatConversations conversations = chatProviderPort.joinedConversations(requestContext(jwt));
        return new ChatConversations(readiness, conversations.conversations());
    }

    public ChatMessages messages(String conversationId, Jwt jwt) {
        requireRead(jwt, "read-messages");
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            return new ChatMessages(readiness, safeIdentifier(conversationId, "conversation-unavailable"), List.of());
        }
        ChatMessages messages = chatProviderPort.timeline(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-empty")),
                null,
                100);
        return new ChatMessages(readiness, messages.conversationId(), messages.messages());
    }

    public com.massimotter.weave.backend.chat.domain.ChatConversation conversation(
            String conversationId,
            Jwt jwt) {
        requireRead(jwt, "read-conversation");
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            throw new IllegalStateException("Chat is not ready.");
        }
        return chatProviderPort.conversation(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")));
    }

    public String syncCursor(Jwt jwt) {
        requireRead(jwt, "read-sync-cursor");
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            return "chat-unavailable";
        }
        return chatProviderPort.currentCursor(requestContext(jwt)).value();
    }

    public ChatMessage sendMessage(
            String conversationId,
            String transactionId,
            String body,
            Jwt jwt) {
        requireWrite(jwt, "send-message");
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            throw new IllegalStateException("Chat is not ready for message delivery.");
        }
        ChatMessage message = chatProviderPort.send(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")),
                new ChatTransactionId(opaqueTransactionId(transactionId)),
                body);
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                requestContext(jwt).contextId(),
                actorRef(jwt),
                "matrix-client-server-facade",
                AuditAction.CHAT_MESSAGE_SENT,
                message.sentAt(),
                "chat-message:" + message.messageId(),
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "domain", "chat",
                        "conversationId", message.conversationId(),
                        "messageId", message.messageId(),
                        "transactionIdHash", sha256(transactionId),
                        "providerPayloadExposed", false)));
        return message;
    }

    public ChatTimeline timeline(String conversationId, Jwt jwt, int limit) {
        requireRead(jwt, "read-timeline");
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            return new ChatTimeline(safeIdentifier(conversationId, "conversation-unavailable"), List.of());
        }
        return chatProviderPort.timelineEvents(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-empty")),
                null,
                limit);
    }

    public ChatTimelineEvent sendEvent(
            String conversationId,
            String transactionId,
            ChatEventContent content,
            Jwt jwt) {
        requireWrite(jwt, "send-event");
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            throw new IllegalStateException("Chat is not ready for event delivery.");
        }
        ChatTimelineEvent event = chatProviderPort.sendEvent(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")),
                new ChatTransactionId(opaqueTransactionId(transactionId)),
                content);
        auditTimelineMutation(jwt, event, transactionId);
        return event;
    }

    public ChatRedactionReceipt redactEvent(
            String conversationId,
            String eventId,
            String transactionId,
            Jwt jwt) {
        requireWrite(jwt, "redact-event");
        requireReady(jwt);
        ChatRedactionReceipt receipt = chatProviderPort.redactEvent(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")),
                new ChatTransactionId(opaqueTransactionId(transactionId)),
                safeIdentifier(eventId, "event-required"));
        auditRedactionMutation(jwt, receipt, transactionId);
        return receipt;
    }

    public com.massimotter.weave.backend.chat.domain.ChatConversation createConversation(
            String transactionId,
            String title,
            String kind,
            List<ChatResolvedIdentity> invitedIdentities,
            Jwt jwt) {
        return createConversation(
                transactionId,
                title,
                kind,
                invitedIdentities,
                ChatEncryptionState.unencrypted(),
                jwt);
    }

    public com.massimotter.weave.backend.chat.domain.ChatConversation createConversation(
            String transactionId,
            String title,
            String kind,
            List<ChatResolvedIdentity> invitedIdentities,
            ChatEncryptionState initialEncryption,
            Jwt jwt) {
        requireWrite(jwt, "create-conversation");
        requireReady(jwt);
        ChatRequestContext context = requestContext(jwt);
        requireInviteEligibility(context, invitedIdentities);
        return chatProviderPort.createConversation(
                context,
                new ChatTransactionId(opaqueTransactionId(transactionId)),
                title,
                kind,
                invitedIdentities,
                initialEncryption);
    }

    public com.massimotter.weave.backend.chat.domain.ChatConversation joinConversation(
            String conversationId,
            Jwt jwt) {
        requireWrite(jwt, "join-conversation");
        requireReady(jwt);
        return chatProviderPort.joinConversation(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")));
    }

    public com.massimotter.weave.backend.chat.domain.ChatConversation leaveConversation(
            String conversationId,
            Jwt jwt) {
        requireWrite(jwt, "leave-conversation");
        requireReady(jwt);
        return chatProviderPort.leaveConversation(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")));
    }

    public com.massimotter.weave.backend.chat.domain.ChatConversation enableEncryption(
            String conversationId,
            String algorithm,
            Jwt jwt) {
        requireWrite(jwt, "enable-encryption");
        requireReady(jwt);
        var conversation = chatProviderPort.enableEncryption(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")),
                algorithm);
        ChatRequestContext context = requestContext(jwt);
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                context.contextId(),
                actorRef(jwt),
                "matrix-client-server-facade",
                AuditAction.CHAT_ENCRYPTION_ENABLED,
                conversation.updatedAt(),
                "chat-encryption:" + conversation.conversationId(),
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "domain", "chat",
                        "conversationId", conversation.conversationId(),
                        "algorithm", ChatEncryptedEnvelope.MEGOLM_V1,
                        "serverMayReadContent", false,
                        "providerPayloadExposed", false)));
        return conversation;
    }

    public ChatReadReceipt markRead(String conversationId, String eventId, Jwt jwt) {
        requireRead(jwt, "mark-read");
        requireReady(jwt);
        return chatProviderPort.markRead(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")),
                safeIdentifier(eventId, "event-required"));
    }

    public ChatTypingIndicator setTyping(
            String conversationId,
            boolean typing,
            int timeoutMilliseconds,
            Jwt jwt) {
        requireWrite(jwt, "set-typing");
        requireReady(jwt);
        return chatProviderPort.setTyping(
                requestContext(jwt),
                new ConversationId(safeIdentifier(conversationId, "conversation-unavailable")),
                typing,
                timeoutMilliseconds);
    }

    private void auditTimelineMutation(
            Jwt jwt,
            ChatTimelineEvent event,
            String transactionId) {
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                requestContext(jwt).contextId(),
                actorRef(jwt),
                "matrix-client-server-facade",
                AuditAction.CHAT_MESSAGE_SENT,
                event.occurredAt(),
                "chat-event:" + event.eventId(),
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "domain", "chat",
                        "operation", "event-sent",
                        "eventKind", event.content().kind().value(),
                        "conversationId", event.conversationId(),
                        "eventId", event.eventId(),
                        "transactionIdHash", sha256(transactionId),
                        "providerPayloadExposed", false)));
    }

    private void auditRedactionMutation(
            Jwt jwt,
            ChatRedactionReceipt receipt,
            String transactionId) {
        String idempotencyKey = "chat-redaction:" + sha256(requestContext(jwt).tenantId() + "\u0000"
                + requestContext(jwt).identityIssuer() + "\u0000" + actorRef(jwt) + "\u0000"
                + receipt.conversationId() + "\u0000" + receipt.targetEventId() + "\u0000" + transactionId);
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                requestContext(jwt).contextId(),
                actorRef(jwt),
                "matrix-client-server-facade",
                AuditAction.CHAT_EVENT_REDACTED,
                receipt.occurredAt(),
                idempotencyKey,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "domain", "chat",
                        "operation", "event-redacted",
                        "eventKind", "redaction",
                        "conversationId", receipt.conversationId(),
                        "eventId", receipt.redactionEventId(),
                        "targetEventId", receipt.targetEventId(),
                        "transactionIdHash", sha256(transactionId),
                        "providerPayloadExposed", false)));
    }

    public ChatMigrationPreflightReport preflight(ChatMigrationPreflightRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "chat", "migration-preflight");
        ChatMigrationPreflightRequest safeRequest = request == null
                ? new ChatMigrationPreflightRequest(null, null, true, Map.of(), List.of(), List.of(), null)
                : request;
        ChatReadiness readiness = adminReadiness(jwt);
        String sourceProvider = safeProviderKey(safeRequest.sourceProviderKey(), "selected-chat-provider");
        String targetProvider = safeProviderKey(safeRequest.targetProviderKey(), "target-provider-required");
        boolean supportedTarget = ProviderCapabilityContracts.providerCandidates("chat").stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(targetProvider.toLowerCase(Locale.ROOT)));
        Map<String, Integer> objectCounts = safeCounts(safeRequest.inventoryCounts());
        List<String> lossy = safeList(safeRequest.expectedLossyFields());
        List<String> conflicts = safeList(safeRequest.conflictHints());
        List<String> blocked = new java.util.ArrayList<>();
        if (!safeRequest.dryRun()) {
            blocked.add("destructive_apply_not_available_in_chat_domain_facade_v1");
        }
        if (readiness.memberState() == ChatMemberState.MISCONFIGURED || readiness.memberState() == ChatMemberState.UNAVAILABLE) {
            blocked.add("selected_chat_mapping_not_ready");
        }
        if (!supportedTarget) {
            blocked.add("target_provider_not_registered_for_chat_category");
        }
        String preflightId = "chat-preflight-" + Math.abs((sourceProvider + ":" + targetProvider + ":" + objectCounts).hashCode());
        String auditEventId = preflightId + "-" + Instant.now(clock).toEpochMilli();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("category", "chat");
        auditPayload.put("sourceProviderKey", sourceProvider);
        auditPayload.put("targetProviderKey", targetProvider);
        auditPayload.put("mode", "dry-run");
        auditPayload.put("dryRunRequested", safeRequest.dryRun());
        auditPayload.put("destructiveApplyAvailable", false);
        auditPayload.put("objectCounts", objectCounts);
        auditPayload.put("conflictCategoryCount", conflicts.size());
        auditPayload.put("lossyFieldWarningCount", lossy.size());
        auditPayload.put("blockedOperationCount", blocked.size());
        auditPayload.put("providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE);
        auditPayload.put("reason", safeText(safeRequest.reason()));
        auditPayload.put("secretsReturned", false);
        auditPayload.put("downstreamErrorsReturned", false);
        auditPayload.put("credentialMaterialStored", false);
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "chat-domain-facade",
                actorRef(jwt),
                "chat-migration-preflight",
                AuditAction.CHAT_MIGRATION_PREFLIGHTED,
                Instant.now(clock),
                auditEventId,
                AuditRedactionLevel.SECRET_REDACTED,
                auditPayload));
        return new ChatMigrationPreflightReport(
                preflightId,
                "dry-run",
                sourceProvider,
                targetProvider,
                blocked.isEmpty() ? ChatMemberState.READY : ChatMemberState.DEGRADED,
                false,
                true,
                auditEventId,
                objectCounts,
                conflicts.isEmpty() ? List.of("membership_identity_mapping", "history_policy_alignment") : conflicts,
                lossy.isEmpty() ? List.of("Provider-specific reactions, bot metadata, or thread affordances may require Weave annotations.") : lossy,
                List.copyOf(blocked),
                List.of(
                        "Dry-run only: no provider data is mutated.",
                        "Counts are category-level and omit downstream payloads.",
                        "Warnings are safe for admin/operator support review."),
                Instant.now(clock));
    }

    private ChatReadiness readiness(Jwt jwt, boolean includeAdminDiagnostics) {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot(jwt);
        var chatCapability = capabilities.chat();
        ProviderRegistryResponse registry = providerRegistry.status();
        Optional<ProviderSelection> maybeSelection = providerSelectionRepository.findByCategory("chat");
        Optional<ProviderStatusResponse> maybeProvider = maybeSelection
                .flatMap(selection -> registry.providers().stream()
                        .filter(provider -> ProviderCategoryCatalog.providerMatchesCategory(provider, "chat"))
                        .filter(provider -> providerKeyMatches(provider, selection.providerKey()))
                        .findFirst());

        ChatMemberState state = mapState(chatCapability.policyState(), chatCapability.enabled(), maybeSelection, maybeProvider);
        if (state == ChatMemberState.READY && !chatProviderPort.configured()) {
            state = ChatMemberState.MISCONFIGURED;
        } else if (state == ChatMemberState.READY && maybeSelection
                .map(ProviderSelection::providerKey)
                .filter(this::selectedAdapterMatches)
                .isEmpty()) {
            state = ChatMemberState.MISCONFIGURED;
        } else if (state == ChatMemberState.READY && !chatProviderPort.readiness().available()) {
            String code = chatProviderPort.readiness().supportSafeCode();
            state = code.contains("unavailable") || code.contains("interrupted")
                    ? ChatMemberState.UNAVAILABLE
                    : ChatMemberState.DEGRADED;
        }
        String impact = memberImpact(state, chatCapability.memberImpact());
        ChatProviderMappingRecord mapping = includeAdminDiagnostics
                ? mapping(maybeSelection, maybeProvider, state)
                : null;
        Map<String, Object> diagnostics = includeAdminDiagnostics
                ? adminDiagnostics(maybeSelection, maybeProvider, state)
                : Map.of(
                        "domain", "chat",
                        "state", state.value(),
                        "diagnosticsExposed", false,
                        "downstreamErrorsReturned", false,
                        "secretsReturned", false);
        return new ChatReadiness(
                CONTRACT_VERSION,
                "chat",
                state,
                impact,
                state != ChatMemberState.READY,
                true,
                false,
                false,
                maybeSelection.map(ProviderSelection::migrationDryRunRequired).orElse(false),
                mapping,
                defaultHistoryPolicy(),
                diagnostics,
                Instant.now(clock));
    }

    private ChatMemberState mapState(
            WorkspaceCapabilityPolicyState policyState,
            boolean capabilityEnabled,
            Optional<ProviderSelection> maybeSelection,
            Optional<ProviderStatusResponse> maybeProvider) {
        if (policyState == WorkspaceCapabilityPolicyState.POLICY_BLOCKED) {
            return ChatMemberState.POLICY_BLOCKED;
        }
        if (policyState == WorkspaceCapabilityPolicyState.DISABLED || !capabilityEnabled) {
            return ChatMemberState.POLICY_BLOCKED;
        }
        if (maybeSelection.isEmpty()) {
            return ChatMemberState.MISCONFIGURED;
        }
        if (maybeProvider.isEmpty()) {
            return ChatMemberState.UNAVAILABLE;
        }
        ProviderStatusResponse provider = maybeProvider.get();
        if (!provider.enabled() || provider.state() == ProviderState.DISABLED) {
            return ChatMemberState.POLICY_BLOCKED;
        }
        if (!provider.configured() || provider.state() == ProviderState.NOT_CONFIGURED) {
            return ChatMemberState.MISCONFIGURED;
        }
        if (provider.state() == ProviderState.DEGRADED) {
            return ChatMemberState.DEGRADED;
        }
        if (provider.state() == ProviderState.READY || provider.state() == ProviderState.CONFIGURED) {
            return ChatMemberState.READY;
        }
        return ChatMemberState.UNAVAILABLE;
    }

    private ChatProviderMappingRecord mapping(
            Optional<ProviderSelection> maybeSelection,
            Optional<ProviderStatusResponse> maybeProvider,
            ChatMemberState state) {
        String selectedProvider = maybeSelection.map(ProviderSelection::providerKey).orElse("awaiting_admin_selection");
        List<String> lossy = maybeSelection.map(ProviderSelection::lossyMappingNotes).orElse(List.of());
        Map<String, Object> diagnostics = adminDiagnostics(maybeSelection, maybeProvider, state);
        return new ChatProviderMappingRecord(
                "chat",
                selectedProvider,
                ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                maybeSelection.isPresent(),
                maybeProvider.map(ProviderStatusResponse::configured).orElse(false),
                state,
                state != ChatMemberState.READY,
                true,
                false,
                false,
                lossy,
                diagnostics);
    }

    private Map<String, Object> adminDiagnostics(
            Optional<ProviderSelection> maybeSelection,
            Optional<ProviderStatusResponse> maybeProvider,
            ChatMemberState state) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("category", "chat");
        diagnostics.put("providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE);
        diagnostics.put("selectedByAdmin", maybeSelection.isPresent());
        diagnostics.put("state", state.value());
        diagnostics.put("missingConfigurationCategory", missingConfigurationCategory(maybeSelection, maybeProvider, state));
        diagnostics.put("policyState", state == ChatMemberState.POLICY_BLOCKED ? "policy_blocked" : "allowed_or_unavailable");
        diagnostics.put("configured", maybeProvider.map(ProviderStatusResponse::configured).orElse(false));
        diagnostics.put("supportedCapabilities", maybeProvider.map(provider -> List.copyOf(provider.supportedCapabilities())).orElse(List.of()));
        diagnostics.put("unsupportedOperations", maybeProvider.map(provider -> List.copyOf(provider.unsupportedOperations())).orElse(List.of()));
        diagnostics.put("currentRealProviderPath", "matrix-chat");
        diagnostics.put("currentRealProviderAliases", List.of("synapse-homeserver"));
        diagnostics.put("contractOnlyChatProviders", List.of("microsoft-teams", "slack", "nextcloud-talk"));
        diagnostics.put("canonicalAdapterKey", chatProviderPort.conformanceProfile().adapterKey());
        diagnostics.put("canonicalAdapterReadiness", chatProviderPort.readiness().supportSafeCode());
        diagnostics.put("canonicalSupportedOperations", chatProviderPort.conformanceProfile().supportedOperations().stream().sorted().toList());
        diagnostics.put("secretsReturned", false);
        diagnostics.put("downstreamErrorsReturned", false);
        diagnostics.put("diagnosticsRedacted", true);
        return diagnostics;
    }

    private String missingConfigurationCategory(
            Optional<ProviderSelection> maybeSelection,
            Optional<ProviderStatusResponse> maybeProvider,
            ChatMemberState state) {
        if (state == ChatMemberState.POLICY_BLOCKED) {
            return "policy";
        }
        if (maybeSelection.isEmpty()) {
            return "admin_provider_selection";
        }
        if (maybeProvider.isEmpty()) {
            return "unsupported_provider_mapping";
        }
        if (!maybeProvider.get().configured()) {
            return "backend_provider_configuration";
        }
        return "none";
    }

    private boolean providerKeyMatches(ProviderStatusResponse provider, String providerKey) {
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return provider.providerKey().equals(providerKey)
                || provider.candidates().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
    }

    private boolean selectedAdapterMatches(String selectedProviderKey) {
        return chatProviderPort.providerSelectionKeys().stream()
                .anyMatch(key -> key.equalsIgnoreCase(selectedProviderKey));
    }

    private void requireRead(Jwt jwt, String operation) {
        workspaceCapabilityService.requireCapability(jwt, "chat.read", "chat", operation);
        requireContextPermission(jwt, ContextPermission.VIEW);
    }

    private void requireWrite(Jwt jwt, String operation) {
        workspaceCapabilityService.requireCapability(jwt, "chat.send", "chat", operation);
        requireContextPermission(jwt, ContextPermission.EDIT);
    }

    private void requireContextPermission(Jwt jwt, ContextPermission permission) {
        ChatRequestContext context = requestContext(jwt);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                context.tenantId(), context.contextId(), context.authorizationPrincipalRef(), permission));
        if (!decision.allowed()) {
            throw new ChatAccessDeniedException();
        }
    }

    private void requireInviteEligibility(
            ChatRequestContext context,
            List<ChatResolvedIdentity> invitedIdentities) {
        for (ChatResolvedIdentity identity : invitedIdentities == null
                ? List.<ChatResolvedIdentity>of()
                : invitedIdentities) {
            if (!context.tenantId().equals(identity.tenantId())) {
                throw new ChatAccessDeniedException();
            }
            var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                    context.tenantId(),
                    context.contextId(),
                    identity.authorizationPrincipalRef(),
                    ContextPermission.VIEW));
            if (!decision.allowed()) {
                throw new ChatAccessDeniedException();
            }
        }
    }

    private void requireReady(Jwt jwt) {
        if (memberReadiness(jwt).memberState() != ChatMemberState.READY) {
            throw new IllegalStateException("Chat is not ready.");
        }
    }

    private ChatHistoryPolicy defaultHistoryPolicy() {
        return new ChatHistoryPolicy(
                "conversation_members",
                "organization_default_retention",
                false,
                true,
                List.of("History policy is a Weave Chat concept; provider retention details stay admin/operator side."));
    }

    private String memberImpact(ChatMemberState state, String capabilityImpact) {
        return switch (state) {
            case READY -> "Chat is available through the Weave workspace.";
            case DEGRADED -> "Chat is degraded. You can keep working where available; ask an admin to review Workspace Health.";
            case POLICY_BLOCKED -> "Chat is blocked by your role or group policy. Ask an admin if you need access.";
            case UNAVAILABLE -> "Chat is unavailable for this workspace right now.";
            case MISCONFIGURED -> "Chat is not ready for members in this workspace. Ask an admin to review Workspace Health.";
            case COMING_LATER -> "Chat is not enabled for this workspace yet.";
        };
    }

    private Map<String, Integer> safeCounts(Map<String, Integer> counts) {
        if (counts == null) {
            return Map.of();
        }
        Map<String, Integer> safe = new LinkedHashMap<>();
        counts.forEach((key, value) -> {
            String safeKey = safeIdentifier(key, "unknown");
            if (safeKey.matches("[a-z][a-z0-9_-]{0,40}")) {
                safe.put(safeKey, Math.max(0, value == null ? 0 : value));
            }
        });
        return safe;
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::safeText)
                .distinct()
                .limit(20)
                .toList();
    }

    private String safeProviderKey(String value, String fallback) {
        String safe = safeIdentifier(value, fallback);
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private String safeIdentifier(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String safe = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
        return safe.isBlank() ? fallback : safe;
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        return value.trim()
                .replaceAll("(?i)bearer\\s+[^\\s]+", "[redacted-token]")
                .replaceAll("(?i)xox[a-z]-[^\\s]+", "[redacted]")
                .replaceAll("(?i)secret=[^\\s&]+", "secret=[redacted]");
    }

    private String organizationId(Jwt jwt) {
        if (jwt == null) {
            throw new ChatAccessDeniedException();
        }
        Object tenant = jwt.getClaims().get("weave_tenant_id");
        if (!(tenant instanceof String value) || value.isBlank()) {
            throw new ChatAccessDeniedException();
        }
        return value.trim();
    }

    private ChatRequestContext requestContext(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null || jwt.getIssuer().toString().isBlank()) {
            throw new ChatAccessDeniedException();
        }
        String issuer = jwt.getIssuer().toString();
        String configuredPrincipalClaim = jwt.getClaimAsString(contextAuthorizationProperties.principalClaim());
        String authorizationPrincipalRef = contextAuthorizationProperties.principalRef(configuredPrincipalClaim);
        if (authorizationPrincipalRef == null) {
            throw new ChatAccessDeniedException();
        }
        String contextId = jwt.getClaimAsString("weave_context_id");
        if (contextId == null || contextId.isBlank()) {
            contextId = jwt.getClaimAsString("context_id");
        }
        if (contextId == null || contextId.isBlank()) {
            contextId = "workspace-default";
        }
        return new ChatRequestContext(
                organizationId(jwt),
                contextId.trim(),
                issuer,
                new ChatActorRef(actorRef(jwt)),
                authorizationPrincipalRef);
    }

    private String actorRef(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ChatAccessDeniedException();
        }
        return "user:" + jwt.getSubject();
    }

    private String opaqueTransactionId(String value) {
        if (value == null || value.isBlank() || value.length() > 160 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Chat transaction identifier is invalid.");
        }
        return value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
