package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProvisioningPlan;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRuntimePolicyAuthorityTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-07-20T10:00:00Z");
    private static final String PERSON = "acct_" + "a".repeat(32);

    @TempDir
    Path temporary;

    @Test
    void expandsOnlyServerOwnedReferencesAndIssuesAWorkloadOnlyProfile() throws IOException {
        Path policyFile = writePolicy(validPolicy());
        FileRuntimePolicyAuthority policy = authority(policyFile);
        RuntimePersonDirectory.ResolvedRuntimePerson person = new RuntimePersonDirectory.ResolvedRuntimePerson(
                "tenant-default", PERSON,
                new RuntimeMemberBinding("https://auth.weave.test/realms/weave", "keycloak-user-1"));

        RuntimeProvisioningPlan plan = policy.provisioningPlan(person);
        RuntimeCell cell = RuntimeCell.provisioning(
                person.organizationRef(), person.personRef(), person.memberBinding(), "cell:example",
                new RuntimeWorkloadBinding(
                        "https://auth.weave.test/realms/weave",
                        "service-account-weaver-cell-example",
                        "weaver-cell-example",
                        RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                        "credentialref://weave/runtime/cell-example/workload"),
                "sha256:" + "1".repeat(64),
                plan.workspaceRevision(), plan.workspaceManifestRef(), plan.runtimeStateStoreRef(),
                "audit:provision", ISSUED_AT);

        RuntimeProfile profile = policy.runtimeProfile(
                cell, "rp_example", ISSUED_AT, ISSUED_AT.plus(policy.profileTtl()));

        assertThat(plan.workspaceManifestRef())
                .isEqualTo("webdav-manifest://tenant-default/" + PERSON + "/current");
        assertThat(plan.runtimeStateStoreRef())
                .isEqualTo("runtime-state://tenant-default/" + PERSON + "/state");
        assertThat(plan.authenticationMethod())
                .isEqualTo(RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT);
        assertThat(profile.zeroDurableCellBytes()).isTrue();
        assertThat(profile.matrix().encryptionRequired()).isTrue();
        assertThat(profile.workloadIdentity().subject())
                .isEqualTo("service-account-weaver-cell-example");
        assertThat(profile.mcp().servers()).singleElement().satisfies(server -> {
            assertThat(server.endpoint()).isEqualTo("https://api.weave.test/mcp");
            assertThat(server.requestedResource()).isEqualTo("https://api.weave.test/mcp");
            assertThat(server.extensionId())
                    .isEqualTo("io.modelcontextprotocol/oauth-client-credentials");
            assertThat(server.grantType()).isEqualTo("client_credentials");
            assertThat(server.credentialRef())
                    .isEqualTo("credentialref://weave/runtime/cell:example/weaver-cell-example/mcp");
        });
    }

    @Test
    void rejectsHumanOrInsecureMcpPolicyAndUnknownInput() throws IOException {
        Path insecure = writePolicy(validPolicy().replace(
                "https://api.weave.test/mcp", "http://api.weave.test/mcp"));
        assertThatThrownBy(() -> authority(insecure).profileTtl())
                .isInstanceOf(RuntimePolicyException.class)
                .hasMessageContaining("HTTPS URI");

        Path unknown = writePolicy(validPolicy().replace(
                "\"schemaVersion\":", "\"unexpected\":true,\"schemaVersion\":"));
        assertThatThrownBy(() -> authority(unknown).profileTtl())
                .isInstanceOf(RuntimePolicyException.class)
                .hasMessageContaining("invalid or unreadable");
    }

    @Test
    void rejectsUnboundedLifetimeAndUnsupportedTemplates() throws IOException {
        Path excessive = writePolicy(validPolicy().replace(
                "\"profileTtlSeconds\":120", "\"profileTtlSeconds\":301"));
        assertThatThrownBy(() -> authority(excessive).profileTtl())
                .isInstanceOf(RuntimePolicyException.class)
                .hasMessageContaining("exceeds");

        Path template = writePolicy(validPolicy().replace(
                "{personRef}/current", "{email}/current"));
        assertThatThrownBy(() -> authority(template).profileTtl())
                .isInstanceOf(RuntimePolicyException.class)
                .hasMessageContaining("unsupported placeholder");
    }

    private FileRuntimePolicyAuthority authority(Path policyFile) {
        return new FileRuntimePolicyAuthority(policyFile, new ObjectMapper(), Duration.ofMinutes(5));
    }

    private Path writePolicy(String content) throws IOException {
        Path policy = temporary.resolve("runtime-policy-" + Integer.toUnsignedString(content.hashCode()) + ".json");
        Files.writeString(policy, content);
        return policy;
    }

    private static String validPolicy() {
        return """
                {
                  "schemaVersion":"weave.runtime-policy/v1",
                  "profileTtlSeconds":120,
                  "workspace":{
                    "revision":"workspace:v1",
                    "manifestRefTemplate":"webdav-manifest://{organizationRef}/{personRef}/current",
                    "runtimeStateStoreRefTemplate":"runtime-state://{organizationRef}/{personRef}/state"
                  },
                  "modelPolicy":{
                    "allowedProviders":["provider-neutral"],
                    "allowedModels":["model-default"],
                    "fallback":[],
                    "maximumContextTokens":32768,
                    "dataRegion":"eu"
                  },
                  "matrix":{
                    "accountRefTemplate":"matrix-account://{personRef}",
                    "homeserverRefTemplate":"matrix-homeserver://default",
                    "credentialRefTemplate":"credentialref://weave/runtime/{cellRef}/matrix",
                    "allowedRooms":[],
                    "autoJoin":"off"
                  },
                  "mcp":{
                    "servers":[{
                      "serverRef":"weave-mcp",
                      "endpoint":"https://api.weave.test/mcp",
                      "requestedResource":"https://api.weave.test/mcp",
                      "requiredScopes":["mcp.tools"],
                      "credentialRefTemplate":"credentialref://weave/runtime/{cellRef}/{workloadClientId}/mcp",
                      "allowedToolClasses":["calendar.read"]
                    }],
                    "visibleToolClasses":["calendar.read"]
                  },
                  "approvals":{
                    "pluginRouting":{"enabled":true,"mode":"same-chat","targetRefs":[]},
                    "execMode":"ask",
                    "persistentTrustPolicy":"bounded"
                  },
                  "sandbox":{
                    "mode":"required",
                    "networkPolicy":"allowlist",
                    "allowedNetworkTargets":["api.weave.test"],
                    "filesystemPolicy":"workspace-only",
                    "approvedMountRefs":[]
                  },
                  "automation":{"heartbeatEnabled":false,"schedulePolicy":"disabled"}
                }
                """;
    }
}
