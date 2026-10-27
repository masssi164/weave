package com.massimotter.weave.backend.chat.application;

import com.massimotter.weave.backend.chat.port.ChatConversationAdmission;

import com.massimotter.weave.backend.chat.port.ChatConversationAdmission.AuthorizedContext;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.AuditWriteGate;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.chat.api.ChatProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.chat.api.ChatProviderReplacementDryRunResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public class ChatProviderReplacementService {
    private static final String DOMAIN = "chat";
    private final ChatConversationAdmission admission;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final AuditEventPublisher auditEventPublisher;

    public ChatProviderReplacementService(ChatConversationAdmission admission,
            WorkspaceCapabilityService workspaceCapabilityService, AuditEventPublisher auditEventPublisher) {
        this.admission = admission;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.auditEventPublisher = auditEventPublisher;
    }

    public ChatProviderReplacementDryRunResponse dryRunProviderReplacement(
            Jwt jwt,
            ChatProviderReplacementDryRunRequest request) {
        var principal = admission.authorize(jwt, "chat.read", "provider_replacement_dry_run", ContextPermission.ADMIN);
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
            conflicts.add("Membership identity conflicts require admin resolution against the Keycloak role/group mapping before cutover.");
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

    private void requireAdminReadinessCapability(Jwt jwt) {
        workspaceCapabilityService.requireCapability(
                jwt,
                "admin_control_plane.readiness_read",
                DOMAIN,
                "provider_replacement_dry_run");
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
