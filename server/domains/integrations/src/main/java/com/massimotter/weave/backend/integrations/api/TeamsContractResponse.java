package com.massimotter.weave.backend.integrations.api;

import java.util.List;

public record TeamsContractResponse(
        boolean enabled,
        boolean gatedBehindSlackHardening,
        String status,
        List<String> requiredBeforeImplementation,
        List<String> degradedStates) {
}
