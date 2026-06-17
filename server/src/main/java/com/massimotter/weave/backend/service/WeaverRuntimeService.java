package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WeaverRuntimeProfileResponse;
import com.massimotter.weave.backend.weaver.WeaverApprovalReceipt;
import com.massimotter.weave.backend.weaver.WeaverToolInvocationResult;
import com.massimotter.weave.backend.weaver.WeaverToolRegistry;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalReceiptRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationRequest;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpContentBlock;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpKey;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolAnnotations;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolCatalog;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolDefinition;
import com.massimotter.weave.contract.mcp.WeaveMcpToolMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class WeaverRuntimeService {

    private static final String MANAGED_BY = "weave-weaver-runtime-reconciler";
    private static final Pattern UNSAFE_DIAGNOSTIC = Pattern.compile(
            "(?i)(bearer\\s+[^\\s]+|refresh_token[=:][^\\s,}]+|api[_-]?key[=:][^\\s,}]+|secret[=:][^\\s,}]+|openclaw\\.json|memory://[^\\s,}]+|/memory/[^\\s,}]+)");

    private final ConcurrentMap<String, WeaverRuntimeProfileResponse> issuedProfiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WeaverRuntimeInstance> runtimeInstances = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> latestProfileHashByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> profileFingerprintByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> profileSequenceByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> customizationByUser = new ConcurrentHashMap<>();

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final WeaverRuntimeProperties weaverRuntimeProperties;
    private final AuditEventPublisher auditEventPublisher;
    private final WeaverToolRegistry weaverToolRegistry;

    public WeaverRuntimeService(
            WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            WeaverRuntimeProperties weaverRuntimeProperties,
            AuditEventPublisher auditEventPublisher,
            WeaverToolRegistry weaverToolRegistry) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
        this.weaverRuntimeProperties = weaverRuntimeProperties;
        this.auditEventPublisher = auditEventPublisher;
        this.weaverToolRegistry = weaverToolRegistry;
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
        Map<String, Object> customization = customizationByUser.getOrDefault(userRef, Map.of());
        String profileVersion = profileVersion(userRef, profileFingerprint(allowedCapabilities, pluginAllowlist, toolAllowlist, execEnabled, elevatedEnabled, customization));
        String expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String runtimeTokenExpiresAt = Instant.now().plus(10, ChronoUnit.MINUTES).toString();
        String previousProfileHash = latestProfileHashByUser.getOrDefault(userRef, "none");
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
                channelProjection(runtimeProfileHash, profileVersion, previousProfileHash, userRef, expiresAt, runtimeTokenExpiresAt),
                mcpProjection(runtimeProfileHash, profileVersion, previousProfileHash, userRef, expiresAt, runtimeTokenExpiresAt, toolAllowlist, allowedCapabilities),
                credentialBrokerContract(userRef),
                auditPolicy(runtimeProfileHash, userRef),
                supportSafeProfileReceipt(profileVersion, runtimeProfileHash, signature, expiresAt, runtimeTokenExpiresAt, false, "active"),
                "writes-delete-external-send-provider-switch require approval receipts",
                "secretrefs-only-no-raw-provider-tokens",
                "one-user-one-isolated-workspace-memory-session-store",
                "Weaver runtime profile is governed by organization policy; unavailable tools are hidden from the runtime.");
        auditGeneratedProfile(response);
        issuedProfiles.put(runtimeProfileHash, response);
        latestProfileHashByUser.put(userRef, runtimeProfileHash);
        return response;
    }

    public WeaverRuntimeCustomizationDecision applyRuntimeCustomization(Jwt jwt, Map<String, Object> requestedCustomization) {
        String userRef = supportSafeUserRef(jwt);
        Map<String, Object> request = requestedCustomization == null ? Map.of() : Map.copyOf(requestedCustomization);
        String denial = customizationDenial(request);
        if (denial != null) {
            auditCustomization(userRef, denial, false, request.keySet().stream().sorted().toList(), "blocked");
            return new WeaverRuntimeCustomizationDecision(false, denial, null, "Forbidden Weaver customization attempt was blocked by admin policy.");
        }
        customizationByUser.put(userRef, request);
        WeaverRuntimeProfileResponse profile = profileFor(jwt);
        auditCustomization(userRef, "allowed_user_customization", true, request.keySet().stream().sorted().toList(), profile.runtimeProfileHash());
        return new WeaverRuntimeCustomizationDecision(true, "allowed_user_customization", profile, "Customization accepted inside organization policy boundaries.");
    }

    public WeaverRuntimeProfileResponse rollbackRuntimeProfile(Jwt jwt, String rollbackProfileHash) {
        String userRef = supportSafeUserRef(jwt);
        WeaverRuntimeProfileResponse rollback = issuedProfiles.get(rollbackProfileHash == null ? "" : rollbackProfileHash.strip());
        if (rollback == null || !rollback.userRef().equals(userRef) || rollback.revoked()) {
            auditRollback(userRef, rollbackProfileHash, "rollback_denied");
            return disabledProfile(userRef, "runtime-profile-rollback-denied", "RuntimeProfile rollback failed closed.");
        }
        latestProfileHashByUser.put(userRef, rollback.runtimeProfileHash());
        auditRollback(userRef, rollback.runtimeProfileHash(), "rollback_restored");
        return rollback;
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

    public WeaverRuntimeInstance provisionRuntime(WeaverRuntimeProfileResponse profile, String orgRef, String policyVersion) {
        if (profile == null || !profile.enabled() || profile.revoked()) {
            throw new IllegalArgumentException("Only active Weaver RuntimeProfiles can be provisioned.");
        }
        Map<String, String> labels = runtimeLabels(profile, orgRef, policyVersion);
        WeaverRuntimeInstance instance = new WeaverRuntimeInstance(
                profile.userRef(),
                containerId(profile.userRef(), profile.runtimeProfileHash(), policyVersion),
                "running",
                profile.runtimeProfileHash(),
                policyVersion,
                profile.containerImage(),
                profile.workspacePath(),
                labels,
                Instant.now().toString(),
                null);
        runtimeInstances.put(profile.userRef(), instance);
        return instance;
    }

    public WeaverRuntimeInstance deactivateRuntime(String userRef, String reason) {
        WeaverRuntimeInstance current = runtimeInstances.get(userRef);
        if (current == null) {
            return new WeaverRuntimeInstance(userRef, "none", "stopped", "none", "none", "", "", Map.of(), Instant.now().toString(), reason);
        }
        WeaverRuntimeInstance stopped = current.withState("stopped", reason);
        runtimeInstances.put(userRef, stopped);
        return stopped;
    }

    public List<WeaverRuntimeInstance> runtimeInstances() {
        return List.copyOf(runtimeInstances.values());
    }

    public boolean canReadWorkspace(Jwt jwt, String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return false;
        }
        String expected = workspacePath(supportSafeUserRef(jwt));
        return workspacePath.equals(expected) || workspacePath.startsWith(expected + "/");
    }

    public List<WeaverRuntimeReconcileDecision> reconcile(
            String orgRef,
            List<WeaverRuntimeDesiredState> desiredStates,
            List<WeaverRuntimeInstance> actualInstances) {
        Map<String, WeaverRuntimeInstance> actualByUser = new LinkedHashMap<>();
        for (WeaverRuntimeInstance instance : actualInstances == null ? List.<WeaverRuntimeInstance>of() : actualInstances) {
            actualByUser.put(instance.userRef(), instance);
        }
        List<WeaverRuntimeReconcileDecision> decisions = new ArrayList<>();
        for (WeaverRuntimeDesiredState desired : desiredStates == null ? List.<WeaverRuntimeDesiredState>of() : desiredStates) {
            WeaverRuntimeInstance actual = actualByUser.remove(desired.userRef());
            WeaverRuntimeReconcileDecision decision = decide(orgRef, desired, actual);
            decisions.add(decision);
            auditReconcileDecision(decision);
        }
        for (WeaverRuntimeInstance orphan : actualByUser.values()) {
            WeaverRuntimeDesiredState desired = new WeaverRuntimeDesiredState(
                    orphan.userRef(), false, "revoked", orphan.runtimeProfileHash(), orphan.policyVersion(), orphan.containerImage(), orphan.workspacePath());
            WeaverRuntimeReconcileDecision decision = decide(orgRef, desired, orphan);
            decisions.add(decision);
            auditReconcileDecision(decision);
        }
        return List.copyOf(decisions);
    }

    public Map<String, Object> supportSafeRuntimeBundle(List<WeaverRuntimeInstance> instances, Map<String, String> diagnostics) {
        List<Map<String, Object>> redactedInstances = (instances == null ? List.<WeaverRuntimeInstance>of() : instances).stream()
                .map(instance -> Map.<String, Object>ofEntries(
                        Map.entry("userRef", instance.userRef()),
                        Map.entry("containerId", instance.containerId()),
                        Map.entry("state", instance.state()),
                        Map.entry("runtimeProfileHash", instance.runtimeProfileHash()),
                        Map.entry("policyVersion", instance.policyVersion()),
                        Map.entry("workspacePath", instance.workspacePath()),
                        Map.entry("labels", instance.labels()),
                        Map.entry("supportSafe", true)))
                .toList();
        Map<String, String> redactedDiagnostics = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : (diagnostics == null ? Map.<String, String>of() : diagnostics).entrySet()) {
            String key = entry.getKey() == null ? "diagnostic" : entry.getKey().toLowerCase(Locale.ROOT);
            if (key.contains("memory") || key.contains("secret") || key.contains("token") || key.contains("openclaw")) {
                redactedDiagnostics.put(key, "[redacted]");
            } else {
                redactedDiagnostics.put(key, redactDiagnostic(entry.getValue()));
            }
        }
        return Map.ofEntries(
                Map.entry("artifactKind", "weave-weaver-runtime-support-bundle"),
                Map.entry("redaction", "support_safe"),
                Map.entry("rawWeaverMemoryExported", false),
                Map.entry("rawOpenClawConfigExported", false),
                Map.entry("rawProviderSecretsExported", false),
                Map.entry("rawProviderPayloadsExported", false),
                Map.entry("instances", redactedInstances),
                Map.entry("diagnostics", redactedDiagnostics));
    }

    public WeaverRuntimeDesiredState desiredStateFromProfile(WeaverRuntimeProfileResponse profile, String policyVersion) {
        return new WeaverRuntimeDesiredState(
                profile.userRef(),
                profile.enabled() && !profile.revoked(),
                profile.revocationStatus(),
                profile.runtimeProfileHash(),
                policyVersion,
                profile.containerImage(),
                profile.workspacePath());
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
                channelProjection(runtimeProfileHash, profileVersion, "none", userRef, expiresAt, expiresAt),
                mcpProjection(runtimeProfileHash, profileVersion, "none", userRef, expiresAt, expiresAt, List.of(), List.of()),
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

    private Map<String, String> runtimeLabels(WeaverRuntimeProfileResponse profile, String orgRef, String policyVersion) {
        return Map.ofEntries(
                Map.entry("weave.org", orgRef == null || orgRef.isBlank() ? "org:unknown" : orgRef),
                Map.entry("weave.user", profile.userRef()),
                Map.entry("weave.profile_hash", profile.runtimeProfileHash()),
                Map.entry("weave.policy_version", policyVersion == null || policyVersion.isBlank() ? "policy:unknown" : policyVersion),
                Map.entry("weave.managed_by", MANAGED_BY));
    }

    private String containerId(String userRef, String runtimeProfileHash, String policyVersion) {
        return "weaver-" + sha256(String.join("|", userRef, runtimeProfileHash, policyVersion == null ? "" : policyVersion)).substring(0, 20);
    }

    private WeaverRuntimeReconcileDecision decide(
            String orgRef,
            WeaverRuntimeDesiredState desired,
            WeaverRuntimeInstance actual) {
        String desiredState = desired.active() ? "running" : "stopped";
        String actualState = actual == null ? "missing" : actual.state();
        String action;
        String outcome;
        if (!desired.active() && actual == null) {
            action = "noop";
            outcome = "already-stopped";
        } else if (!desired.active()) {
            action = "revoke";
            outcome = "stop-container";
        } else if (actual == null || !"running".equals(actual.state())) {
            action = "create";
            outcome = "start-container";
        } else if (!desired.runtimeProfileHash().equals(actual.runtimeProfileHash())
                || !desired.policyVersion().equals(actual.policyVersion())
                || !desired.workspacePath().equals(actual.workspacePath())) {
            action = "update";
            outcome = "replace-drifted-container";
        } else {
            action = "noop";
            outcome = "in-sync";
        }
        return new WeaverRuntimeReconcileDecision(
                desired.userRef(), orgRef, desiredState, actualState, action, outcome, desired.runtimeProfileHash(),
                actual == null ? "none" : actual.runtimeProfileHash(), desired.policyVersion(), actual == null ? "none" : actual.policyVersion());
    }

    private String redactDiagnostic(String value) {
        if (value == null) {
            return "";
        }
        return UNSAFE_DIAGNOSTIC.matcher(value).replaceAll("[redacted]");
    }

    private void auditReconcileDecision(WeaverRuntimeReconcileDecision decision) {
        auditEventPublisher.publish(new AuditEvent(
                "tenant:workspace",
                null,
                decision.userRef(),
                "weaver-runtime-reconciler",
                AuditAction.WEAVER_RUNTIME_RECONCILED,
                Instant.now(),
                "weaver-runtime:" + decision.userRef(),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.ofEntries(
                        Map.entry("user", decision.userRef()),
                        Map.entry("desiredState", decision.desiredState()),
                        Map.entry("actualState", decision.actualState()),
                        Map.entry("action", decision.action()),
                        Map.entry("outcome", decision.outcome()),
                        Map.entry("desiredRuntimeProfileHash", decision.desiredRuntimeProfileHash()),
                        Map.entry("actualRuntimeProfileHash", decision.actualRuntimeProfileHash()),
                        Map.entry("desiredPolicyVersion", decision.desiredPolicyVersion()),
                        Map.entry("actualPolicyVersion", decision.actualPolicyVersion()),
                        Map.entry("managedBy", MANAGED_BY),
                        Map.entry("supportSafe", true))));
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

    private String profileFingerprint(
            List<String> allowedCapabilities,
            List<String> pluginAllowlist,
            List<String> toolAllowlist,
            boolean execEnabled,
            boolean elevatedEnabled,
            Map<String, Object> customization) {
        return sha256(String.join("|",
                allowedCapabilities.toString(),
                pluginAllowlist.toString(),
                toolAllowlist.toString(),
                Boolean.toString(execEnabled),
                Boolean.toString(elevatedEnabled),
                weaverRuntimeProperties.baselineProfile(),
                weaverRuntimeProperties.image(),
                weaverRuntimeProperties.workspaceRootTemplate(),
                weaverRuntimeProperties.isolatedAgentDirectory(),
                weaverRuntimeProperties.dockerNetworkMode(),
                customization.toString()));
    }

    private String profileVersion(String userRef, String fingerprint) {
        String previous = profileFingerprintByUser.put(userRef, fingerprint);
        if (!fingerprint.equals(previous)) {
            profileSequenceByUser.merge(userRef, 1, Integer::sum);
        }
        int sequence = profileSequenceByUser.getOrDefault(userRef, 1);
        return "v" + sequence + "-" + fingerprint.substring(0, 12);
    }

    private String customizationDenial(Map<String, Object> request) {
        for (String key : request.keySet()) {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("_", "-");
            String compact = normalized.replace("-", "");
            Map<String, String> forbiddenReasons = Map.ofEntries(
                    Map.entry("rawopenclawconfig", "admin_policy_forbids_raw_openclaw_config"),
                    Map.entry("openclawjson", "admin_policy_forbids_raw_openclaw_config"),
                    Map.entry("arbitrarymcpserver", "admin_policy_forbids_arbitrary_mcp_server"),
                    Map.entry("ownsecret", "admin_policy_forbids_own_secrets"),
                    Map.entry("ownsecrets", "admin_policy_forbids_own_secrets"),
                    Map.entry("unlimitedtools", "admin_policy_forbids_unlimited_tools"),
                    Map.entry("uncheckedplugin", "admin_policy_forbids_unchecked_plugins"),
                    Map.entry("uncheckedplugins", "admin_policy_forbids_unchecked_plugins"),
                    Map.entry("teamwideaction", "admin_policy_forbids_team_wide_action"),
                    Map.entry("providersecret", "admin_policy_forbids_own_secrets"),
                    Map.entry("execenabled", "admin_policy_forbids_exec_enabled"),
                    Map.entry("maxdataaccess", "admin_policy_forbids_max_data_access"));
            if (forbiddenReasons.containsKey(compact)) {
                return forbiddenReasons.get(compact);
            }
        }
        return null;
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

    private Map<String, Object> channelProjection(
            String runtimeProfileHash,
            String profileVersion,
            String previousProfileHash,
            String userRef,
            String expiresAt,
            String runtimeTokenExpiresAt) {
        String runtimeTokenRef = "credentialref://weave/runtime/short-lived/" + userRef.replace("user:", "");
        Map<String, Object> runtimeProfileFetch = Map.of(
                "fetchRef", "weave-runtime-profile://" + runtimeProfileHash,
                "runtimeProfileHash", runtimeProfileHash,
                "profileVersion", profileVersion,
                "expiresAt", expiresAt,
                "previousProfileHash", previousProfileHash,
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
                Map.entry("mcpServersProjectedSeparately", true));
    }

    private Map<String, Object> mcpProjection(
            String runtimeProfileHash,
            String profileVersion,
            String previousProfileHash,
            String userRef,
            String expiresAt,
            String runtimeTokenExpiresAt,
            List<String> toolAllowlist,
            List<String> allowedCapabilities) {
        String runtimeTokenRef = "credentialref://weave/runtime/short-lived/" + userRef.replace("user:", "");
        Map<String, Object> binding = Map.ofEntries(
                Map.entry("serverKey", "weave-domain-tools"),
                Map.entry("displayName", "Weave governed domain tools"),
                Map.entry("transport", "streamable-http"),
                Map.entry("endpointRef", "internal://weave-mcp/streamable-http"),
                Map.entry("credentialRef", "credentialref://weave/mcp/weave-domain-tools/runtime-token"),
                Map.entry("runtimeTokenRef", runtimeTokenRef),
                Map.entry("runtimeTokenExpiresAt", runtimeTokenExpiresAt),
                Map.entry("runtimeProfileFetchRef", "weave-runtime-profile://" + runtimeProfileHash),
                Map.entry("routingChannelRef", "channels.weave-chat"),
                Map.entry("routingPlaneSeparated", true),
                Map.entry("enabled", !toolAllowlist.isEmpty()),
                Map.entry("supportSafe", true),
                Map.entry("rawEndpointExposed", false),
                Map.entry("rawServerConfigExposed", false),
                Map.entry("secretValuesExposed", false),
                Map.entry("previousProfileHash", previousProfileHash),
                Map.entry("profileVersion", profileVersion),
                Map.entry("expiresAt", expiresAt),
                Map.entry("allowedTools", toolAllowlist),
                Map.entry("allowedCapabilities", allowedCapabilities),
                Map.entry("approvalRequiredFor", List.of("calendar.create_event", "chat.send_message", "boards.comment")));
        return Map.of(
                "servers", Map.of("weave-domain-tools", binding),
                "supportSafe", true,
                "denyByDefault", true,
                "channelPlaneRef", "channels.weave-chat",
                "memberMayMutateServerBindings", false,
                "rawProviderEndpointsExposed", false);
    }

    public Map<String, Object> mcpServerProjection(Jwt jwt, String runtimeProfileHash, String serverKey) {
        WeaverRuntimeProfileResponse profile = profileByHash(jwt, runtimeProfileHash);
        if (!profile.enabled()) {
            return Map.of(
                    "serverKey", serverKey,
                    "status", profile.posture(),
                    "enabled", false,
                    "supportSafe", true,
                    "message", "MCP server discovery failed closed.");
        }
        Object server = profile.mcpProjection().getOrDefault("servers", Map.of());
        if (!(server instanceof Map<?, ?> servers) || !servers.containsKey(serverKey)) {
            return Map.of(
                    "serverKey", serverKey,
                    "status", "server_not_projected",
                    "enabled", false,
                    "supportSafe", true,
                    "message", "Requested MCP server is not projected by this RuntimeProfile.");
        }
        return Map.of("server", servers.get(serverKey), "supportSafe", true);
    }

    public BridgeDiscoveryResponse discoverMcpTools(Jwt jwt, String runtimeProfileHash, String serverKey) {
        WeaverRuntimeProfileResponse profile = profileByHash(jwt, runtimeProfileHash);
        RuntimeInvocationContext runtime = runtimeContext(profile, serverKey, null, discoveryAuditRef(serverKey));
        if (!profile.enabled() || !"weave-domain-tools".equals(serverKey)) {
            return new BridgeDiscoveryResponse(runtime, new WeaveMcpToolCatalog(new WeaveMcpKey(serverKey).value(), "weave-mcp-bridge-v1", List.of()));
        }
        return new BridgeDiscoveryResponse(
                runtime,
                new WeaveMcpToolCatalog(
                        "weave-domain-tools",
                        "weave-mcp-bridge-v1",
                        weaverToolRegistry.discover(profile.allowedCapabilities()).stream()
                                .filter(definition -> profile.toolAllowlist().contains(definition.name()))
                                .map(definition -> new WeaveMcpToolDefinition(
                                        definition.name(),
                                        definition.version(),
                                        definition.domain(),
                                        toContractMode(definition.mode().name()),
                                        definition.requiredCapability(),
                                        definition.writeLike(),
                                        definition.inputSchema(),
                                        new WeaveMcpToolAnnotations(
                                                "READ".equals(definition.mode().name()),
                                                definition.writeLike(),
                                                false),
                                        definition.supportSafeDescription()))
                                .toList()));
    }

    public BridgeInvocationResponse invokeMcpTool(
            Jwt jwt,
            String serverKey,
            String toolName,
            BridgeInvocationRequest request) {
        WeaverRuntimeProfileResponse profile = profileByHash(jwt, request.runtime().runtimeProfileHash());
        if (!profile.enabled() || !"weave-domain-tools".equals(serverKey)) {
            return bridgeInvocationResponse(
                    toolName,
                    ToolInvocationStatus.DENIED,
                    auditRef(toolName, "runtime_profile_fetch_denied"),
                    "MCP invocation failed closed.",
                    Map.of("supportSafe", true));
        }
        if (!toolName.equals(request.toolName())) {
            return bridgeInvocationResponse(
                    toolName,
                    ToolInvocationStatus.VALIDATION_ERROR,
                    auditRef(toolName, "tool_name_mismatch"),
                    "Tool name in request body must match the URL path.",
                    Map.of("supportSafe", true, "requestedToolName", request.toolName()));
        }
        WeaverToolInvocationResult result = weaverToolRegistry.invoke(new com.massimotter.weave.backend.weaver.WeaverToolInvocationRequest(
                toolName,
                profile.userRef(),
                profile.runtimeProfileHash(),
                profile.userRef(),
                profile.signature(),
                profile.revoked(),
                String.valueOf(profile.supportSafeProfileReceipt().getOrDefault("runtimeTokenExpiresAt", "")),
                true,
                profile.allowedCapabilities(),
                profile.toolAllowlist(),
                request.arguments(),
                request.runtime().approvalReceiptRef() == null ? null : request.runtime().approvalReceiptRef().value(),
                approvalReceipt(request)));
        return new BridgeInvocationResponse(
                result.toolName(),
                toInvocationStatus(result.status()),
                String.valueOf(result.redactedResult().getOrDefault("auditRef", auditRef(toolName, result.status()))),
                true,
                List.of(new WeaveMcpContentBlock("text", result.supportSafeMessage(), null, Map.of("status", result.status()))),
                result.redactedResult());
    }

    private WeaverApprovalReceipt approvalReceipt(BridgeInvocationRequest request) {
        ApprovalReceiptRef ref = request.runtime().approvalReceiptRef();
        if (ref == null) {
            return null;
        }
        Map<String, Object> arguments = request.arguments();
        return new WeaverApprovalReceipt(
                ref.value(),
                request.runtime().userRef().value(),
                request.toolName(),
                List.of(request.runtime().runtimeProfileRef().value()),
                String.valueOf(arguments.getOrDefault("approvalPolicyVersion", "support-safe-bridge-v1")),
                String.valueOf(arguments.getOrDefault("approvalExpiresAt", Instant.now().plus(5, ChronoUnit.MINUTES))),
                String.valueOf(arguments.getOrDefault("approvalAuditRef", request.runtime().auditRef())));
    }

    private BridgeInvocationResponse bridgeInvocationResponse(String toolName, ToolInvocationStatus status, String auditRef, String message, Map<String, Object> structuredContent) {
        return new BridgeInvocationResponse(
                toolName,
                status,
                auditRef,
                true,
                List.of(new WeaveMcpContentBlock("text", message, null, Map.of("status", status.name()))),
                structuredContent);
    }

    private RuntimeInvocationContext runtimeContext(
            WeaverRuntimeProfileResponse profile,
            String serverKey,
            ApprovalReceiptRef approvalReceiptRef,
            String auditRef) {
        return new RuntimeInvocationContext(
                new WeaveMcpRef("org:workspace"),
                new WeaveMcpRef(profile.userRef()),
                new WeaveMcpRef("weave-runtime-profile://" + profile.runtimeProfileHash()),
                profile.runtimeProfileHash(),
                new WeaveMcpRef(String.valueOf(serverProjectionRuntimeTokenRef(profile, serverKey))),
                auditRef,
                approvalReceiptRef,
                null,
                profile.allowedCapabilities(),
                profile.toolAllowlist());
    }

    private Object serverProjectionRuntimeTokenRef(WeaverRuntimeProfileResponse profile, String serverKey) {
        Object servers = profile.mcpProjection().get("servers");
        if (servers instanceof Map<?, ?> serverMap) {
            Object server = serverMap.get(serverKey);
            if (server instanceof Map<?, ?> serverProjection) {
                Object runtimeTokenRef = serverProjection.get("runtimeTokenRef");
                return runtimeTokenRef == null ? "credentialref://weave/runtime/short-lived/unknown" : runtimeTokenRef;
            }
        }
        return "credentialref://weave/runtime/short-lived/unknown";
    }

    private String discoveryAuditRef(String serverKey) {
        return "audit://weaver-mcp/" + serverKey + "/discover";
    }

    private String auditRef(String toolName, String status) {
        return "audit://weaver-tool/" + (toolName == null || toolName.isBlank() ? "unknown" : toolName) + "/" + status;
    }

    private ToolInvocationStatus toInvocationStatus(String status) {
        return switch (status) {
            case "ok" -> ToolInvocationStatus.SUCCESS;
            case "approval_required", "blocked", "scoped_grant_missing", "runtime_profile_fetch_denied", "runtime_profile_unsigned", "runtime_profile_user_mismatch", "runtime_profile_revoked", "runtime_token_expired", "consent_required", "overbroad_grant" -> ToolInvocationStatus.DENIED;
            case "tool_name_mismatch" -> ToolInvocationStatus.VALIDATION_ERROR;
            default -> ToolInvocationStatus.UNAVAILABLE;
        };
    }

    private WeaveMcpToolMode toContractMode(String mode) {
        return switch (mode) {
            case "READ" -> WeaveMcpToolMode.READ;
            case "WRITE" -> WeaveMcpToolMode.WRITE;
            case "EXTERNAL_SEND" -> WeaveMcpToolMode.EXTERNAL_SEND;
            default -> WeaveMcpToolMode.READ;
        };
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

    private void auditCustomization(String userRef, String reason, boolean accepted, List<String> requestedFields, String profileRef) {
        auditEventPublisher.publish(new AuditEvent(
                "tenant:workspace",
                null,
                userRef,
                "weaver-runtime-policy",
                AuditAction.ADMIN_POLICY_UPDATED,
                Instant.now(),
                "weaver-customization:" + userRef,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.ofEntries(
                        Map.entry("user", userRef),
                        Map.entry("action", "runtime-profile.customization"),
                        Map.entry("domain", "weaver-runtime"),
                        Map.entry("decision", accepted ? "accepted" : "blocked"),
                        Map.entry("policyReason", reason),
                        Map.entry("requestedFields", requestedFields),
                        Map.entry("profileRef", profileRef),
                        Map.entry("supportSafe", true))));
    }

    private void auditRollback(String userRef, String runtimeProfileHash, String decision) {
        auditEventPublisher.publish(new AuditEvent(
                "tenant:workspace",
                null,
                userRef,
                "weaver-runtime-policy",
                AuditAction.WEAVER_RUNTIME_PROFILE_ROLLED_BACK,
                Instant.now(),
                "weaver-profile:" + userRef + ":rollback",
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.ofEntries(
                        Map.entry("user", userRef),
                        Map.entry("runtimeProfileHash", runtimeProfileHash == null ? "none" : runtimeProfileHash),
                        Map.entry("action", "profile.rollback"),
                        Map.entry("domain", "weaver-runtime"),
                        Map.entry("decision", decision),
                        Map.entry("supportSafe", true))));
    }

    public record WeaverRuntimeInstance(
            String userRef,
            String containerId,
            String state,
            String runtimeProfileHash,
            String policyVersion,
            String containerImage,
            String workspacePath,
            Map<String, String> labels,
            String startedAt,
            String stoppedReason) {

        public WeaverRuntimeInstance {
            labels = Map.copyOf(labels == null ? Map.of() : labels);
        }

        WeaverRuntimeInstance withState(String state, String reason) {
            return new WeaverRuntimeInstance(
                    userRef, containerId, state, runtimeProfileHash, policyVersion, containerImage, workspacePath, labels, startedAt, reason);
        }
    }

    public record WeaverRuntimeDesiredState(
            String userRef,
            boolean active,
            String revocationStatus,
            String runtimeProfileHash,
            String policyVersion,
            String containerImage,
            String workspacePath) {
    }

    public record WeaverRuntimeReconcileDecision(
            String userRef,
            String orgRef,
            String desiredState,
            String actualState,
            String action,
            String outcome,
            String desiredRuntimeProfileHash,
            String actualRuntimeProfileHash,
            String desiredPolicyVersion,
            String actualPolicyVersion) {
    }

    public record WeaverRuntimeCustomizationDecision(
            boolean accepted,
            String policyReason,
            WeaverRuntimeProfileResponse profile,
            String memberImpact) {
    }
}
