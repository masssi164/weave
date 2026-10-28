package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.capability.api.AdminWorkspaceController;
import com.massimotter.weave.backend.home.api.WorkspaceController;
import com.massimotter.weave.backend.home.application.WorkspaceHomeService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/** Shared workspace transport and installed-domain daily-work composition. */
@AutoConfiguration(after = CoreWorkspaceCapabilityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({WorkspaceController.class, AdminWorkspaceController.class, WorkspaceHomeService.class})
public class CoreHomeAutoConfiguration {}
