package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.application.RuntimeProfileIssuanceService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigner;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the lifecycle graph only when every authoritative control-plane adapter is present. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "'${weave.agent-runtime.storage.mode:disabled}' == 'jdbc'"
                + " && '${weave.agent-runtime.workload-identity.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.policy.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.profile-signing.enabled:false}' == 'true'")
@ConditionalOnBean(RuntimeStateStoreAdmin.class)
public class AgentRuntimeAdminConfiguration {

    @Bean
    RuntimeProfileIssuanceService runtimeProfileIssuanceService(
            RuntimePolicyAuthority policy,
            RuntimeProfileSigner signer,
            RuntimeProfileRepository profiles) {
        return new RuntimeProfileIssuanceService(policy, signer, profiles, Clock.systemUTC());
    }

    @Bean
    AgentRuntimeAdminService agentRuntimeAdminService(
            RuntimePersonDirectory people,
            RuntimePolicyAuthority policy,
            AgentRuntimeControlService control,
            RuntimeProfileIssuanceService profileIssuance,
            RuntimeCellRepository cells,
            RuntimeCommandRepository commands,
            RuntimeProfileRepository profiles,
            RuntimeWorkloadIdentityAdmin workloadIdentities,
            RuntimeStateStoreAdmin runtimeState) {
        return new AgentRuntimeAdminService(
                people, policy, control, profileIssuance, cells, commands, profiles,
                workloadIdentities, runtimeState, Clock.systemUTC());
    }
}
