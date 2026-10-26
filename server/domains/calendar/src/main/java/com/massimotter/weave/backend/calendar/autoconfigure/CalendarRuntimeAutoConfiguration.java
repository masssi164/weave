package com.massimotter.weave.backend.calendar.autoconfigure;

import com.massimotter.weave.backend.calendar.api.CalendarController;
import com.massimotter.weave.backend.calendar.api.CalendarDavSetupController;
import com.massimotter.weave.backend.calendar.api.CalendarUserController;
import com.massimotter.weave.backend.calendar.api.dav.CalDavCalendarController;
import com.massimotter.weave.backend.calendar.application.CalendarFacadeService;
import com.massimotter.weave.backend.calendar.application.CalendarProviderBindingReadiness;
import com.massimotter.weave.backend.calendar.application.CalendarUserApiService;
import com.massimotter.weave.backend.calendar.capability.CalendarWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.calendar.config.CalendarBindingBootstrapConfiguration;
import com.massimotter.weave.backend.calendar.config.CalendarCalDavConfiguration;
import com.massimotter.weave.backend.calendar.config.CalendarCalDavProperties;
import com.massimotter.weave.backend.calendar.config.CalendarMcpSecurityConfiguration;
import com.massimotter.weave.backend.calendar.config.CalendarProviderHealthConfiguration;
import com.massimotter.weave.backend.calendar.config.CalendarProviderRegistryConfiguration;
import com.massimotter.weave.backend.calendar.persistence.NativeCalendarProviderAdapter;
import com.massimotter.weave.backend.calendar.persistence.NativeCalendarRelationalStore;
import com.massimotter.weave.backend.calendar.spaces.CalendarDefaultSpacePreparationContribution;
import com.massimotter.weave.backend.calendar.spaces.CalendarSpaceRelationshipContribution;
import com.massimotter.weave.calendar.autoconfigure.CalendarPersistenceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.calendar.discovery.CalendarDiscoveryConfiguration;

/** Installs the actual Calendar serving graph without App scanning or live provider probes. */
@AutoConfiguration(after = CalendarPersistenceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(CalendarCalDavProperties.class)
@Import({
        CalendarDiscoveryConfiguration.class,
        CalendarController.class,
        CalendarDavSetupController.class,
        CalendarUserController.class,
        CalDavCalendarController.class,
        CalendarFacadeService.class,
        CalendarUserApiService.class,
        CalendarProviderBindingReadiness.class,
        NativeCalendarProviderAdapter.class,
        NativeCalendarRelationalStore.class,
        CalendarCalDavConfiguration.class,
        CalendarBindingBootstrapConfiguration.class,
        CalendarMcpSecurityConfiguration.class,
        CalendarProviderRegistryConfiguration.class,
        CalendarProviderHealthConfiguration.class,
        CalendarWorkspaceCapabilityConfiguration.class,
        CalendarDefaultSpacePreparationContribution.class,
        CalendarSpaceRelationshipContribution.class
})
public class CalendarRuntimeAutoConfiguration {}
