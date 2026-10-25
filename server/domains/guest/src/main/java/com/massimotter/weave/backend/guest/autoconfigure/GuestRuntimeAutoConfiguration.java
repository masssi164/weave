package com.massimotter.weave.backend.guest.autoconfigure;

import com.massimotter.weave.backend.guest.api.GuestAccessController;
import com.massimotter.weave.backend.guest.application.GuestAccessService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/** Installs existing future behavior without changing product readiness or release inclusion. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({com.massimotter.weave.backend.guest.config.GuestAccessProperties.class})
@Import({GuestAccessController.class, GuestAccessService.class})
public class GuestRuntimeAutoConfiguration {}
