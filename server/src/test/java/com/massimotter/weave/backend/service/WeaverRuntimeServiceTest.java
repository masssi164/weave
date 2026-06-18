package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WeaverRuntimeProfileResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(profile.signature()).startsWith("weave-hmac-sha256:v1:keyref:");
        assertThat(profile.revoked()).isFalse();
        assertThat(profile.revocationStatus()).isEqualTo("active");
        assertThat(profile.revocationGeneration()).isZero();
        assertThat(profile.previousProfileHash()).isEqualTo("none");
        assertThat(profile.rollbackProfileHash()).isEqualTo("none");
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
                .containsEntry("runtimeTokenExpiresAt", profile.supportSafeProfileReceipt().get("runtimeTokenExpiresAt"))
                .containsEntry("rawProviderChannelConfigsRendered", false)
                .containsEntry("memberMaySwitchProviderAdapters", false)
                .containsKey("mcpServerBindings");
        assertThat(profile.channelProjection().get("runtimeProfileFetch").toString())
                .contains("fetchRef=weave-runtime-profile://" + profile.runtimeProfileHash())
                .contains("signatureRequired=true", "revocationChecked=true", "rawProfileBodyReturnedToMembers=false");
        assertThat(profile.channelProjection().get("mcpServerBindings").toString())
                .contains("weave-domain-tools", "streamable-http")
                .contains("runtimeProfileFetchRef=weave-runtime-profile://" + profile.runtimeProfileHash())
                .contains("runtimeTokenRef=credentialref://weave/runtime/short-lived/")
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
                .containsEntry("signatureAlgorithm", "weave-hmac-sha256:v1")
                .containsEntry("signatureKeyRef", "secretref://weave/weaver/runtime-profile-signing-key")
                .containsEntry("signed", true)
                .containsEntry("fetchByHashRequired", true)
                .containsEntry("fetchRef", "weave-runtime-profile://" + profile.runtimeProfileHash())
                .containsEntry("runtimeTokenExported", false)
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
    void projectsGovernedMcpCalendarToolsFromKeycloakDerivedGroups() {
        WeaverRuntimeService service = service(true, runtimeProperties(
                true,
                List.of("weaver.files_read", "weaver.exec_disabled", "weaver.calendar_read", "weaver.calendar_create_event"),
                List.of("calendar.search_events", "calendar.create_event")), new InMemoryAuditEventPublisher());

        var profile = service.profileFor(jwt(
                "member@example.invalid",
                List.of("member"),
                List.of("weave-weaver-runtime", "weave-weaver-pilot", "weave-calendar-editors")));

        assertThat(profile.enabled()).isTrue();
        assertThat(profile.allowedCapabilities()).contains("weaver.calendar_read", "weaver.calendar_create_event");
        assertThat(profile.channelProjection().get("mcpServerBindings").toString())
                .contains("enabled=true", "calendar.search_events", "calendar.create_event")
                .contains("approvalRequiredFor=[calendar.create_event]")
                .contains("runtimeProfileFetchRef=weave-runtime-profile://" + profile.runtimeProfileHash())
                .doesNotContain("write calendar", "Bearer ", "rawMcpServerConfig");
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

    @Test
    void versionsProfileCustomizationsAndRollsBackToPreviousProfile() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverRuntimeService service = service(true, runtimeProperties(true), audit);
        Jwt member = jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot"));

        var base = service.profileFor(member);
        var customized = service.applyRuntimeCustomization(member, Map.of(
                "displayName", "Otter Weaver",
                "style", "concise",
                "language", "en",
                "memoryOptIn", true));

        assertThat(customized.accepted()).isTrue();
        assertThat(customized.profile().profileVersion()).isNotEqualTo(base.profileVersion());
        assertThat(customized.profile().runtimeProfileHash()).isNotEqualTo(base.runtimeProfileHash());
        assertThat(customized.profile().previousProfileHash()).isEqualTo(base.runtimeProfileHash());

        var rolledBack = service.rollbackRuntimeProfile(member, base.runtimeProfileHash());

        assertThat(rolledBack.runtimeProfileHash()).isEqualTo(base.runtimeProfileHash());
        assertThat(rolledBack.profileVersion()).isEqualTo(base.profileVersion());
        assertThat(audit.events()).extracting(event -> event.action())
                .contains(AuditAction.ADMIN_POLICY_UPDATED, AuditAction.WEAVER_RUNTIME_PROFILE_ROLLED_BACK);
        assertThat(audit.events().toString()).doesNotContain("member@example.invalid", "openclaw.json", "Bearer ");
    }

    @Test
    void blocksForbiddenCustomizationAttemptsAndAuditsPolicyReason() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverRuntimeService service = service(true, runtimeProperties(true), audit);

        var decision = service.applyRuntimeCustomization(
                jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime")),
                Map.of("rawOpenClawConfig", "openclaw.json {\"apiKey\":\"sk-secret\"}"));

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.policyReason()).isEqualTo("admin_policy_forbids_raw_openclaw_config");
        assertThat(decision.profile()).isNull();
        assertThat(audit.events()).hasSize(1);
        assertThat(audit.events().get(0).action()).isEqualTo(AuditAction.ADMIN_POLICY_UPDATED);
        assertThat(audit.events().get(0).payload())
                .containsEntry("decision", "blocked")
                .containsEntry("policyReason", "admin_policy_forbids_raw_openclaw_config")
                .containsEntry("supportSafe", true);
        assertThat(audit.events().toString()).doesNotContain("sk-secret", "Bearer ", "refresh_token");
    }

    @Test
    void fetchesOnlyIssuedCurrentRuntimeProfileByHashForSameUser() {
        WeaverRuntimeService service = service(true, runtimeProperties(true), new InMemoryAuditEventPublisher());
        Jwt member = jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot"));

        var issued = service.profileFor(member);
        var fetched = service.profileByHash(member, issued.runtimeProfileHash());

        assertThat(fetched.enabled()).isTrue();
        assertThat(fetched.runtimeProfileHash()).isEqualTo(issued.runtimeProfileHash());
        assertThat(fetched.supportSafeProfileReceipt()).containsEntry("fetchByHashRequired", true);

        var customized = service.applyRuntimeCustomization(member, Map.of("style", "concise"));
        assertThat(customized.accepted()).isTrue();
        var stale = service.profileByHash(member, issued.runtimeProfileHash());
        assertThat(stale.enabled()).isFalse();
        assertThat(stale.posture()).isEqualTo("runtime-profile-fetch-denied");

        var unknown = service.profileByHash(member, "sha256:not-issued");
        assertThat(unknown.enabled()).isFalse();
        assertThat(unknown.posture()).isEqualTo("runtime-profile-hash-not-issued");

        Jwt otherMember = jwt("other@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot"));
        var mismatched = service.profileByHash(otherMember, issued.runtimeProfileHash());
        assertThat(mismatched.enabled()).isFalse();
        assertThat(mismatched.posture()).isEqualTo("runtime-profile-fetch-denied");
        assertThat(mismatched.toString()).doesNotContain("Bearer ", "openclaw.json", "refresh_token", "https://matrix.weave.test");
    }

    @Test
    void rejectsMissingTamperedExpiredAndWrongKeyRuntimeProfileSignatures() {
        WeaverRuntimeService service = service(true, runtimeProperties(true), new InMemoryAuditEventPublisher());
        Jwt member = jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot"));
        var issued = service.profileFor(member);

        assertThat(service.profileByHash(member, issued.runtimeProfileHash()).enabled()).isTrue();

        var missingSignature = copyProfile(issued, issued.runtimeProfileHash(), "", issued.expiresAt());
        assertThatThrownBy(() -> service.provisionRuntime(missingSignature, "org:acme", "policy:v32"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid signed");

        var tamperedProfile = copyProfile(issued, "sha256:tampered", issued.signature(), issued.expiresAt());
        assertThatThrownBy(() -> service.provisionRuntime(tamperedProfile, "org:acme", "policy:v32"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid signed");

        var expiredProfile = copyProfile(issued, issued.runtimeProfileHash(), issued.signature(), Instant.now().minusSeconds(60).toString());
        assertThatThrownBy(() -> service.provisionRuntime(expiredProfile, "org:acme", "policy:v32"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid signed");

        WeaverRuntimeService wrongKeyService = service(true, runtimePropertiesWithKey(true, "different-weaver-runtime-profile-signing-key-32-bytes"), new InMemoryAuditEventPublisher());
        assertThatThrownBy(() -> wrongKeyService.provisionRuntime(issued, "org:acme", "policy:v32"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid signed");
    }

    @Test
    void rejectsStaleSignedProfileDuringProvisioning() {
        WeaverRuntimeService service = service(true, runtimeProperties(true), new InMemoryAuditEventPublisher());
        Jwt member = jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot"));

        var issued = service.profileFor(member);
        var customized = service.applyRuntimeCustomization(member, Map.of("style", "concise"));

        assertThat(customized.accepted()).isTrue();
        assertThatThrownBy(() -> service.provisionRuntime(issued, "org:acme", "policy:v32"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current Weaver RuntimeProfiles");
        assertThat(service.provisionRuntime(customized.profile(), "org:acme", "policy:v32").runtimeProfileHash())
                .isEqualTo(customized.profile().runtimeProfileHash());
    }

    @Test
    void usesConfigBoundSigningKeyAcrossServiceReplicasAndFailsWhenMissing() {
        var first = service(true, runtimePropertiesWithKey(true, "stable-weaver-runtime-profile-signing-key-32-bytes"), new InMemoryAuditEventPublisher());
        var second = service(true, runtimePropertiesWithKey(true, "stable-weaver-runtime-profile-signing-key-32-bytes"), new InMemoryAuditEventPublisher());
        Jwt member = jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot"));

        var issued = first.profileFor(member);
        var replicaIssued = second.profileFor(member);

        assertThat(replicaIssued.signature()).isNotBlank();
        assertThat(replicaIssued.signature().split(":")[3]).isEqualTo(issued.signature().split(":")[3]);
        assertThatThrownBy(() -> service(true, runtimePropertiesWithKey(true, null), new InMemoryAuditEventPublisher()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signing key is not configured");
        assertThat(issued.supportSafeProfileReceipt().toString())
                .contains("secretref://weave/weaver/runtime-profile-signing-key")
                .doesNotContain("stable-weaver-runtime-profile-signing-key");
        // RuntimeProfile has no not-before field today; expiry and current-issued checks are the temporal fail-closed gates.
    }

    @Test
    void revokesPreviouslyIssuedProfilesWhenEligibilityIsRemoved() {
        WeaverRuntimeService service = service(true, runtimeProperties(true), new InMemoryAuditEventPublisher());
        Jwt eligible = jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-runtime", "weave-weaver-pilot"));

        var issued = service.profileFor(eligible);
        var blocked = service.profileFor(jwt("member@example.invalid", List.of("member"), List.of("weave-weaver-pilot")));

        assertThat(blocked.enabled()).isFalse();
        assertThat(blocked.posture()).isEqualTo("policy-blocked");
        assertThat(blocked.revocationGeneration()).isEqualTo(1);

        var revokedFetch = service.profileByHash(eligible, issued.runtimeProfileHash());
        assertThat(revokedFetch.enabled()).isFalse();
        assertThat(revokedFetch.posture()).isEqualTo("runtime-profile-fetch-denied");
    }

    @Test
    void provisionsDistinctPerUserRuntimeInstancesAndStopsOnlyDisabledUser() {
        WeaverRuntimeService service = service(true, runtimeProperties(true), new InMemoryAuditEventPublisher());
        var alice = service.profileFor(jwt("alice@example.invalid", List.of("member"), List.of("weave-weaver-runtime")));
        var bob = service.profileFor(jwt("bob@example.invalid", List.of("member"), List.of("weave-weaver-runtime")));

        var aliceRuntime = service.provisionRuntime(alice, "org:acme", "policy:v24");
        var bobRuntime = service.provisionRuntime(bob, "org:acme", "policy:v24");
        var stoppedAlice = service.deactivateRuntime(alice.userRef(), "admin-disabled");

        assertThat(aliceRuntime.userRef()).isNotEqualTo(bobRuntime.userRef());
        assertThat(aliceRuntime.containerId()).isNotEqualTo(bobRuntime.containerId());
        assertThat(aliceRuntime.workspacePath()).isNotEqualTo(bobRuntime.workspacePath());
        assertThat(aliceRuntime.labels())
                .containsEntry("weave.org", "org:acme")
                .containsEntry("weave.user", alice.userRef())
                .containsEntry("weave.profile_hash", alice.runtimeProfileHash())
                .containsEntry("weave.policy_version", "policy:v24")
                .containsEntry("weave.managed_by", "weave-weaver-runtime-reconciler");
        assertThat(stoppedAlice.state()).isEqualTo("stopped");
        assertThat(stoppedAlice.stoppedReason()).isEqualTo("admin-disabled");
        assertThat(service.runtimeInstances())
                .filteredOn(instance -> instance.userRef().equals(bob.userRef()))
                .singleElement()
                .extracting(WeaverRuntimeService.WeaverRuntimeInstance::state)
                .isEqualTo("running");
    }

    @Test
    void reconcilesCreateUpdateStopAndRevokeWithSupportSafeAudit() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverRuntimeService service = service(true, runtimeProperties(true), audit);
        var alice = service.profileFor(jwt("alice@example.invalid", List.of("member"), List.of("weave-weaver-runtime")));
        var bob = service.profileFor(jwt("bob@example.invalid", List.of("member"), List.of("weave-weaver-runtime")));
        var carol = service.profileFor(jwt("carol@example.invalid", List.of("member"), List.of("weave-weaver-runtime")));

        var bobDrifted = new WeaverRuntimeService.WeaverRuntimeInstance(
                bob.userRef(),
                "weaver-drifted",
                "running",
                "sha256:old-profile",
                "policy:v23",
                bob.containerImage(),
                bob.workspacePath(),
                Map.of("weave.managed_by", "weave-weaver-runtime-reconciler"),
                Instant.now().toString(),
                null);
        var carolRunning = service.provisionRuntime(carol, "org:acme", "policy:v24");

        var decisions = service.reconcile(
                "org:acme",
                List.of(
                        service.desiredStateFromProfile(alice, "policy:v24"),
                        service.desiredStateFromProfile(bob, "policy:v24"),
                        new WeaverRuntimeService.WeaverRuntimeDesiredState(
                                carol.userRef(), false, "admin-disabled", carol.runtimeProfileHash(), "policy:v24", carol.containerImage(), carol.workspacePath())),
                List.of(bobDrifted, carolRunning));

        assertThat(decisions)
                .extracting(WeaverRuntimeService.WeaverRuntimeReconcileDecision::action)
                .containsExactly("create", "update", "revoke");
        assertThat(decisions).allSatisfy(decision -> assertThat(decision.outcome()).isNotBlank());
        assertThat(audit.events())
                .filteredOn(event -> event.action() == AuditAction.WEAVER_RUNTIME_RECONCILED)
                .hasSize(3)
                .allSatisfy(event -> assertThat(event.payload())
                        .containsKeys("desiredState", "actualState", "action", "outcome")
                        .containsEntry("supportSafe", true));
        assertThat(audit.events().toString()).doesNotContain("alice@example.invalid", "bob@example.invalid", "openclaw.json", "Bearer ");
    }

    @Test
    void blocksCrossUserWorkspaceReadsAndRedactsWeaverSupportBundle() {
        WeaverRuntimeService service = service(true, runtimeProperties(true), new InMemoryAuditEventPublisher());
        Jwt aliceJwt = jwt("alice@example.invalid", List.of("member"), List.of("weave-weaver-runtime"));
        Jwt bobJwt = jwt("bob@example.invalid", List.of("member"), List.of("weave-weaver-runtime"));
        var alice = service.profileFor(aliceJwt);
        var bob = service.profileFor(bobJwt);
        var aliceRuntime = service.provisionRuntime(alice, "org:acme", "policy:v24");
        var bobRuntime = service.provisionRuntime(bob, "org:acme", "policy:v24");

        assertThat(service.canReadWorkspace(aliceJwt, aliceRuntime.workspacePath() + "/notes.md")).isTrue();
        assertThat(service.canReadWorkspace(aliceJwt, aliceRuntime.workspacePath() + "/../" + bob.userRef() + "/memory/session.json")).isFalse();
        assertThat(service.canReadWorkspace(aliceJwt, bobRuntime.workspacePath() + "/memory/session.json")).isFalse();
        assertThat(service.canReadWorkspace(bobJwt, aliceRuntime.workspacePath() + "/memory/session.json")).isFalse();

        Map<String, Object> bundle = service.supportSafeRuntimeBundle(
                List.of(aliceRuntime, bobRuntime),
                Map.of(
                        "weaverMemory", "memory://alice/private prompt about Bob",
                        "openclawConfig", "openclaw.json {\"apiKey\":\"sk-secret\"}",
                        "providerDiagnostic", "Bearer raw-token refresh_token=raw https://matrix.weave.test/_matrix/private",
                        "providerUrl", "https://svc-user:svc-pass@matrix.weave.test/_matrix/admin?access_token=raw",
                        "health", "runtime labels ok"));

        assertThat(bundle)
                .containsEntry("redaction", "support_safe")
                .containsEntry("rawWeaverMemoryExported", false)
                .containsEntry("rawOpenClawConfigExported", false)
                .containsEntry("rawProviderSecretsExported", false);
        assertThat(bundle.toString())
                .doesNotContain("memory://alice", "private prompt", "openclaw.json", "sk-secret", "Bearer raw-token", "refresh_token=raw", "svc-pass", "access_token=raw", "_matrix/admin")
                .contains("[redacted]");
    }

    private WeaverRuntimeService service(
            boolean workspaceWeaverEnabled,
            WeaverRuntimeProperties runtimeProperties,
            InMemoryAuditEventPublisher audit) {
        WorkspaceCapabilityProperties capabilities = new WorkspaceCapabilityProperties(
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                new WorkspaceCapabilityProperties.Capability(workspaceWeaverEnabled, null, WorkspaceCapabilityReadiness.READY));
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        WorkspaceCapabilityService capabilityService = new WorkspaceCapabilityService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                capabilities,
                runtimeProperties);
        return new WeaverRuntimeService(capabilityService, capabilities, runtimeProperties, audit);
    }

    private WeaverRuntimeProperties runtimeProperties(boolean enabled) {
        return runtimeProperties(enabled, List.of("weaver.files_read", "weaver.exec_disabled"), List.of("files.read"));
    }

    private WeaverRuntimeProperties runtimeProperties(boolean enabled, List<String> allowedCapabilities, List<String> toolAllowlist) {
        return runtimePropertiesWithKey(enabled, allowedCapabilities, toolAllowlist, "weaver-runtime-profile-test-signing-key-32-bytes-minimum");
    }

    private WeaverRuntimeProperties runtimePropertiesWithKey(boolean enabled, String signingKey) {
        return runtimePropertiesWithKey(enabled, List.of("weaver.files_read", "weaver.exec_disabled"), List.of("files.read"), signingKey);
    }

    private WeaverRuntimeProperties runtimePropertiesWithKey(boolean enabled, List<String> allowedCapabilities, List<String> toolAllowlist, String signingKey) {
        return new WeaverRuntimeProperties(
                enabled,
                null,
                null,
                null,
                null,
                null,
                List.of("weave-weaver-runtime", "weaver-group"),
                allowedCapabilities,
                List.of("weave-files-readonly"),
                toolAllowlist,
                null,
                signingKey,
                false,
                false,
                true,
                false);
    }

    private WeaverRuntimeProfileResponse copyProfile(
            WeaverRuntimeProfileResponse profile,
            String runtimeProfileHash,
            String signature,
            String expiresAt) {
        return new WeaverRuntimeProfileResponse(
                profile.enabled(), profile.posture(), profile.runtimeKind(), profile.runtimeProvider(), profile.modelProvider(),
                profile.toolProvider(), profile.generatedFrom(), profile.userRef(), profile.profileVersion(), runtimeProfileHash,
                signature, expiresAt, profile.revoked(), profile.revocationStatus(), profile.revocationGeneration(),
                profile.previousProfileHash(), profile.rollbackProfileHash(), profile.baselineProfile(), profile.containerImage(),
                profile.workspacePath(), profile.isolatedAgentDirectory(), profile.dockerNetworkMode(), profile.allowedCapabilities(),
                profile.pluginAllowlist(), profile.toolAllowlist(), profile.execEnabled(), profile.elevatedEnabled(),
                profile.auditRequired(), profile.forkRequired(), profile.channelProjection(), profile.credentialBrokerContract(),
                profile.auditPolicy(), profile.supportSafeProfileReceipt(), profile.approvalPolicy(), profile.secretPosture(),
                profile.isolationBoundary(), profile.memberImpact());
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
