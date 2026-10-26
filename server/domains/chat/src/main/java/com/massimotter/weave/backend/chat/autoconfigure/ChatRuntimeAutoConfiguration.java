package com.massimotter.weave.backend.chat.autoconfigure;

import com.massimotter.weave.backend.chat.api.ChatController;
import com.massimotter.weave.backend.chat.api.matrix.MatrixClientServerProjectionController;
import com.massimotter.weave.backend.chat.application.ChatConversationAdmissionService;
import com.massimotter.weave.backend.chat.application.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.application.ChatProviderBindingGate;
import com.massimotter.weave.backend.chat.application.ChatProviderReplacementService;
import com.massimotter.weave.backend.chat.application.ChatProviderStatus;
import com.massimotter.weave.backend.chat.application.ChatQuarantineReconciliationService;
import com.massimotter.weave.backend.chat.application.DurableChatSpaceAccess;
import com.massimotter.weave.backend.chat.application.matrix.MatrixDeviceProofService;
import com.massimotter.weave.backend.chat.application.matrix.MatrixE2eeStateService;
import com.massimotter.weave.backend.chat.application.matrix.MatrixFacadeClientStateService;
import com.massimotter.weave.backend.chat.provider.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.chat.capability.ChatWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.chat.config.CanonicalChatPersistenceConfiguration;
import com.massimotter.weave.backend.chat.config.ChatBindingBootstrapConfiguration;
import com.massimotter.weave.backend.chat.config.ChatE2eProofProperties;
import com.massimotter.weave.backend.chat.config.ChatE2eProofSecurityConfiguration;
import com.massimotter.weave.backend.chat.config.ChatProviderHealthConfiguration;
import com.massimotter.weave.backend.chat.config.ChatProviderRegistryConfiguration;
import com.massimotter.weave.backend.chat.config.ChatRuntimeConfiguration;
import com.massimotter.weave.backend.chat.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.chat.config.MatrixApplicationServiceSecurityConfiguration;
import com.massimotter.weave.backend.chat.config.MatrixChatProperties;
import com.massimotter.weave.backend.chat.e2e.ChatE2eCallbackReplayController;
import com.massimotter.weave.backend.chat.e2e.ChatE2eCallbackReplayTap;
import com.massimotter.weave.backend.chat.e2e.ChatE2eProviderProofController;
import com.massimotter.weave.backend.chat.e2e.MatrixE2eePathProofController;
import com.massimotter.weave.backend.chat.persistence.CanonicalChatJpaAuthority;
import com.massimotter.weave.backend.chat.persistence.JpaMatrixFacadeClientStateStore;
import com.massimotter.weave.backend.chat.provider.synapse.ChatProviderPortAdapterResolver;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceController;
import com.massimotter.weave.backend.chat.spaces.ChatSpaceRelationshipContribution;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.chat.discovery.ChatDiscoveryConfiguration;

/** Installs the actual Chat serving graph independently of App component scanning. */
@AutoConfiguration(afterName = {
        "com.massimotter.weave.chat.autoconfigure.ChatPersistenceAutoConfiguration",
        "com.massimotter.weave.chat.autoconfigure.MatrixE2eeStoreAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({
        ChatRuntimeProperties.class,
        MatrixChatProperties.class,
        ChatE2eProofProperties.class
})
@Import({
        ChatDiscoveryConfiguration.class,
        ChatController.class,
        MatrixClientServerProjectionController.class,
        ChatConversationAdmissionService.class,
        ChatDomainFacadeService.class,
        ChatProviderBindingGate.class,
        ChatProviderReplacementService.class,
        ChatProviderStatus.class,
        ChatQuarantineReconciliationService.class,
        DurableChatSpaceAccess.class,
        MatrixDeviceProofService.class,
        MatrixE2eeStateService.class,
        MatrixFacadeClientStateService.class,
        ChatWorkspaceCapabilityConfiguration.class,
        CanonicalChatPersistenceConfiguration.class,
        ChatBindingBootstrapConfiguration.class,
        ChatE2eProofSecurityConfiguration.class,
        ChatProviderHealthConfiguration.class,
        ChatProviderRegistryConfiguration.class,
        ChatRuntimeConfiguration.class,
        MatrixApplicationServiceSecurityConfiguration.class,
        ChatE2eCallbackReplayController.class,
        ChatE2eCallbackReplayTap.class,
        ChatE2eProviderProofController.class,
        MatrixE2eePathProofController.class,
        CanonicalChatJpaAuthority.class,
        JpaMatrixFacadeClientStateStore.class,
        MatrixProtocolCoreService.class,
        ChatProviderPortAdapterResolver.class,
        MatrixApplicationServiceController.class,
        ChatSpaceRelationshipContribution.class
})
public class ChatRuntimeAutoConfiguration {}
