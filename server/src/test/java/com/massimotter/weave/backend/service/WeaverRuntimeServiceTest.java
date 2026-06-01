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
        assertThat(profile.runtimeProvider()).isEqualTo("openclaw-derived-container");
        assertThat(profile.modelProvider()).isEqualTo("organization-default-model-profile");
        assertThat(profile.toolProvider()).isEqualTo("weave-domain-tool-registry");
        assertThat(profile.secretPosture()).isEqualTo("secretrefs-only-no-raw-provider-tokens");
        assertThat(profile.revoked()).isTrue();
        assertThat(profile.supportSafeProfileReceipt()).containsEntry("signed", true).containsEntry("containsRawSecrets", false);
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
        assertThat(profile.runtimeProvider()).isEqualTo("openclaw-derived-container");
        assertThat(profile.modelProvider()).isEqualTo("organization-default-model-profile");
        assertThat(profile.toolProvider()).isEqualTo("weave-domain-tool-registry");
        assertThat(profile.userRef()).startsWith("user:");
        assertThat(profile.userRef()).doesNotContain("member@example.invalid");
        assertThat(profile.profileVersion()).startsWith("v");
        assertThat(profile.runtimeProfileHash()).startsWith("sha256:");
        assertThat(profile.signature()).startsWith("weave-signature:v1:");
        assertThat(profile.revoked()).isFalse();
        assertThat(profile.revocationStatus()).isEqualTo("active");
        assertThat(profile.previousProfileHash()).startsWith("sha256:");
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
        assertThat(profile.channelProjection())
                .containsEntry("channelId", "channels.weave-chat")
                .containsEntry("providerRef", "provider:chat:selected-by-admin")
                .containsEntry("rawProviderChannelConfigsRendered", false)
                .containsEntry("memberMaySwitchProviderAdapters", false)
                .containsKey("mcpServerBindings");
        assertThat(profile.channelProjection().get("mcpServerBindings").toString())
                .contains("weave-domain-tools", "streamable-http", "calendar.search_events", "boards.comment")
                .doesNotContain("Bearer ", "openclaw.json", "rawMcpServerConfig");
        assertThat(profile.credentialBrokerContract())
                .containsEntry("broker", "weave-credential-broker")
                .containsEntry("shortLivedAccess", true)
                .containsEntry("supportSafeReceipts", true)
                .containsEntry("rawProviderSecretsExported", false)
                .containsEntry("oauthRefreshTokensExported", false);
        assertThat(profile.auditPolicy().get("decisionKinds").toString())
                .contains("profile", "model", "channel", "tool", "mcp", "reload", "revocation", "rollback");
        assertThat(profile.supportSafeProfileReceipt())
                .containsEntry("profileVersion", profile.profileVersion())
                .containsEntry("runtimeProfileHash", profile.runtimeProfileHash())
                .containsEntry("signature", profile.signature())
                .containsEntry("signed", true)
                .containsEntry("revoked", false)
                .containsEntry("supportSafe", true);
        assertThat(profile.approvalPolicy()).contains("approval receipts");
        assertThat(profile.secretPosture()).isEqualTo("secretrefs-only-no-raw-provider-tokens");
        assertThat(profile.isolationBoundary()).isEqualTo("one-user-one-isolated-workspace-memory-session-store");
        assertThat(profile.toString()).doesNotContain("refresh_token", "Bearer ", "xox", "sk-");

        assertThat(audit.events()).hasSize(1);
        assertThat(audit.events().get(0).action()).isEqualTo(AuditAction.WEAVER_RUNTIME_PROFILE_GENERATED);
        assertThat(audit.events().get(0).payload())
                .containsEntry("runtimeProfileHash", profile.runtimeProfileHash())
                .containsEntry("user", profile.userRef())
                .containsEntry("tool", "runtime-profile-generator")
                .containsEntry("action", "profile.generate")
                .containsEntry("domain", "weaver-runtime")
                .containsEntry("providerRef", "provider:chat:selected-by-admin")
                .containsEntry("decision", "generated");
        assertThat(audit.events().get(0).payload()).containsEntry("supportSafe", true);
        assertThat(audit.events().get(0).payload()).containsEntry("execEnabled", false);
    }

    @Test
    void regeneratesStableChatProjectionWhenProfileMetadataChanges() {
        WeaverRuntimeService service = service(true, runtimeProperties(true), new InMemoryAuditEventPublisher());

        var base = service.profileFor(jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot")));
        var regenerated = service.profileFor(jwt("different-member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot")));

        assertThat(regenerated.runtimeProfileHash()).isNotEqualTo(base.runtimeProfileHash());
        assertThat(regenerated.channelProjection()).containsEntry("channelId", "channels.weave-chat");
        assertThat(regenerated.channelProjection()).containsEntry("providerRef", "provider:chat:selected-by-admin");
        assertThat(regenerated.supportSafeProfileReceipt()).containsEntry("regeneratesOnPolicyOrProviderChange", true);
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
                        "iss", "https://auth.example.invalid/realms/acme",
                        "realm_access", Map.of("roles", roles),
                        "groups", groups));
    }
}
