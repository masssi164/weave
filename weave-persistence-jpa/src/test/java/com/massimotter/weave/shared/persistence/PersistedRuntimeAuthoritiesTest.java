package com.massimotter.weave.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementDeniedException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadBindingAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistedRuntimeAuthoritiesTest {
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final RuntimeMemberBinding MEMBER = new RuntimeMemberBinding(ISSUER, "member-subject");
    private static final RuntimeWorkloadBinding WORKLOAD = new RuntimeWorkloadBinding(
            ISSUER,
            "service-account-cell",
            "weaver-cell-test",
            RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
            "credentialref://weave/runtime/weaver-cell-test");

    @Test
    void acceptsOnlyTheExactCurrentlyPersistedWorkloadBinding() {
        RuntimeCellRepository cells = mock(RuntimeCellRepository.class);
        when(cells.findByWorkload(ISSUER, WORKLOAD.subject())).thenReturn(Optional.of(cell()));
        var authority = new PersistedRuntimeWorkloadBindingAuthority(cells);
        var current = new RuntimeWorkloadBindingAuthority.CurrentBindingCommand(
                "org:test", "person:test", "cell:test", WORKLOAD, "audit:test");

        authority.requireCurrentBinding(current);

        var crossCell = new RuntimeWorkloadBindingAuthority.CurrentBindingCommand(
                "org:test", "person:test", "cell:other", WORKLOAD, "audit:test");
        assertThatThrownBy(() -> authority.requireCurrentBinding(crossCell))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageNotContaining(WORKLOAD.subject());
    }

    @Test
    void acceptsOnlyAnEffectivePersistedEntitlementForTheExactMember() {
        RuntimeGovernanceRepository governance = mock(RuntimeGovernanceRepository.class);
        RuntimeEntitlementRef entitlement = entitlement(NOW.plusSeconds(30));
        when(governance.findCurrent("org:test", "person:test")).thenReturn(Optional.of(entitlement));
        var authority = new PersistedRuntimeEntitlementAuthority(
                governance, Clock.fixed(NOW, ZoneOffset.UTC));
        var command = new RuntimeEntitlementAuthority.ObserveEntitlementCommand(
                "org:test", "person:test", MEMBER, "audit:test");

        assertThat(authority.observe(command).expiresAt()).isEqualTo(NOW.plusSeconds(30));

        var wrongMember = new RuntimeEntitlementAuthority.ObserveEntitlementCommand(
                "org:test",
                "person:test",
                new RuntimeMemberBinding(ISSUER, "other-member"),
                "audit:test");
        assertThatThrownBy(() -> authority.observe(wrongMember))
                .isInstanceOf(RuntimeEntitlementDeniedException.class);

        when(governance.findCurrent("org:test", "person:test"))
                .thenReturn(Optional.of(entitlement(NOW)));
        assertThatThrownBy(() -> authority.observe(command))
                .isInstanceOf(RuntimeEntitlementDeniedException.class);
    }

    private static RuntimeCell cell() {
        return RuntimeCell.provisioning(
                "org:test",
                "person:test",
                MEMBER,
                "cell:test",
                WORKLOAD,
                "sha256:" + "1".repeat(64),
                "workspace:1",
                "webdav-manifest:workspace:1",
                "runtime-state://org/test/person/test/state/1",
                "audit:test",
                NOW.minusSeconds(60));
    }

    private static RuntimeEntitlementRef entitlement(Instant expiresAt) {
        return new RuntimeEntitlementRef(
                UUID.randomUUID(),
                "entitlement:" + "1".repeat(64),
                "sha256:" + "2".repeat(64),
                "org:test",
                "person:test",
                MEMBER,
                "keycloak",
                "sha256:" + "3".repeat(64),
                "sha256:" + "4".repeat(64),
                RuntimeEntitlementState.ENTITLED,
                NOW.minusSeconds(60),
                NOW.minusSeconds(1),
                expiresAt,
                null,
                null,
                "audit:test",
                NOW.minusSeconds(60),
                NOW.minusSeconds(1));
    }
}
