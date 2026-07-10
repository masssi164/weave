package com.massimotter.weave.backend.weaver;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeaverApprovalReceiptTest {

    private static final Map<String, Object> ARGUMENTS = Map.of(
            "title", "Planning",
            "calendarRef", "calendar:team:engineering");

    @Test
    void receiptIsBoundToActorProfileDomainToolScopePolicyAndContractVersion() {
        WeaverApprovalReceipt receipt = receipt(
                "sha256:profile-1",
                MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(),
                MemberMcpDomainDefinition.CONTRACT_VERSION,
                "approved",
                Instant.now().minusSeconds(1));

        assertThat(receipt.validFor(
                        "user:member",
                        "sha256:profile-1",
                        MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(),
                        "calendar.create_event",
                        List.of("calendar:team:engineering"),
                        ARGUMENTS,
                        "policy:support-safe-bridge-v1",
                        MemberMcpDomainDefinition.CONTRACT_VERSION))
                .isTrue();
        assertThat(receipt.validFor(
                        "user:member",
                        "sha256:profile-2",
                        MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(),
                        "calendar.create_event",
                        List.of("calendar:team:engineering"),
                        ARGUMENTS,
                        "policy:support-safe-bridge-v1",
                        MemberMcpDomainDefinition.CONTRACT_VERSION))
                .isFalse();
        assertThat(receipt.validFor(
                        "user:member",
                        "sha256:profile-1",
                        "provider-calendar",
                        "calendar.create_event",
                        List.of("calendar:team:engineering"),
                        ARGUMENTS,
                        "policy:support-safe-bridge-v1",
                        "obsolete-contract"))
                .isFalse();
    }

    @Test
    void receiptRejectsChangedArgumentsEvenWhenCanonicalScopesStillMatch() {
        WeaverApprovalReceipt receipt = receipt(
                "sha256:profile-1",
                MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(),
                MemberMcpDomainDefinition.CONTRACT_VERSION,
                "approved",
                Instant.now().minusSeconds(1));

        assertThat(receipt.validFor(
                        "user:member",
                        "sha256:profile-1",
                        MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(),
                        "calendar.create_event",
                        List.of("calendar:team:engineering"),
                        Map.of("title", "Changed title", "calendarRef", "calendar:team:engineering"),
                        "policy:support-safe-bridge-v1",
                        MemberMcpDomainDefinition.CONTRACT_VERSION))
                .isFalse();
    }

    @Test
    void deniedOrNotYetApprovedReceiptFailsClosed() {
        WeaverApprovalReceipt denied = receipt(
                "sha256:profile-1",
                MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(),
                MemberMcpDomainDefinition.CONTRACT_VERSION,
                "denied",
                Instant.now().minusSeconds(1));
        WeaverApprovalReceipt futureApproval = receipt(
                "sha256:profile-1",
                MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(),
                MemberMcpDomainDefinition.CONTRACT_VERSION,
                "approved",
                Instant.now().plusSeconds(60));

        assertThat(valid(denied)).isFalse();
        assertThat(valid(futureApproval)).isFalse();
    }

    private boolean valid(WeaverApprovalReceipt receipt) {
        return receipt.validFor(
                "user:member",
                "sha256:profile-1",
                MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(),
                "calendar.create_event",
                List.of("calendar:team:engineering"),
                ARGUMENTS,
                "policy:support-safe-bridge-v1",
                MemberMcpDomainDefinition.CONTRACT_VERSION);
    }

    private WeaverApprovalReceipt receipt(
            String runtimeProfileHash,
            String domain,
            String contractVersion,
            String decision,
            Instant approvedAt) {
        return new WeaverApprovalReceipt(
                "approval://calendar-create/1",
                "user:member",
                runtimeProfileHash,
                domain,
                "calendar.create_event",
                List.of("calendar:team:engineering"),
                WeaverApprovalReceipt.argumentDigest(ARGUMENTS),
                contractVersion,
                "policy:support-safe-bridge-v1",
                decision,
                "allow-once",
                "elicitation://openclaw/test",
                approvedAt.toString(),
                Instant.now().plusSeconds(300).toString(),
                "audit://weaver-approval/calendar-create/1");
    }
}
