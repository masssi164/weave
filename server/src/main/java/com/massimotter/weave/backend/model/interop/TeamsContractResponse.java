package com.massimotter.weave.backend.model.interop;

import java.util.List;

public record TeamsContractResponse(
        boolean enabled,
        boolean gatedBehindSlackHardening,
        String status,
        List<String> requiredBeforeImplementation,
        List<String> degradedStates) {
}
