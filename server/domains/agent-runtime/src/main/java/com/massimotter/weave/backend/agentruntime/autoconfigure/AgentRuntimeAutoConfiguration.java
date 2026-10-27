package com.massimotter.weave.backend.agentruntime.autoconfigure;

import com.massimotter.weave.backend.agentruntime.api.AgentRuntimeAdminController;
import com.massimotter.weave.backend.agentruntime.api.AgentRuntimeAdminExceptionHandler;
import com.massimotter.weave.backend.agentruntime.api.AgentRuntimeErrorResponseWriter;
import com.massimotter.weave.backend.agentruntime.api.AgentRuntimeProfileController;
import com.massimotter.weave.backend.agentruntime.capability.AgentRuntimeWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimeAdminConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimeAdminSecurityConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimeJwtConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimePolicyConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimeProfileConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimeProfileSecurityConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimeProfileSigningConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimeStateStoreConfiguration;
import com.massimotter.weave.backend.agentruntime.config.AgentRuntimeWorkloadIdentityConfiguration;
import com.massimotter.weave.backend.agentruntime.persistence.JpaRuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.persistence.JpaRuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.persistence.JpaRuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.persistence.JpaRuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.persistence.RuntimeStateJpaAuthority;
import com.massimotter.weave.agentruntime.autoconfigure.AgentRuntimePersistenceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.agentruntime.discovery.AgentRuntimeDiscoveryConfiguration;

/** Actual future control-plane graph; schema and operator contexts construct no serving providers. */
@AutoConfiguration(after = AgentRuntimePersistenceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
        AgentRuntimeDiscoveryConfiguration.class,
        AgentRuntimeAdminController.class,
        AgentRuntimeAdminExceptionHandler.class,
        AgentRuntimeErrorResponseWriter.class,
        AgentRuntimeProfileController.class,
        AgentRuntimeWorkspaceCapabilityConfiguration.class,
        AgentRuntimeAdminConfiguration.class,
        AgentRuntimeAdminSecurityConfiguration.class,
        AgentRuntimeJwtConfiguration.class,
        AgentRuntimePolicyConfiguration.class,
        AgentRuntimeProfileConfiguration.class,
        AgentRuntimeProfileSecurityConfiguration.class,
        AgentRuntimeProfileSigningConfiguration.class,
        AgentRuntimeStateStoreConfiguration.class,
        AgentRuntimeWorkloadIdentityConfiguration.class,
        JpaRuntimeCellRepository.class,
        JpaRuntimeCommandRepository.class,
        JpaRuntimeGovernanceRepository.class,
        JpaRuntimeProfileRepository.class,
        RuntimeStateJpaAuthority.class
})
public class AgentRuntimeAutoConfiguration {}
