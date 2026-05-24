package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class WeaverRuntimeServiceTest {

    @Test
    void keepsWeaverDisabledByDefaultEvenForRuntimeGroup() {
        // V01_GOVERNED_WEAVER_RUNTIME_POLICY
        WeaverRuntimeService service = service(
                false,
                runtimeProperties(true),
                new InMemoryAuditEventPublisher());

        var profile = service.profileFor(jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot")));

        assertThat(profile.enabled()).isFalse();
        assertThat(profile.posture()).isEqualTo("disabled-by-default");
        assertThat(profile.runtimeKind()).isEqualTo("per-user-docker");
        assertThat(profile.generatedFrom()).isEqualTo("workspace-capability-policy");
        assertThat(profile.execEnabled()).isFalse();
        assertThat(profile.elevatedEnabled()).isFalse();
        assertThat(profile.auditRequired()).isTrue();
    }

    @Test
    void blocksRuntimeWhenUserPolicyDoesNotGrantWeaverEnabled() {
        WeaverRuntimeService service = service(
                true,
                runtimeProperties(true),
                new InMemoryAuditEventPublisher());

        var profile = service.profileFor(jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-pilot")));

        assertThat(profile.enabled()).isFalse();
        assertThat(profile.posture()).isEqualTo("policy-blocked");
        assertThat(profile.allowedCapabilities()).isEmpty();
    }

    @Test
    void generatesAuditedPerUserDockerProfileFromCapabilityPolicy() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverRuntimeService service = service(true, runtimeProperties(true), audit);

        var profile = service.profileFor(jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot")));

        assertThat(profile.enabled()).isTrue();
        assertThat(profile.posture()).isEqualTo("ready-to-provision");
        assertThat(profile.runtimeKind()).isEqualTo("per-user-docker");
        assertThat(profile.generatedFrom()).isEqualTo("workspace-capability-policy");
        assertThat(profile.userRef()).startsWith("user:");
        assertThat(profile.userRef()).doesNotContain("member@example.invalid");
        assertThat(profile.workspacePath()).startsWith("/var/lib/weave/weaver/");
        assertThat(profile.isolatedAgentDirectory()).isEqualTo(".weaver/agents");
        assertThat(profile.dockerNetworkMode()).isEqualTo("none");
        assertThat(profile.allowedCapabilities()).containsExactly("weaver.files_read", "weaver.exec_disabled");
        assertThat(profile.pluginAllowlist()).containsExactly("weave-files-readonly");
        assertThat(profile.toolAllowlist()).containsExactly("files.read");
        assertThat(profile.execEnabled()).isFalse();
        assertThat(profile.elevatedEnabled()).isFalse();
        assertThat(profile.auditRequired()).isTrue();
        assertThat(profile.forkRequired()).isFalse();

        assertThat(audit.events()).hasSize(1);
        assertThat(audit.events().get(0).action()).isEqualTo(AuditAction.WEAVER_RUNTIME_PROFILE_GENERATED);
        assertThat(audit.events().get(0).payload()).containsEntry("supportSafe", true);
        assertThat(audit.events().get(0).payload()).containsEntry("execEnabled", false);
    }

    private WeaverRuntimeService service(
            boolean workspaceWeaverEnabled,
            WeaverRuntimeProperties runtimeProperties,
            InMemoryAuditEventPublisher audit) {
        WorkspaceCapabilityProperties capabilities = new WorkspaceCapabilityProperties(
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(workspaceWeaverEnabled, null, WorkspaceCapabilityReadiness.READY));
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.weave.local/realms/weave");
        WorkspaceCapabilityService capabilityService = new WorkspaceCapabilityService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                capabilities,
                runtimeProperties);
        return new WeaverRuntimeService(capabilityService, capabilities, runtimeProperties, audit);
    }

    private WeaverRuntimeProperties runtimeProperties(boolean enabled) {
        return new WeaverRuntimeProperties(
                enabled,
                null,
                null,
                null,
                null,
                null,
                List.of("weave-weaver-runtime"),
                List.of("weaver.files_read", "weaver.exec_disabled"),
                List.of("weave-files-readonly"),
                List.of("files.read"),
                false,
                false,
                true,
                false);
    }

    private Jwt jwt(String subject, List<String> roles, List<String> groups) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "realm_access", Map.of("roles", roles),
                        "groups", groups));
    }
}
