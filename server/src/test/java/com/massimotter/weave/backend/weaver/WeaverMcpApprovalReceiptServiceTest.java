package com.massimotter.weave.backend.weaver;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalEvidence;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeaverMcpApprovalReceiptServiceTest {

    private static final String ACTOR = "user:member";
    private static final String PROFILE = "sha256:profile";
    private static final Map<String, Object> ARGUMENTS = Map.of(
            "title", "Planning",
            "startsAt", "2026-07-10T09:00:00Z",
            "calendarRef", "calendar:team:engineering");

    @Test
    void trustedExactElicitationMintsAOneUseServerReceipt() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T08:00:00Z"));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        WeaverMcpApprovalReceiptService service = new WeaverMcpApprovalReceiptService(audit, clock);
        var definition = MemberMcpToolCatalog.byName().get("calendar.create_event");
        ApprovalEvidence evidence = evidence(
                "elicitation://openclaw/one",
                "calendar.create_event",
                List.of("calendar:team:engineering"),
                clock.instant());

        var resolved = service.issue(evidence, ACTOR, PROFILE, definition, ARGUMENTS, delegatedIdentity());
        var replay = service.issue(evidence, ACTOR, PROFILE, definition, ARGUMENTS, delegatedIdentity());

        assertThat(resolved.approved()).isTrue();
        assertThat(resolved.receipt().receiptRef()).startsWith("approval://weaver/");
        assertThat(resolved.receipt().scopeRefs()).containsExactly("calendar:team:engineering");
        assertThat(resolved.receipt().argumentDigest())
                .isEqualTo(WeaverApprovalReceipt.argumentDigest(ARGUMENTS));
        assertThat(resolved.receipt().approvalMode()).isEqualTo("allow-once");
        assertThat(resolved.receipt().evidenceRef()).isEqualTo("elicitation://openclaw/one");
        assertThat(resolved.receipt().policyVersion()).isEqualTo(WeaverToolRegistry.APPROVAL_POLICY_VERSION);
        assertThat(resolved.receipt().effectiveMemberIssuer()).isEqualTo("https://auth.weave.test/realms/weave");
        assertThat(resolved.receipt().effectiveMemberSubject()).isEqualTo("member-123");
        assertThat(resolved.receipt().workloadClientId()).isEqualTo("weave-mcp-server");
        assertThat(resolved.receipt().tokenAudiences()).containsExactly("weave-backend");
        assertThat(resolved.receipt().delegationRef()).startsWith("delegation://sha256/");
        assertThat(resolved.receipt().entitlementRevision()).startsWith("entitlement://runtime-profile/");
        assertThat(resolved.receipt().nonce()).isNotBlank();
        assertThat(replay.status()).isEqualTo("mcp_elicitation_replayed");
        assertThat(audit.events()).extracting(event -> event.payload().get("status"))
                .containsExactly("mcp_elicitation_receipt_issued", "mcp_elicitation_replayed");
    }

    @Test
    void untrustedMismatchedExpiredAndForeignEvidenceFailClosed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T08:00:00Z"));
        WeaverMcpApprovalReceiptService service =
                new WeaverMcpApprovalReceiptService(new InMemoryAuditEventPublisher(), clock);
        var definition = MemberMcpToolCatalog.byName().get("calendar.create_event");

        var untrusted = service.issue(
                evidence("elicitation://openclaw/untrusted", definition.name(), List.of("calendar:team:engineering"), clock.instant()),
                ACTOR,
                PROFILE,
                definition,
                ARGUMENTS,
                null);
        var mismatched = service.issue(
                evidence("elicitation://openclaw/mismatch", definition.name(), List.of("calendar:workspace"), clock.instant()),
                ACTOR,
                PROFILE,
                definition,
                ARGUMENTS,
                delegatedIdentity());
        var foreign = service.issue(
                evidence("elicitation://other-client/foreign", definition.name(), List.of("calendar:team:engineering"), clock.instant()),
                ACTOR,
                PROFILE,
                definition,
                ARGUMENTS,
                delegatedIdentity());
        clock.advance(Duration.ofMinutes(3));
        var expired = service.issue(
                evidence("elicitation://openclaw/expired", definition.name(), List.of("calendar:team:engineering"), clock.instant().minus(Duration.ofMinutes(3))),
                ACTOR,
                PROFILE,
                definition,
                ARGUMENTS,
                delegatedIdentity());

        assertThat(untrusted.status()).isEqualTo("mcp_delegation_untrusted");
        assertThat(mismatched.status()).isEqualTo("mcp_elicitation_binding_mismatch");
        assertThat(foreign.status()).isEqualTo("mcp_elicitation_untrusted");
        assertThat(expired.status()).isEqualTo("mcp_elicitation_expired");
        assertThat(untrusted.receipt()).isNull();
        assertThat(mismatched.receipt()).isNull();
        assertThat(foreign.receipt()).isNull();
        assertThat(expired.receipt()).isNull();
    }

    private ApprovalEvidence evidence(
            String ref,
            String toolName,
            List<String> scopes,
            Instant decidedAt) {
        return new ApprovalEvidence(
                "mcp-elicitation/v1",
                ref,
                toolName,
                scopes,
                "allow-once",
                decidedAt.toString());
    }

    private McpDelegatedIdentity delegatedIdentity() {
        return new McpDelegatedIdentity(
                "https://auth.weave.test/realms/weave",
                "member-123",
                "org:workspace",
                "weave-mcp-server",
                List.of("weave-backend"),
                "delegation://sha256/test");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
