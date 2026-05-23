package com.massimotter.weave.backend.context.authz;

/**
 * Internal policy point for Context/Space authorization.
 *
 * Implementations must fail closed and evaluate Weave Context identities, not raw provider bindings.
 */
public interface ContextAuthorizationPort {

    ContextAuthorizationDecision check(ContextAuthorizationRequest request);
}
