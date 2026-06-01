package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WeaverRuntimeProfileResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class WeaverRuntimeService {

    private final ConcurrentMap<String, WeaverRuntimeProfileResponse> issuedProfiles = new ConcurrentHashMap<>();

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final WeaverRuntimeProperties weaverRuntimeProperties;
    private final AuditEventPublisher auditEventPublisher;

    public WeaverRuntimeService(
            WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            WeaverRuntimeProperties weaverRuntimeProperties,
            AuditEventPublisher auditEventPublisher) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
        this.weaverRuntimeProperties = weaverRuntimeProperties;
        this.auditEventPublisher = auditEventPublisher;
    }

    public WeaverRuntimeProfileResponse profileFor(Jwt jwt) {
        List<String> grantedCapabilities = workspaceCapabilityService.grantedCapabilities(jwt);
        String userRef = supportSafeUserRef(jwt);
        if (!workspaceCapabilityProperties.weaver().enabled()) {
            return disabledProfile(
                    userRef,
                    "disabled-by-default",
                    "Weaver is disabled by organization policy until an admin enables the provider category.");
        }
        if (!weaverRuntimeProperties.enabled()) {
            return disabledProfile(
                    userRef,
                    "runtime-generator-disabled",
                    "Weaver runtime generation is disabled until the organization enables a governed runtime profile.");
        }
        if (!grantedCapabilities.contains("weaver.enabled")) {
            return disabledProfile(
                    userRef,
                    "policy-blocked",
                    "Your role or group policy does not allow a Weaver runtime.");
        }

        List<String> allowedCapabilities = allowedCapabilities(grantedCapabilities);
        List<String> pluginAllowlist = weaverRuntimeProperties.pluginAllowlist();
        List<String> toolAllowlist = weaverRuntimeProperties.toolAllowlist();
        boolean execEnabled = weaverRuntimeProperties.execEnabled() && grantedCapabilities.contains("weaver.exec_enabled");
        boolean elevatedEnabled = weaverRuntimeProperties.elevatedEnabled() && grantedCapabilities.contains("weaver.elevated_enabled");
        String profileVersion = profileVersion(userRef, allowedCapabilities);
        String expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String runtimeTokenExpiresAt = Instant.now().plus(10, ChronoUnit.MINUTES).toString();
        String previousProfileHash = previousProfileHash(userRef, profileVersion);
        String runtimeProfileHash = runtimeProfileHash(
                userRef,
                profileVersion,
                expiresAt,
                false,
                "active",
                previousProfileHash,
                allowedCapabilities,
                pluginAllowlist,
                toolAllowlist,
                execEnabled,
                elevatedEnabled,
                weaverRuntimeProperties.auditRequired(),
                weaverRuntimeProperties.forkRequired(),
                weaverRuntimeProperties.image(),
                workspacePath(userRef),
                weaverRuntimeProperties.isolatedAgentDirectory(),
                weaverRuntimeProperties.dockerNetworkMode(),
                userRef);
        String signature = signProfile(runtimeProfileHash, profileVersion);
        WeaverRuntimeProfileResponse response = new WeaverRuntimeProfileResponse(
                true,
                "ready-to-provision",
                "per-user-docker",
                "openclaw-derived-container",
                "organization-default-model-profile",
                "weave-domain-tool-registry",
                "workspace-capability-policy",
                userRef,
                profileVersion,
                runtimeProfileHash,
                signature,
                expiresAt,
                false,
                "active",
                previousProfileHash,
                weaverRuntimeProperties.baselineProfile(),
                weaverRuntimeProperties.image(),
                workspacePath(userRef),
                weaverRuntimeProperties.isolatedAgentDirectory(),
                weaverRuntimeProperties.dockerNetworkMode(),
                allowedCapabilities,
                pluginAllowlist,
                toolAllowlist,
                execEnabled,
                elevatedEnabled,
                weaverRuntimeProperties.auditRequired(),
                weaverRuntimeProperties.forkRequired(),
                channelProjection(runtimeProfileHash, profileVersion, userRef, expiresAt, runtimeTokenExpiresAt),
                credentialBrokerContract(userRef),
                auditPolicy(runtimeProfileHash, userRef),
                supportSafeProfileReceipt(profileVersion, runtimeProfileHash, signature, expiresAt, runtimeTokenExpiresAt, false, "active"),
                "writes-delete-external-send-provider-switch require approval receipts",
                "secretrefs-only-no-raw-provider-tokens",
                "one-user-one-isolated-workspace-memory-session-store",
                "Weaver is available through the governed organization profile; unavailable tools are hidden from the runtime.");
        auditGeneratedProfile(response);
        issuedProfiles.put(runtimeProfileHash, response);
        return response;
    }

    public WeaverRuntimeProfileResponse profileByHash(Jwt jwt, String runtimeProfileHash) {
        String userRef = supportSafeUserRef(jwt);
        WeaverRuntimeProfileResponse issued = issuedProfiles.get(runtimeProfileHash == null ? "" : runtimeProfileHash.strip());
        if (issued == null) {
            return disabledProfile(userRef, "runtime-profile-hash-not-issued", "RuntimeProfile fetch-by-hash failed closed.");
        }
        if (!issued.userRef().equals(userRef) || issued.revoked() || Instant.parse(issued.expiresAt()).isBefore(Instant.now())) {
            return disabledProfile(userRef, "runtime-profile-fetch-denied", "RuntimeProfile fetch-by-hash failed closed.");
        }
        if (!Boolean.TRUE.equals(issued.supportSafeProfileReceipt().get("signed"))
                || !Boolean.TRUE.equals(issued.supportSafeProfileReceipt().get("fetchByHashRequired"))) {
            return disabledProfile(userRef, "runtime-profile-signature-missing", "RuntimeProfile fetch-by-hash failed closed.");
        }
        return issued;
    }

    private WeaverRuntimeProfileResponse disabledProfile(String userRef, String posture, String impact) {
        String profileVersion = "disabled-0";
        String expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES).toString();
        String runtimeProfileHash = runtimeProfileHash(
                userRef,
                profileVersion,
                expiresAt,
                true,
                posture,
                "none",
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                true,
                false,
                "",
                "",
                weaverRuntimeProperties.isolatedAgentDirectory(),
                "none",
                userRef);
        String signature = signProfile(runtimeProfileHash, profileVersion);
        return new WeaverRuntimeProfileResponse(
                false,
                posture,
                "per-user-docker",
                "openclaw-derived-container",
                "organization-default-model-profile",
                "weave-domain-tool-registry",
                "workspace-capability-policy",
                userRef,
                profileVersion,
                runtimeProfileHash,
                signature,
                expiresAt,
                true,
                posture,
                "none",
                weaverRuntimeProperties.baselineProfile(),
                "",
                "",
                weaverRuntimeProperties.isolatedAgentDirectory(),
                "none",
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                true,
                false,
                channelProjection(runtimeProfileHash, profileVersion, userRef, expiresAt, expiresAt),
                credentialBrokerContract(userRef),
                auditPolicy(runtimeProfileHash, userRef),
                supportSafeProfileReceipt(
                        profileVersion,
                        runtimeProfileHash,
                        signature,
                        expiresAt,
                        expiresAt,
                        true,
                        posture),
                "writes-delete-external-send-provider-switch require approval receipts",
                "secretrefs-only-no-raw-provider-tokens",
                "disabled-runtime-has-no-workspace-memory-session-store",
                impact);
    }

    private List<String> allowedCapabilities(List<String> grantedCapabilities) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String capability : weaverRuntimeProperties.allowedCapabilities()) {
            if (grantedCapabilities.contains(capability)) {
                allowed.add(capability);
            }
        }
        if (!weaverRuntimeProperties.execEnabled()) {
            allowed.add("weaver.exec_disabled");
        }
        return List.copyOf(allowed);
    }

    private String workspacePath(String userRef) {
        return weaverRuntimeProperties.workspaceRootTemplate().replace("{userId}", userRef.replace("user:", ""));
    }

    private String profileVersion(String userRef, List<String> allowedCapabilities) {
        return "v" + sha256(String.join("|", userRef, allowedCapabilities.toString(), weaverRuntimeProperties.baselineProfile()))
                .substring(0, 16);
    }

    private String runtimeProfileHash(
            String userRef,
            String profileVersion,
            String expiresAt,
            boolean revoked,
            String revocationStatus,
            String previousProfileHash,
            List<String> allowedCapabilities,
            List<String> pluginAllowlist,
            List<String> toolAllowlist,
            boolean execEnabled,
            boolean elevatedEnabled,
            boolean auditRequired,
            boolean forkRequired,
            String containerImage,
            String workspacePath,
            String isolatedAgentDirectory,
            String dockerNetworkMode,
            String credentialUserRef) {
        return "sha256:" + sha256(String.join("|",
                userRef,
                profileVersion,
                expiresAt,
                Boolean.toString(revoked),
                revocationStatus,
                previousProfileHash,
                weaverRuntimeProperties.baselineProfile(),
                containerImage,
                workspacePath,
                isolatedAgentDirectory,
                dockerNetworkMode,
                allowedCapabilities.toString(),
                pluginAllowlist.toString(),
                toolAllowlist.toString(),
                Boolean.toString(execEnabled),
                Boolean.toString(elevatedEnabled),
                Boolean.toString(auditRequired),
                Boolean.toString(forkRequired),
                "channels.weave-chat",
                "provider:chat:selected-by-admin",
                "credentialref://weave/runtime/weave-chat/" + credentialUserRef.replace("user:", ""),
                credentialBrokerContract(credentialUserRef).toString()));
    }

    private String signProfile(String runtimeProfileHash, String profileVersion) {
        return "weave-signature:v1:" + sha256(runtimeProfileHash + ":" + profileVersion + ":support-safe");
    }

    private String previousProfileHash(String userRef, String profileVersion) {
        return "sha256:" + sha256(userRef + ":previous:" + profileVersion);
    }

    private Map<String, Object> channelProjection(
            String runtimeProfileHash,
            String profileVersion,
            String userRef,
            String expiresAt,
            String runtimeTokenExpiresAt) {
        String runtimeTokenRef = "credentialref://weave/runtime/short-lived/" + userRef.replace("user:", "");
        Map<String, Object> runtimeProfileFetch = Map.of(
                "fetchRef", "weave-runtime-profile://" + runtimeProfileHash,
                "runtimeProfileHash", runtimeProfileHash,
                "profileVersion", profileVersion,
                "expiresAt", expiresAt,
                "previousProfileHash", previousProfileHash(userRef, profileVersion),
                "signatureRequired", true,
                "signatureAlgorithm", "weave-signature:v1",
                "revocationChecked", true,
                "supportSafe", true,
                "rawProfileBodyReturnedToMembers", false);
        return Map.ofEntries(
                Map.entry("channelId", "channels.weave-chat"),
                Map.entry("domain", "chat"),
                Map.entry("providerRef", "provider:chat:selected-by-admin"),
                Map.entry("routingProfileVersion", profileVersion),
                Map.entry("runtimeProfileHash", runtimeProfileHash),
                Map.entry("runtimeTokenRef", runtimeTokenRef),
                Map.entry("runtimeTokenExpiresAt", runtimeTokenExpiresAt),
                Map.entry("runtimeProfileFetch", runtimeProfileFetch),
                Map.entry("reloadStrategy", "reload-or-restart-stable-channel"),
                Map.entry("rawProviderChannelConfigsRendered", false),
                Map.entry("memberMaySwitchProviderAdapters", false),
                Map.entry("mcpServerBindings", List.of(Map.ofEntries(
                        Map.entry("serverKey", "weave-domain-tools"),
                        Map.entry("transport", "streamable-http"),
                        Map.entry("endpointRef", "internal://weave-mcp/streamable-http"),
                        Map.entry("credentialRef", "credentialref://weave/mcp/weave-domain-tools/runtime-token"),
                        Map.entry("runtimeTokenRef", runtimeTokenRef),
                        Map.entry("runtimeTokenExpiresAt", runtimeTokenExpiresAt),
                        Map.entry("runtimeProfileFetchRef", "weave-runtime-profile://" + runtimeProfileHash),
                        Map.entry("enabled", false),
                        Map.entry("supportSafe", true),
                        Map.entry("rawEndpointExposed", false),
                        Map.entry("allowedTools", List.of("admin.get_readiness", "weaver.get_runtime_profile_projection", "calendar.search_events", "boards.comment")),
                        Map.entry("approvalRequiredFor", List.of("boards.comment"))))));
    }

    private Map<String, Object> credentialBrokerContract(String userRef) {
        return Map.ofEntries(
                Map.entry("broker", "weave-credential-broker"),
                Map.entry("secretPosture", "credentialrefs-and-short-lived-access-only"),
                Map.entry("orgSecrets", "credentialref://weave/org/provider-secrets"),
                Map.entry("userGrants", "credentialref://weave/user-grants/" + userRef.replace("user:", "")),
                Map.entry("channelTokens", "credentialref://weave/channels/weave-chat/runtime-token"),
                Map.entry("mcpOAuth", "credentialref://weave/mcp/oauth-grants"),
                Map.entry("runtimeTokens", "credentialref://weave/runtime/short-lived"),
                Map.entry("shortLivedAccess", true),
                Map.entry("supportSafeReceipts", true),
                Map.entry("rawProviderSecretsExported", false),
                Map.entry("oauthRefreshTokensExported", false));
    }

    private Map<String, Object> auditPolicy(String runtimeProfileHash, String userRef) {
        return Map.of(
                "runtimeProfileHash", runtimeProfileHash,
                "user", userRef,
                "requiredFields", List.of("runtimeProfileHash", "user", "tool", "action", "domain", "providerRef", "credentialRef", "decision"),
                "decisionKinds", List.of("profile", "model", "channel", "tool", "mcp", "reload", "revocation", "rollback"),
                "supportSafe", true);
    }

    private Map<String, Object> supportSafeProfileReceipt(
            String profileVersion,
            String runtimeProfileHash,
            String signature,
            String expiresAt,
            String runtimeTokenExpiresAt,
            boolean revoked,
            String revocationStatus) {
        return Map.ofEntries(
                Map.entry("profileVersion", profileVersion),
                Map.entry("runtimeProfileHash", runtimeProfileHash),
                Map.entry("signature", signature),
                Map.entry("signed", true),
                Map.entry("fetchByHashRequired", true),
                Map.entry("fetchRef", "weave-runtime-profile://" + runtimeProfileHash),
                Map.entry("expiresAt", expiresAt),
                Map.entry("runtimeTokenExpiresAt", runtimeTokenExpiresAt),
                Map.entry("runtimeTokenExported", false),
                Map.entry("revoked", revoked),
                Map.entry("revocationStatus", revocationStatus),
                Map.entry("regeneratesOnPolicyOrProviderChange", true),
                Map.entry("containsRawSecrets", false),
                Map.entry("supportSafe", true));
    }

    private String supportSafeUserRef(Jwt jwt) {
        String subject = jwt == null ? "system" : jwt.getSubject();
        return "user:" + sha256(subject == null || subject.isBlank() ? "unknown" : subject).substring(0, 16);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for support-safe Weaver user refs", e);
        }
    }

    private void auditGeneratedProfile(WeaverRuntimeProfileResponse response) {
        if (!response.auditRequired()) {
            throw new IllegalStateException("Weaver runtime profiles require audit before provisioning");
        }
        auditEventPublisher.publish(new AuditEvent(
                "tenant:workspace",
                null,
                response.userRef(),
                "weaver-runtime-generator",
                AuditAction.WEAVER_RUNTIME_PROFILE_GENERATED,
                Instant.now(),
                "weaver-profile:" + response.userRef() + ":" + response.baselineProfile(),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.ofEntries(
                        Map.entry("posture", response.posture()),
                        Map.entry("runtimeProfileHash", response.runtimeProfileHash()),
                        Map.entry("user", response.userRef()),
                        Map.entry("tool", "runtime-profile-generator"),
                        Map.entry("action", "profile.generate"),
                        Map.entry("domain", "weaver-runtime"),
                        Map.entry("providerRef", response.channelProjection().get("providerRef")),
                        Map.entry("credentialRef", response.credentialBrokerContract().get("runtimeTokens")),
                        Map.entry("decision", response.revoked() ? "revoked" : "generated"),
                        Map.entry("runtimeKind", response.runtimeKind()),
                        Map.entry("generatedFrom", response.generatedFrom()),
                        Map.entry("allowedCapabilities", response.allowedCapabilities()),
                        Map.entry("execEnabled", response.execEnabled()),
                        Map.entry("elevatedEnabled", response.elevatedEnabled()),
                        Map.entry("supportSafe", true))));
    }
}
