package com.massimotter.weave.backend.dogfood;

import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocol.*;

import com.massimotter.weave.backend.audit.*;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/** Immutable owner/plan binding in the existing append-only audit authority, not a new ledger. */
public class DogfoodOperatorPlanBinding {
    private final AuditEventJpaRepository events;
    private final AuditEventPublisher audit;

    public DogfoodOperatorPlanBinding(AuditEventJpaRepository events, AuditEventPublisher audit) {
        this.events = events;
        this.audit = audit;
    }

    @Transactional(timeout = 20)
    public void bind(Request request, String organizationId, String ownerAccountRef, String ownerMemberHandle) {
        String key = "dogfood-plan-" + request.plan().planId();
        Map<String, Object> payload = Map.of("plan", request.plan().planId(),
                "organizationOrigin", request.plan().organizationOrigin(), "apiBaseUrl", request.apiBaseUrl(),
                "issuerUrl", request.plan().issuerUrl(), "intendedOwnerAccountRef", ownerAccountRef,
                "intendedOwnerMemberHandle", ownerMemberHandle);
        observe(request, organizationId, AuditAction.DOGFOOD_PREPARATION_AUTHORIZED, key, payload);
    }

    /** Replaying the same invocation verifies the immutable audit event without rewriting its time. */
    @Transactional(timeout = 20)
    public void observe(Request request, String organizationId, AuditAction action, String key, Map<String, Object> payload) {
        var existing = events.findByTenantIdAndIdempotencyKey(organizationId, key);
        if (existing.isPresent()) {
            var event = existing.get();
            require(actor(request).equals(event.actorRef()) && "dogfood-prepare-members".equals(event.sourceRef())
                    && "workspace-default".equals(event.contextId()) && action.name().equals(event.action())
                    && JSON.valueToTree(payload).equals(JSON.readTree(event.payloadJson())), "operator-reference-conflict");
            return;
        }
        audit.publish(new AuditEvent(organizationId, "workspace-default", actor(request), "dogfood-prepare-members",
                action, Instant.now(), key, AuditRedactionLevel.SUPPORT_SAFE, payload));
    }
}
