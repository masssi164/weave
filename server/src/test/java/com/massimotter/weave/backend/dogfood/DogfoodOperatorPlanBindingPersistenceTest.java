package com.massimotter.weave.backend.dogfood;

import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocol.*;
import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocolTest.*;
import static org.assertj.core.api.Assertions.*;

import com.massimotter.weave.backend.audit.JpaAuditEventPublisher;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import org.junit.jupiter.api.Test;

class DogfoodOperatorPlanBindingPersistenceTest {
    @Test void durableOperatorAuthorityRetainsOwnerAndPlanAcrossResumeWithoutRewritingAudit() {
        var source = JpaTestDatabase.entityFirstDataSource("operator-plan-binding");
        var events = JpaTestDatabase.repository(source, AuditEventJpaRepository.class);
        var audit = JpaTestDatabase.transactional(source, new JpaAuditEventPublisher(events, JSON));
        var binding = JpaTestDatabase.transactional(source, new DogfoodOperatorPlanBinding(events, audit));
        Request first = request("invitations");
        String owner = "acct_" + "0".repeat(32), handle = "mem_" + "o".repeat(32);
        binding.bind(first, "org", owner, handle);
        var original = events.findByTenantIdAndIdempotencyKey("org", "dogfood-plan-" + PLAN).orElseThrow();
        Request resume = new Request(first.schemaVersion(), first.operation(), first.environment(), "a".repeat(32),
                "0".repeat(40), first.sourceTree(), first.specificationCommit(), first.apiBaseUrl(),
                new Plan(first.plan().schemaVersion(), PLAN, "a".repeat(32), first.plan().organizationOrigin(), first.plan().apiOrigin(),
                        first.plan().issuerUrl(), first.plan().spaceRef(), first.plan().members()), null, null);
        binding.bind(resume, "org", owner, handle);
        assertThat(events.findAll()).hasSize(1);
        var unchanged = events.findByTenantIdAndIdempotencyKey("org", "dogfood-plan-" + PLAN).orElseThrow();
        assertThat(unchanged.payloadJson()).isEqualTo(original.payloadJson());
        assertThat(unchanged.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(unchanged.actorRef()).isEqualTo(actor(first));
        assertThatThrownBy(() -> binding.bind(resume, "org", "acct_" + "1".repeat(32), handle)).isInstanceOf(Blocked.class);
        assertThat(events.findAll()).hasSize(1);
        binding.observe(first, "org", com.massimotter.weave.backend.audit.AuditAction.DOGFOOD_PREPARATION_VERIFIED,
                "invocation-" + first.nonce(), java.util.Map.of("result", "VERIFIED"));
        binding.observe(first, "org", com.massimotter.weave.backend.audit.AuditAction.DOGFOOD_PREPARATION_VERIFIED,
                "invocation-" + first.nonce(), java.util.Map.of("result", "VERIFIED"));
        assertThat(events.findAll()).hasSize(2);
        assertThatThrownBy(() -> binding.observe(first, "org", com.massimotter.weave.backend.audit.AuditAction.DOGFOOD_PREPARATION_VERIFIED,
                "invocation-" + first.nonce(), java.util.Map.of("result", "CONFLICT"))).isInstanceOf(Blocked.class);
        assertThat(unchanged.payloadJson()).doesNotContain("Actual owner", "email", "password", "subject");
    }
}
