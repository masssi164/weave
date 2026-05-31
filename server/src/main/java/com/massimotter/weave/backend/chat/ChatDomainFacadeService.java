package com.massimotter.weave.backend.chat;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightReport;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightRequest;
import com.massimotter.weave.backend.chat.domain.ChatProviderMappingRecord;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
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
    private final Clock clock;

    @Autowired
    public ChatDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher) {
        this(providerRegistry, providerSelectionRepository, workspaceCapabilityService, auditEventPublisher, Clock.systemUTC());
    }

    ChatDomainFacadeService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this.providerRegistry = providerRegistry;
        this.providerSelectionRepository = providerSelectionRepository;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.auditEventPublisher = auditEventPublisher;
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
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            return new ChatConversations(readiness, List.of());
        }
        // Provider adapters will populate canonical conversations behind this seam. Until then,
        // the facade returns an empty Weave-domain collection rather than leaking provider APIs.
        return new ChatConversations(readiness, List.of());
    }

    public ChatMessages messages(String conversationId, Jwt jwt) {
        ChatReadiness readiness = memberReadiness(jwt);
        if (readiness.memberState() != ChatMemberState.READY) {
            return new ChatMessages(readiness, safeIdentifier(conversationId, "conversation-unavailable"), List.of());
        }
        return new ChatMessages(readiness, safeIdentifier(conversationId, "conversation-empty"), List.of());
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
            return ChatMemberState.DISABLED;
        }
        if (maybeSelection.isEmpty()) {
            return ChatMemberState.MISCONFIGURED;
        }
        if (maybeProvider.isEmpty()) {
            return ChatMemberState.UNAVAILABLE;
        }
        ProviderStatusResponse provider = maybeProvider.get();
        if (!provider.enabled() || provider.state() == ProviderState.DISABLED) {
            return ChatMemberState.DISABLED;
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
            case DISABLED -> "Chat is disabled by workspace policy.";
            case DEGRADED -> "Chat is degraded. You can keep working where available; ask an admin to review Workspace Health.";
            case POLICY_BLOCKED -> "Chat is blocked by your role or group policy. Ask an admin if you need access.";
            case UNAVAILABLE -> "Chat is unavailable for this workspace right now.";
            case MISCONFIGURED -> "Chat is not ready for members in this workspace. Ask an admin to review Workspace Health.";
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
            return "weave-dogfood";
        }
        Object tenant = jwt.getClaims().get("weave_tenant");
        return tenant instanceof String value && !value.isBlank() ? value : "weave-dogfood";
    }

    private String actorRef(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return "actor:system";
        }
        return "user:" + jwt.getSubject();
    }
}
