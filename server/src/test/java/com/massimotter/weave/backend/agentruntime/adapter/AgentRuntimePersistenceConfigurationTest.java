package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.AgentRuntimeStateStoreProperties;
import com.massimotter.weave.backend.config.AgentRuntimeWorkloadIdentityProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AgentRuntimePersistenceConfigurationTest {

    @Test
    void canonicalConfigurationHasNoRuntimeStorageSelector() throws Exception {
        String application = new String(
                getClass().getResourceAsStream("/application-base.yml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(application)
                .doesNotContain("agent-runtime.storage.mode")
                .doesNotContain("WEAVE_AGENT_RUNTIME_STORAGE_MODE")
                .contains("state-store:")
                .contains("endpoint: ${WEAVE_AGENT_RUNTIME_STATE_S3_ENDPOINT:}")
                .contains("bucket: ${WEAVE_AGENT_RUNTIME_STATE_S3_BUCKET:}")
                .contains("credential-ref: ${WEAVE_AGENT_RUNTIME_STATE_S3_CREDENTIAL_REF:}")
                .contains("access-key-file: ${WEAVE_AGENT_RUNTIME_STATE_S3_ACCESS_KEY_FILE:}")
                .contains("secret-key-file: ${WEAVE_AGENT_RUNTIME_STATE_S3_SECRET_KEY_FILE:}")
                .doesNotContain("WEAVE_AGENT_RUNTIME_STATE_S3_ACCESS_KEY:")
                .doesNotContain("WEAVE_AGENT_RUNTIME_STATE_S3_SECRET_KEY:");
    }

    @Test
    void stateObjectStoreFailsClosedWithoutRequiredCoordinates() {
        AgentRuntimeStateStoreProperties properties = new AgentRuntimeStateStoreProperties();

        assertThatThrownBy(properties::requiredEndpoint)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
        assertThatThrownBy(properties::requiredBucket)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    void workloadClientDefaultsUseCanonicalMcpScope() {
        AgentRuntimeWorkloadIdentityProperties properties = new AgentRuntimeWorkloadIdentityProperties();

        assertThat(properties.optionalClientScopes())
                .contains("agent-runtime.profile.read", "mcp.tools")
                .doesNotContain("mcp:tools");
    }
}
