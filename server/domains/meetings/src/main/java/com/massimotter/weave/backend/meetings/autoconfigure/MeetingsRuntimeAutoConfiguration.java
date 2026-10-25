package com.massimotter.weave.backend.meetings.autoconfigure;

import com.massimotter.weave.backend.meetings.api.MeetingCapsuleController;
import com.massimotter.weave.backend.meetings.application.MeetingCapsuleService;
import com.massimotter.weave.backend.meetings.capability.MeetingsWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.meetings.config.LiveKitSfuProviderProperties;
import com.massimotter.weave.backend.meetings.config.MeetingsProviderRegistryConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/** Installs implemented future-domain behavior; release inclusion remains explicit. */
@AutoConfiguration(afterName = "com.massimotter.weave.backend.chat.autoconfigure.ChatRuntimeAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@org.springframework.boot.context.properties.EnableConfigurationProperties(
        LiveKitSfuProviderProperties.class)
@Import({
        MeetingCapsuleService.class,
        MeetingCapsuleController.class,
        MeetingsWorkspaceCapabilityConfiguration.class,
        MeetingsProviderRegistryConfiguration.class
})
public class MeetingsRuntimeAutoConfiguration {}
