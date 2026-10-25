package com.massimotter.weave.backend.calendar.spaces;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContext;
import com.massimotter.weave.backend.service.calendar.CalendarProviderBindingReadiness;
import com.massimotter.weave.backend.spaces.preparation.DefaultSpacePreparationContribution;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class CalendarDefaultSpacePreparationContribution implements DefaultSpacePreparationContribution {
    private final CalendarProviderBindingReadiness readiness;
    public CalendarDefaultSpacePreparationContribution(CalendarProviderBindingReadiness readiness) { this.readiness = readiness; }
    @Override public String domain() { return "calendar"; }
    @Override public List<String> requiredCapabilities() { return List.of("calendar.read", "calendar.manage_events"); }
    @Override public boolean ready(WorkspaceCapabilitiesResponse snapshot) {
        return snapshot != null && DefaultSpacePreparationContribution.ready(snapshot.calendar());
    }
    @Override public Optional<Runnable> captureAuthorityRecheck(OrganizationIdentityContext owner) {
        var binding = readiness.require(owner.organizationId());
        return Optional.of(() -> readiness.requireUnchanged(owner.organizationId(), binding));
    }
}
