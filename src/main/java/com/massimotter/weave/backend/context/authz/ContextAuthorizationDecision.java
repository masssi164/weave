package com.massimotter.weave.backend.context.authz;

/**
 * Deterministic authorization result for Context/Space permission checks.
 */
public record ContextAuthorizationDecision(boolean allowed, String reason) {

    public static ContextAuthorizationDecision allow(String reason) {
        return new ContextAuthorizationDecision(true, reason);
    }

    public static ContextAuthorizationDecision deny(String reason) {
        return new ContextAuthorizationDecision(false, reason);
    }
}
