package com.massimotter.weave.backend.portability.autoconfigure;

import com.massimotter.weave.backend.portability.api.MigrationController;
import com.massimotter.weave.backend.portability.application.MigrationDryRunService;
import com.massimotter.weave.backend.portability.application.MigrationApplyGateService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/** Installs existing future behavior without changing product readiness or release inclusion. */
@org.springframework.context.annotation.PropertySource("classpath:weave/domain/portability-defaults.properties")
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({com.massimotter.weave.backend.portability.config.MigrationToolkitProperties.class})
@Import({com.massimotter.weave.backend.portability.config.PortabilityAdminConfiguration.class,
        com.massimotter.weave.backend.portability.application.ProviderReplacementDryRunService.class,
        com.massimotter.weave.backend.portability.api.ProviderReplacementController.class,MigrationController.class, MigrationDryRunService.class, MigrationApplyGateService.class})
public class PortabilityRuntimeAutoConfiguration {}
