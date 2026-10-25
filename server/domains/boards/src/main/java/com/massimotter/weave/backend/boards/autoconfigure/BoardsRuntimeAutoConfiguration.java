package com.massimotter.weave.backend.boards.autoconfigure;

import com.massimotter.weave.backend.boards.api.BoardsController;
import com.massimotter.weave.backend.boards.application.BoardsFacadeService;
import com.massimotter.weave.backend.boards.capability.BoardsWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.boards.config.BoardsRuntimeConfiguration;
import com.massimotter.weave.backend.boards.config.BoardsProviderRegistryConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/** Owns existing future feature behavior; release inclusion is an explicit composition decision. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({BoardsController.class, BoardsFacadeService.class, BoardsWorkspaceCapabilityConfiguration.class, BoardsRuntimeConfiguration.class, BoardsProviderRegistryConfiguration.class})
public class BoardsRuntimeAutoConfiguration {}
