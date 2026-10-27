package com.massimotter.weave.backend.office.autoconfigure;

import com.massimotter.weave.backend.office.api.OfficeController;
import com.massimotter.weave.backend.office.application.OfficeFacadeService;
import com.massimotter.weave.backend.office.capability.OfficeWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.office.provider.DisabledOfficeProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.office.discovery.OfficeDiscoveryConfiguration;

/** Owns existing future feature behavior; release inclusion is an explicit composition decision. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
        OfficeDiscoveryConfiguration.class,OfficeController.class, OfficeFacadeService.class, OfficeWorkspaceCapabilityConfiguration.class, DisabledOfficeProvider.class})
public class OfficeRuntimeAutoConfiguration {}
