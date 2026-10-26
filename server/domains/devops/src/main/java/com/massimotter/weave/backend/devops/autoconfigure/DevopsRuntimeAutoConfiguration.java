package com.massimotter.weave.backend.devops.autoconfigure;

import com.massimotter.weave.backend.devops.api.DevopsController;
import com.massimotter.weave.backend.devops.application.DevopsFacadeService;
import com.massimotter.weave.backend.devops.capability.DevopsWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.devops.config.DevopsProviderConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.devops.discovery.DevopsDiscoveryConfiguration;

/** Owns existing future feature behavior; release inclusion is an explicit composition decision. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
        DevopsDiscoveryConfiguration.class,DevopsController.class, DevopsFacadeService.class, DevopsWorkspaceCapabilityConfiguration.class, DevopsProviderConfiguration.class})
public class DevopsRuntimeAutoConfiguration {}
