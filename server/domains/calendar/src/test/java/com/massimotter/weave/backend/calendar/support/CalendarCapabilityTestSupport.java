package com.massimotter.weave.backend.calendar.support;

import com.massimotter.weave.backend.calendar.capability.CalendarWorkspaceCapabilities;
import com.massimotter.weave.backend.calendar.capability.CalendarWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import java.util.List;
import org.mockito.Mockito;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;

/** Real Calendar declaration and Core policy only; no all-domain installation fixture. */
public final class CalendarCapabilityTestSupport {
    private CalendarCapabilityTestSupport() {}

    public static WorkspaceCapabilityService service(OAuth2ResourceServerProperties resourceServer,
            boolean enabled, WorkspaceCapabilityReadiness readiness) {
        var contribution = CalendarWorkspaceCapabilities.contribution(
                new CalendarWorkspaceCapabilityProperties(new CapabilitySettings(enabled, null, readiness)),
                Mockito.mock(ProviderCapabilityHealthService.class));
        return new WorkspaceCapabilityService(resourceServer, new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null), OrganizationIdentityContextResolver.defaults(), List.of(contribution));
    }
}
