package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.discovery.api.PlatformController;
import com.massimotter.weave.backend.discovery.application.PlatformContractService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;

/** Platform endpoints assemble installed domain contributions without provider construction. */
@AutoConfiguration(after = CoreWorkspaceCapabilityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PlatformContractProperties.class)
@Import({PlatformContractService.class, PlatformController.class})
public class CorePlatformAutoConfiguration {}
