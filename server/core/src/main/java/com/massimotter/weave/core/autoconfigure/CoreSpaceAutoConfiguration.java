package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.spaces.adapter.JpaSpaceAccessAdapter;
import com.massimotter.weave.backend.spaces.adapter.JpaSpaceMembershipAdminAdapter;
import com.massimotter.weave.backend.spaces.adapter.JpaSpaceProvisioningAdapter;
import com.massimotter.weave.backend.spaces.application.SpaceRelationshipUserService;
import com.massimotter.weave.backend.spaces.application.SpaceUserApiService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/** Foundational membership authority and shared projections over installed domain contributions. */
@AutoConfiguration(after = {CorePersistenceAutoConfiguration.class, CoreSecurityAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({JpaSpaceAccessAdapter.class, JpaSpaceMembershipAdminAdapter.class,
        JpaSpaceProvisioningAdapter.class, SpaceUserApiService.class, SpaceRelationshipUserService.class})
public class CoreSpaceAutoConfiguration {}
