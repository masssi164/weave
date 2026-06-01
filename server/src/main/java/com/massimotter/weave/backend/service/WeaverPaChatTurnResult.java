package com.massimotter.weave.backend.service;

import java.util.Map;

public record WeaverPaChatTurnResult(
        boolean weaverReceived,
        boolean lmStudioResponseReceived,
        String answer,
        String modelRef,
        String providerRef,
        String auditRef,
        Map<String, Object> supportSafeEvidence) {
}
