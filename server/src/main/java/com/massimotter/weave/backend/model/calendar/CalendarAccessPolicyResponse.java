package com.massimotter.weave.backend.model.calendar;

import java.util.List;

public record CalendarAccessPolicyResponse(
        CalendarAccessModelResponse accessModel,
        List<String> allowedScopes,
        List<String> deniedScopes,
        List<String> requiredBeforePrivateCalendars,
        boolean backendActorMayReadPrivateUserCalendars) {
}
