package com.massimotter.weave.backend.integrations.autoconfigure;

import com.massimotter.weave.backend.integrations.api.ConnectorController;
import com.massimotter.weave.backend.integrations.api.InteropController;
import com.massimotter.weave.backend.integrations.application.ConnectorRuntimeService;
import com.massimotter.weave.backend.integrations.application.InteropGatewayService;
import com.massimotter.weave.backend.integrations.provider.slack.NoopSlackSecretResolver;
import com.massimotter.weave.backend.integrations.provider.slack.SlackSignatureVerifier;
import com.massimotter.weave.backend.integrations.config.IntegrationProviderRegistryConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.integrations.discovery.IntegrationsDiscoveryConfiguration;

/** Installs existing future behavior without changing product readiness or release inclusion. */
@org.springframework.context.annotation.PropertySource("classpath:weave/domain/integrations-defaults.properties")
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({com.massimotter.weave.backend.integrations.config.ConnectorRuntimeProperties.class, com.massimotter.weave.backend.integrations.config.InteropGatewayProperties.class})
@Import({
        IntegrationsDiscoveryConfiguration.class,ConnectorController.class, InteropController.class, ConnectorRuntimeService.class, InteropGatewayService.class, NoopSlackSecretResolver.class, SlackSignatureVerifier.class, IntegrationProviderRegistryConfiguration.class})
public class IntegrationsRuntimeAutoConfiguration {}
