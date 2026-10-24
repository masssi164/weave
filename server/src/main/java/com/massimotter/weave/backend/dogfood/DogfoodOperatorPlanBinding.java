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

    /** Private setup resumes from audited account/phase bindings; provider profile attributes are not authority. */
    public com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ManagedTestProgress managedProgress(
            Request request, String organizationId, String intentRef, String slot) {
        var stages = new java.util.HashSet<String>();
        String account = null;
        for (String stage : com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.MANAGED_TEST_STEPS) {
            var known = events.findByTenantIdAndIdempotencyKey(organizationId, managedStepKey(intentRef, stage));
            if (known.isEmpty()) continue;
            var event = known.get(); var payload = JSON.readTree(event.payloadJson());
            require(actor(request).equals(event.actorRef()) && "dogfood-prepare-members".equals(event.sourceRef())
                    && "workspace-default".equals(event.contextId()) && AuditAction.DOGFOOD_PREPARATION_VERIFIED.name().equals(event.action())
                    && payload.isObject() && payload.size() == 4 && intentRef.equals(payload.path("intentRef").asString())
                    && slot.equals(payload.path("slot").asString()) && stage.equals(payload.path("stage").asString())
                    && payload.path("accountRef").asString().matches("acct_[0-9a-f]{32}"), "operator-reference-conflict");
            String observed = payload.path("accountRef").asString();
            require(account == null || account.equals(observed), "operator-reference-conflict");
            account = observed; stages.add(stage);
        }
        require(stages.isEmpty() || stages.contains("subject"), "operator-reference-conflict");
        return new com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ManagedTestProgress(account, stages);
    }

    public void managedStep(Request request, String organizationId, String intentRef, String slot,
            com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ManagedTestStep step) {
        require(com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.MANAGED_TEST_STEPS.contains(step.stage()),
                "operator-reference-conflict");
        String account = com.massimotter.weave.backend.identity.IdentityReferences.accountId(request.plan().issuerUrl(), step.subject());
        var previous = managedProgress(request, organizationId, intentRef, slot);
        require(("subject".equals(step.stage()) || previous.accountRef() != null)
                && (previous.accountRef() == null || previous.accountRef().equals(account)), "operator-reference-conflict");
        observe(request, organizationId, AuditAction.DOGFOOD_PREPARATION_VERIFIED, managedStepKey(intentRef, step.stage()),
                Map.of("intentRef", intentRef, "slot", slot, "stage", step.stage(), "accountRef", account));
    }

    private static String managedStepKey(String intentRef, String stage) { return "dogfood-managed-" + intentRef + "-" + stage; }

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
