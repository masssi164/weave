package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Shared gate for non-chat domain facades.
 *
 * Files, Calendar, and Boards keep domain-specific services and nouns, but they use this pattern before calling any
 * provider adapter: capability/policy, Space authorization, audited write/delete decision, canonical mapping refs, and
 * support-safe diagnostics.
 */
public final class NonChatDomainFacadeOperationGuard {

    private final CanonicalDomainFacade facade;
    private final ContextAuthorizationPort contextAuthorization;
    private final AuditEventPublisher auditEvents;
    private final Clock clock;

    public NonChatDomainFacadeOperationGuard(
            CanonicalDomainFacade facade,
            ContextAuthorizationPort contextAuthorization,
            AuditEventPublisher auditEvents,
            Clock clock) {
        this.facade = java.util.Objects.requireNonNull(facade, "facade must not be null");
        this.contextAuthorization = java.util.Objects.requireNonNull(contextAuthorization, "contextAuthorization must not be null");
        this.auditEvents = java.util.Objects.requireNonNull(auditEvents, "auditEvents must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public NonChatDomainFacadeOperationDecision decide(Jwt jwt, NonChatDomainFacadeOperationRequest request) {
        CanonicalCapabilityDecision capability = facade.evaluateCapability(jwt, request.capability(), request.operation());
        if (!capability.allowed()) {
            return decision(request, false, false, false, mapCapabilityError(capability.state()), capability.reason());
        }

        var contextDecision = contextAuthorization.check(new ContextAuthorizationRequest(
                request.tenantId(), request.contextId(), request.actorRef(), request.permission()));
        if (!contextDecision.allowed()) {
            return decision(request, false, false, false, SupportSafeFacadeError.CONTEXT_FORBIDDEN, "context_authorization_denied");
        }

        boolean audited = false;
        if (request.writeOrDelete()) {
            auditEvents.publish(new AuditEvent(
                    request.tenantId(),
                    request.contextId(),
                    request.actorRef(),
                    "domain-facade:" + facade.contract().domain(),
                    AuditAction.CONNECTOR_WRITE_ATTEMPTED,
                    Instant.now(clock),
                    facade.contract().domain() + ":" + request.operation() + ":" + request.canonicalObjectRef(),
                    AuditRedactionLevel.SUPPORT_SAFE,
                    auditPayload(request)));
            audited = true;
        }
        return decision(request, true, true, audited, null, request.dryRun() ? "dry_run_allowed" : "allowed");
    }

    private NonChatDomainFacadeOperationDecision decision(
            NonChatDomainFacadeOperationRequest request,
            boolean allowed,
            boolean providerAccessAllowed,
            boolean audited,
            SupportSafeFacadeError error,
            String reason) {
        Map<String, Object> diagnostics = auditPayload(request);
        diagnostics.put("allowed", allowed);
        diagnostics.put("providerAccessAllowed", providerAccessAllowed);
        diagnostics.put("audited", audited);
        diagnostics.put("error", error == null ? "none" : error.value());
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderPayloadsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.put("diagnosticsRedacted", true);
        return new NonChatDomainFacadeOperationDecision(
                facade.contract().domain(),
                request.operation(),
                allowed,
                providerAccessAllowed,
                audited,
                request.dryRun(),
                error,
                reason,
                request.canonicalObjectRef(),
                request.provenanceRef(),
                diagnostics);
    }

    private Map<String, Object> auditPayload(NonChatDomainFacadeOperationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("domain", facade.contract().domain());
        payload.put("operation", request.operation());
        payload.put("capability", request.capability());
        payload.put("canonicalObjectRef", request.canonicalObjectRef());
        payload.put("provenanceRef", request.provenanceRef());
        payload.put("dryRun", request.dryRun());
        payload.put("writeOrDelete", request.writeOrDelete());
        payload.put("policyEvaluatedBeforeProviderAccess", true);
        return payload;
    }

    private SupportSafeFacadeError mapCapabilityError(CanonicalMemberState state) {
        return switch (state) {
            case MISCONFIGURED -> SupportSafeFacadeError.NOT_CONFIGURED;
            case DEGRADED -> SupportSafeFacadeError.DEGRADED;
            case POLICY_BLOCKED -> SupportSafeFacadeError.POLICY_BLOCKED;
            case UNAVAILABLE -> SupportSafeFacadeError.UNAVAILABLE;
            case UNSUPPORTED -> SupportSafeFacadeError.UNSUPPORTED;
            case READY -> SupportSafeFacadeError.PROVIDER_FAILURE;
        };
    }
}
