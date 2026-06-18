package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WeaverRuntimeProfileResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class WeaverRuntimeService {

    private static final String MANAGED_BY = "weave-weaver-runtime-reconciler";
    private static final Pattern UNSAFE_DIAGNOSTIC = Pattern.compile(
            "(?i)(bearer\\s+[^\\s]+|refresh_token[=:][^\\s,}]+|api[_-]?key[=:][^\\s,}]+|secret[=:][^\\s,}]+|openclaw\\.json|memory://[^\\s,}]+|/memory/[^\\s,}]+|https?://[^\\s,}]*(@|token=|access_token=|refresh_token=|api[_-]?key=|secret=)[^\\s,}]*|https?://[^\\s,}]*(/_matrix/private|/private|/admin)[^\\s,}]*)");

    private final ConcurrentMap<String, WeaverRuntimeProfileResponse> issuedProfiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WeaverRuntimeInstance> runtimeInstances = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> latestProfileHashByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> currentProfileHashByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> rollbackProfileHashByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> revocationGenerationByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> profileFingerprintByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> profileSequenceByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> customizationByUser = new ConcurrentHashMap<>();

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final WeaverRuntimeProperties weaverRuntimeProperties;
    private final AuditEventPublisher auditEventPublisher;
    private final WeaverRuntimeRevocationStore revocationStore;

    @Autowired
    public WeaverRuntimeService(
            WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            WeaverRuntimeProperties weaverRuntimeProperties,
            AuditEventPublisher auditEventPublisher) {
        this(workspaceCapabilityService, workspaceCapabilityProperties, weaverRuntimeProperties, auditEventPublisher, new FileBackedWeaverRuntimeRevocationStore(System.getProperty("java.io.tmpdir") + "/weave-runtime-profile-revocations.json"));
    }

    WeaverRuntimeService(
            WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            WeaverRuntimeProperties weaverRuntimeProperties,
            AuditEventPublisher auditEventPublisher,
            WeaverRuntimeRevocationStore revocationStore) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
        this.weaverRuntimeProperties = weaverRuntimeProperties;
        this.auditEventPublisher = auditEventPublisher;
        this.revocationStore = revocationStore;
    }

    public WeaverRuntimeProfileResponse profileFor(Jwt jwt) {
        List<String> grantedCapabilities = workspaceCapabilityService.grantedCapabilities(jwt);
        String userRef = supportSafeUserRef(jwt);
        if (!workspaceCapabilityProperties.weaver().enabled()) {
            revokeProfilesForUser(userRef, "disabled-by-default");
            return disabledProfile(
                    userRef,
                    "disabled-by-default",
                    "Weaver is disabled by organization policy until an admin enables the provider category.");
        }
        if (!weaverRuntimeProperties.enabled()) {
            revokeProfilesForUser(userRef, "runtime-generator-disabled");
            return disabledProfile(
                    userRef,
                    "runtime-generator-disabled",
                    "Weaver runtime generation is disabled until the organization enables a governed runtime profile.");
        }
        if (!grantedCapabilities.contains("weaver.enabled")) {
            revokeProfilesForUser(userRef, "policy-blocked");
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
        String rollbackProfileHash = currentProfileHashByUser.getOrDefault(userRef, "none");
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
                revocationGenerationByUser.getOrDefault(userRef, 0),
                previousProfileHash,
                rollbackProfileHash,
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
                channelProjection(runtimeProfileHash, profileVersion, previousProfileHash, userRef, expiresAt, runtimeTokenExpiresAt, allowedCapabilities, toolAllowlist),
                credentialBrokerContract(userRef),
                auditPolicy(runtimeProfileHash, userRef),
                supportSafeProfileReceipt(profileVersion, runtimeProfileHash, signature, expiresAt, runtimeTokenExpiresAt, false, "active"),
                "writes-delete-external-send-provider-switch require approval receipts",
                "secretrefs-only-no-raw-provider-tokens",
                "one-user-one-isolated-workspace-memory-session-store",
                "Weaver runtime profile is governed by organization policy; unavailable tools are hidden from the runtime.");
        auditGeneratedProfile(response);
        issuedProfiles.put(runtimeProfileHash, response);
        rollbackProfileHashByUser.put(userRef, rollbackProfileHash);
        currentProfileHashByUser.put(userRef, runtimeProfileHash);
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
        if (rollback == null || !rollback.userRef().equals(userRef) || rollback.revoked() || Instant.parse(rollback.expiresAt()).isBefore(Instant.now())) {
            auditRollback(userRef, rollbackProfileHash, "rollback_denied");
            return disabledProfile(userRef, "runtime-profile-rollback-denied", "RuntimeProfile rollback failed closed.");
        }
        String currentProfileHash = currentProfileHashByUser.getOrDefault(userRef, "none");
        rollbackProfileHashByUser.put(userRef, currentProfileHash);
        currentProfileHashByUser.put(userRef, rollback.runtimeProfileHash());
        latestProfileHashByUser.put(userRef, rollback.runtimeProfileHash());
        auditRollback(userRef, rollback.runtimeProfileHash(), "rollback_restored");
        return rollback;
    }

    public WeaverRuntimeProfileResponse profileByHash(Jwt jwt, String runtimeProfileHash) {
        String userRef = supportSafeUserRef(jwt);
        if (durableRevocationFor(runtimeProfileHash).isPresent()) {
            auditRevocationDecision(userRef, runtimeProfileHash, "runtime-profile-fetch-revoked", "durable-revocation-store");
            return disabledProfile(userRef, "runtime-profile-fetch-revoked", "RuntimeProfile fetch-by-hash failed closed.");
        }
        WeaverRuntimeProfileResponse issued = issuedProfiles.get(runtimeProfileHash == null ? "" : runtimeProfileHash.strip());
        if (issued == null) {
            return disabledProfile(userRef, "runtime-profile-hash-not-issued", "RuntimeProfile fetch-by-hash failed closed.");
        }
        if (!issued.userRef().equals(userRef)
                || issued.revoked()
                || Instant.parse(issued.expiresAt()).isBefore(Instant.now())
                || !runtimeProfileHash.strip().equals(currentProfileHashByUser.getOrDefault(userRef, ""))) {
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
        Path expected = Path.of(workspacePath(supportSafeUserRef(jwt))).normalize();
        Path requested = Path.of(workspacePath).normalize();
        return requested.equals(expected) || requested.startsWith(expected);
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
                revocationGenerationByUser.getOrDefault(userRef, 0),
                "none",
                rollbackProfileHashByUser.getOrDefault(userRef, "none"),
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
                channelProjection(runtimeProfileHash, profileVersion, "none", userRef, expiresAt, expiresAt, List.of(), List.of()),
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

    private void revokeProfilesForUser(String userRef, String reason) {
        boolean revokedAny = false;
        int nextGeneration = revocationGenerationByUser.getOrDefault(userRef, 0) + 1;
        for (Map.Entry<String, WeaverRuntimeProfileResponse> entry : issuedProfiles.entrySet()) {
            WeaverRuntimeProfileResponse issued = entry.getValue();
            if (!issued.userRef().equals(userRef) || issued.revoked()) {
                continue;
            }
            WeaverRuntimeProfileResponse revoked = revokeProfile(issued, reason);
            issuedProfiles.put(entry.getKey(), revoked);
            persistRevocation(revoked, reason, nextGeneration);
            revokedAny = true;
        }
        currentProfileHashByUser.remove(userRef);
        rollbackProfileHashByUser.remove(userRef);
        if (revokedAny) {
            revocationGenerationByUser.put(userRef, nextGeneration);
        }
    }

    private WeaverRuntimeProfileResponse revokeProfile(WeaverRuntimeProfileResponse issued, String reason) {
        return new WeaverRuntimeProfileResponse(
                issued.enabled(),
                issued.posture(),
                issued.runtimeKind(),
                issued.runtimeProvider(),
                issued.modelProvider(),
                issued.toolProvider(),
                issued.generatedFrom(),
                issued.userRef(),
                issued.profileVersion(),
                issued.runtimeProfileHash(),
                issued.signature(),
                issued.expiresAt(),
                true,
                reason,
                issued.revocationGeneration() + 1,
                issued.previousProfileHash(),
                issued.rollbackProfileHash(),
                issued.baselineProfile(),
                issued.containerImage(),
                issued.workspacePath(),
                issued.isolatedAgentDirectory(),
                issued.dockerNetworkMode(),
                issued.allowedCapabilities(),
                issued.pluginAllowlist(),
                issued.toolAllowlist(),
                issued.execEnabled(),
                issued.elevatedEnabled(),
                issued.auditRequired(),
                issued.forkRequired(),
                issued.channelProjection(),
                issued.credentialBrokerContract(),
                issued.auditPolicy(),
                supportSafeProfileReceipt(
                        issued.profileVersion(),
                        issued.runtimeProfileHash(),
                        issued.signature(),
                        issued.expiresAt(),
                        String.valueOf(issued.supportSafeProfileReceipt().getOrDefault("runtimeTokenExpiresAt", issued.expiresAt())),
                        true,
                        reason),
                issued.approvalPolicy(),
                issued.secretPosture(),
                issued.isolationBoundary(),
                issued.memberImpact());
    }

    private Optional<WeaverRuntimeRevocationStore.RevocationRecord> durableRevocationFor(String runtimeProfileHash) {
        try {
            return revocationStore.recordForProfile(runtimeProfileHash == null ? "" : runtimeProfileHash.strip());
        } catch (RuntimeException exception) {
            return Optional.of(new WeaverRuntimeRevocationStore.RevocationRecord(
                    "user:unknown",
                    runtimeProfileHash == null ? "none" : runtimeProfileHash,
                    "unknown",
                    -1,
                    "revocation-store-unavailable",
                    "system:weaver-runtime-policy",
                    "runtime-profile",
                    Instant.now(),
                    "audit:weaver-runtime-revocation-store-unavailable"));
        }
    }

    private void persistRevocation(WeaverRuntimeProfileResponse revoked, String reason, int generation) {
        String evidenceRef = "audit:weaver-runtime-revocation:" + revoked.runtimeProfileHash();
        revocationStore.record(new WeaverRuntimeRevocationStore.RevocationRecord(
                revoked.userRef(),
                revoked.runtimeProfileHash(),
                revoked.signature(),
                generation,
                reason,
                "system:weaver-runtime-policy",
                "runtime-profile",
                Instant.now(),
                evidenceRef));
        auditRevocationDecision(revoked.userRef(), revoked.runtimeProfileHash(), reason, evidenceRef);
    }

    private void auditRevocationDecision(String userRef, String runtimeProfileHash, String reason, String evidenceRef) {
        auditEventPublisher.publish(new AuditEvent(
                "tenant:workspace",
                null,
                userRef,
                "weaver-runtime-policy",
                AuditAction.WEAVER_RUNTIME_PROFILE_REVOKED,
                Instant.now(),
                "weaver-profile:" + userRef + ":revocation",
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.ofEntries(
                        Map.entry("user", userRef),
                        Map.entry("runtimeProfileHash", runtimeProfileHash == null ? "none" : runtimeProfileHash),
                        Map.entry("profileRef", runtimeProfileHash == null ? "none" : "weave-runtime-profile://" + runtimeProfileHash),
                        Map.entry("action", "profile.revoke"),
                        Map.entry("domain", "weaver-runtime"),
                        Map.entry("decision", "revoked"),
                        Map.entry("reason", reason),
                        Map.entry("actor", "system:weaver-runtime-policy"),
                        Map.entry("scope", "runtime-profile"),
                        Map.entry("evidenceRef", evidenceRef),
                        Map.entry("supportSafe", true))));
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
            String runtimeTokenExpiresAt,
            List<String> allowedCapabilities,
            List<String> toolAllowlist) {
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
        List<String> mcpAllowedTools = governedMcpAllowedTools(allowedCapabilities, toolAllowlist);
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
                        Map.entry("enabled", !mcpAllowedTools.isEmpty()),
                        Map.entry("supportSafe", true),
                        Map.entry("rawEndpointExposed", false),
                        Map.entry("allowedTools", mcpAllowedTools),
                        Map.entry("approvalRequiredFor", mcpAllowedTools.stream()
                                .filter(tool -> tool.equals("calendar.create_event") || tool.equals("boards.comment"))
                                .toList())))));
    }

    private List<String> governedMcpAllowedTools(List<String> allowedCapabilities, List<String> toolAllowlist) {
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        if (allowedCapabilities.contains("weaver.calendar_read") && toolAllowlist.contains("calendar.search_events")) {
            tools.add("calendar.search_events");
        }
        if (allowedCapabilities.contains("weaver.calendar_create_event") && toolAllowlist.contains("calendar.create_event")) {
            tools.add("calendar.create_event");
        }
        if (allowedCapabilities.contains("weaver.boards_write") && toolAllowlist.contains("boards.comment")) {
            tools.add("boards.comment");
        }
        if (!tools.isEmpty()) {
            tools.add("admin.get_readiness");
            tools.add("weaver.get_runtime_profile_projection");
        }
        return List.copyOf(tools);
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
