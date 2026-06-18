package com.massimotter.weave.backend.domainfacade;

import java.util.Map;

/** Canonical support-safe operation decision returned before any provider adapter may be called. */
public record NonChatDomainFacadeOperationDecision(
        String domain,
        String operation,
        boolean allowed,
        boolean providerAccessAllowed,
        boolean audited,
        boolean dryRun,
        SupportSafeFacadeError error,
        String reason,
        String canonicalObjectRef,
        String provenanceRef,
        Map<String, Object> supportSafeDiagnostics) {}
