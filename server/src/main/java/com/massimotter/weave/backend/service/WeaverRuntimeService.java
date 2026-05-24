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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class WeaverRuntimeService {

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
        WeaverRuntimeProfileResponse response = new WeaverRuntimeProfileResponse(
                true,
                "ready-to-provision",
                "per-user-docker",
                "workspace-capability-policy",
                userRef,
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
                "Weaver is available through the governed organization profile; unavailable tools are hidden from the runtime.");
        auditGeneratedProfile(response);
        return response;
    }

    private WeaverRuntimeProfileResponse disabledProfile(String userRef, String posture, String impact) {
        return new WeaverRuntimeProfileResponse(
                false,
                posture,
                "per-user-docker",
                "workspace-capability-policy",
                userRef,
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
                Map.of(
                        "posture", response.posture(),
                        "runtimeKind", response.runtimeKind(),
                        "generatedFrom", response.generatedFrom(),
                        "allowedCapabilities", response.allowedCapabilities(),
                        "execEnabled", response.execEnabled(),
                        "elevatedEnabled", response.elevatedEnabled(),
                        "supportSafe", true)));
    }
}
