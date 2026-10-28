package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.spaces.adapter.JpaSpaceAccessAdapter;
import com.massimotter.weave.backend.spaces.adapter.JpaSpaceMembershipAdminAdapter;
import com.massimotter.weave.backend.spaces.adapter.JpaSpaceProvisioningAdapter;
import com.massimotter.weave.backend.spaces.application.SpaceRelationshipUserService;
import com.massimotter.weave.backend.spaces.application.SpaceUserApiService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.massimotter.weave.backend.spaces.application.SpaceAdminApiService;
import com.massimotter.weave.backend.spaces.application.DefaultSpaceBootstrapService;
import com.massimotter.weave.backend.spaces.api.SpacesAdminController;
import com.massimotter.weave.backend.spaces.api.SpacesUserController;
import com.massimotter.weave.backend.spaces.api.SpacesRelationshipsUserController;
import com.massimotter.weave.backend.spaces.api.DefaultSpaceBootstrapController;
import com.massimotter.weave.backend.spaces.bootstrap.DefaultSpaceBootstrapCredential;
import com.massimotter.weave.backend.spaces.config.DefaultSpaceBootstrapProperties;

/** Foundational membership authority and shared projections over installed domain contributions. */
@AutoConfiguration(after = {CorePersistenceAutoConfiguration.class, CoreSecurityAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(DefaultSpaceBootstrapProperties.class)
@Import({JpaSpaceAccessAdapter.class, JpaSpaceMembershipAdminAdapter.class,
        JpaSpaceProvisioningAdapter.class, SpaceUserApiService.class, SpaceRelationshipUserService.class,
        SpaceAdminApiService.class, DefaultSpaceBootstrapService.class,
        SpacesAdminController.class, SpacesUserController.class, SpacesRelationshipsUserController.class,
        DefaultSpaceBootstrapController.class, DefaultSpaceBootstrapCredential.class})
public class CoreSpaceAutoConfiguration {}
