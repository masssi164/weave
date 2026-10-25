package com.massimotter.weave.backend.agentruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RuntimeProfileIssuanceServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T10:00:30Z");
    private static final Instant ISSUED_AT = Instant.parse("2026-07-20T10:00:00Z");

    private RuntimePolicyAuthority policy;
    private RuntimeProfileSigner signer;
    private RuntimeProfileRepository profiles;
    private RuntimeProfileIssuanceService service;

    @BeforeEach
    void setUp() {
        policy = mock(RuntimePolicyAuthority.class);
        signer = mock(RuntimeProfileSigner.class);
        profiles = mock(RuntimeProfileRepository.class);
        service = new RuntimeProfileIssuanceService(
                policy, signer, profiles, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void bindsAStableProfileIdToCellSemanticsAndTheIdempotentIssuanceReference() {
        RuntimeCell cell = cell(RuntimeEntitlementState.ENTITLED);
        RuntimeProfile unsigned = mock(RuntimeProfile.class);
        SignedRuntimeProfile signed = signedProfile();
        given(policy.profileTtl()).willReturn(Duration.ofMinutes(2));
        given(policy.runtimeProfile(eq(cell), anyString(), eq(ISSUED_AT), eq(ISSUED_AT.plusSeconds(120))))
                .willReturn(unsigned);
        given(signer.sign(unsigned)).willReturn(signed);
        given(profiles.activate(eq(cell), eq(signed), eq(NOW))).willReturn(signed);

        assertThat(service.issue(cell, "start:idempotency-key-0001", ISSUED_AT)).isEqualTo(signed);
        assertThat(service.issue(cell, "start:idempotency-key-0001", ISSUED_AT)).isEqualTo(signed);

        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(policy, org.mockito.Mockito.times(2)).runtimeProfile(
                eq(cell), ids.capture(), eq(ISSUED_AT), eq(ISSUED_AT.plusSeconds(120)));
        assertThat(ids.getAllValues()).hasSize(2).allSatisfy(id -> assertThat(id).startsWith("rp_"));
        assertThat(ids.getAllValues().get(0)).isEqualTo(ids.getAllValues().get(1));
    }

    @Test
    void failsBeforeSigningWhenEntitlementOrTheIdempotentWindowIsInactive() {
        assertThatThrownBy(() -> service.issue(
                cell(RuntimeEntitlementState.REVOKED), "start:idempotency-key-0001", ISSUED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inactive entitlement");

        given(policy.profileTtl()).willReturn(Duration.ofSeconds(30));
        assertThatThrownBy(() -> service.issue(
                cell(RuntimeEntitlementState.ENTITLED), "start:idempotency-key-0002", NOW.minusSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("window has expired");

        verifyNoInteractions(signer, profiles);
    }

    private static RuntimeCell cell(RuntimeEntitlementState entitlementState) {
        RuntimeCell active = RuntimeCell.provisioning(
                "tenant-default", "acct_" + "a".repeat(32),
                new RuntimeMemberBinding("https://auth.weave.test/realms/weave", "keycloak-user-1"),
                "cell:example",
                new RuntimeWorkloadBinding(
                        "https://auth.weave.test/realms/weave",
                        "service-account-weaver-cell-example",
                        "weaver-cell-example",
                        RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                        "credentialref://weave/runtime/cell-example/workload"),
                "sha256:" + "1".repeat(64),
                "workspace:v1", "webdav-manifest://tenant-default/example/current",
                "runtime-state://tenant-default/example/state", "audit:example", ISSUED_AT);
        if (entitlementState == RuntimeEntitlementState.ENTITLED) {
            return active;
        }
        return new RuntimeCell(
                active.recordId(), active.organizationRef(), active.personRef(), active.memberBinding(),
                active.cellRef(), active.workloadBinding(), entitlementState, active.entitlementRevision(),
                RuntimeCellState.REVOKING, active.observedState(), null, null, active.workspaceRevision(),
                active.workspaceManifestRef(), active.runtimeStateStoreRef(), active.fencingEpoch(), null, null,
                active.version(), active.auditRef(), active.createdAt(), active.updatedAt());
    }

    private static SignedRuntimeProfile signedProfile() {
        return new SignedRuntimeProfile(
                "protected", "payload", "A".repeat(86), "sha256:" + "2".repeat(64),
                "rp_example", "cell:example", "key-example", ISSUED_AT,
                ISSUED_AT.plusSeconds(120));
    }
}
