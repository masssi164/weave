package com.massimotter.weave.backend.decisions.autoconfigure;

import com.massimotter.weave.backend.decisions.api.DecisionLedgerController;
import com.massimotter.weave.backend.decisions.application.DecisionLedgerService;
import com.massimotter.weave.backend.decisions.capability.DecisionsWorkspaceCapabilityConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.decisions.discovery.DecisionsDiscoveryConfiguration;

/** Installs implemented future-domain behavior; release inclusion remains explicit. */
@AutoConfiguration(afterName = "com.massimotter.weave.backend.chat.autoconfigure.ChatRuntimeAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
        DecisionsDiscoveryConfiguration.class,
        DecisionLedgerService.class,
        DecisionLedgerController.class,
        DecisionsWorkspaceCapabilityConfiguration.class
})
public class DecisionsRuntimeAutoConfiguration {}
