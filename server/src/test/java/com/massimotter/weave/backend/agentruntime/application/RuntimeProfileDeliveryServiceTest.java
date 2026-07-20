package com.massimotter.weave.backend.agentruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeProfileException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeProfileDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");
    private static final String HASH = "sha256:" + "a".repeat(64);
    private static final RuntimeWorkloadPrincipal PRINCIPAL = new RuntimeWorkloadPrincipal(
            "https://auth.weave.test/realms/weave", "service-account-subject-1", "weaver-cell-example");

    private RuntimeProfileRepository repository;
    private RuntimeProfileVerifier verifier;
    private RuntimeProfileDeliveryService service;
    private SignedRuntimeProfile envelope;
    private RuntimeProfile profile;
    private RuntimeProfile.WorkloadIdentity workload;

    @BeforeEach
    void setUp() {
        repository = mock(RuntimeProfileRepository.class);
        verifier = mock(RuntimeProfileVerifier.class);
        service = new RuntimeProfileDeliveryService(
                repository, verifier, Clock.fixed(NOW, ZoneOffset.UTC));
        envelope = new SignedRuntimeProfile(
                "header", "payload", "A".repeat(86), HASH,
                "rp_example", "cell:example", "key-1",
                NOW.minusSeconds(30), NOW.plusSeconds(30));
        profile = mock(RuntimeProfile.class);
        workload = mock(RuntimeProfile.WorkloadIdentity.class);
        when(profile.workloadIdentity()).thenReturn(workload);
        when(workload.issuer()).thenReturn(PRINCIPAL.issuer());
        when(workload.subject()).thenReturn(PRINCIPAL.subject());
        when(workload.clientId()).thenReturn(PRINCIPAL.clientId());
    }

    @Test
    void returnsOnlyTheCurrentVerifiedEnvelopeForTheExactWorkload() {
        when(repository.findCurrentForWorkload(
                HASH, PRINCIPAL.issuer(), PRINCIPAL.subject(), PRINCIPAL.clientId(), NOW))
                .thenReturn(Optional.of(envelope));
        when(verifier.verify(envelope, NOW)).thenReturn(profile);

        assertThat(service.findCurrent(HASH, PRINCIPAL)).contains(envelope);
    }

    @Test
    void hidesProfileAndBindingMismatchesBehindAbsence() {
        when(repository.findCurrentForWorkload(
                HASH, PRINCIPAL.issuer(), PRINCIPAL.subject(), PRINCIPAL.clientId(), NOW))
                .thenReturn(Optional.of(envelope));
        when(verifier.verify(envelope, NOW)).thenReturn(profile);
        when(workload.clientId()).thenReturn("weaver-cell-another");

        assertThat(service.findCurrent(HASH, PRINCIPAL)).isEmpty();

        SignedRuntimeProfile wrongHash = new SignedRuntimeProfile(
                envelope.protectedHeader(), envelope.payload(), envelope.signature(),
                "sha256:" + "b".repeat(64), envelope.profileId(), envelope.cellRef(), envelope.keyId(),
                envelope.issuedAt(), envelope.expiresAt());
        when(repository.findCurrentForWorkload(
                HASH, PRINCIPAL.issuer(), PRINCIPAL.subject(), PRINCIPAL.clientId(), NOW))
                .thenReturn(Optional.of(wrongHash));
        assertThat(service.findCurrent(HASH, PRINCIPAL)).isEmpty();
    }

    @Test
    void invalidStoredEvidenceFailsClosedAndMalformedHashesNeverReachPersistence() {
        when(repository.findCurrentForWorkload(
                HASH, PRINCIPAL.issuer(), PRINCIPAL.subject(), PRINCIPAL.clientId(), NOW))
                .thenReturn(Optional.of(envelope));
        when(verifier.verify(envelope, NOW)).thenThrow(new InvalidRuntimeProfileException("invalid-signature"));

        assertThat(service.findCurrent(HASH, PRINCIPAL)).isEmpty();

        RuntimeProfileRepository untouched = mock(RuntimeProfileRepository.class);
        RuntimeProfileDeliveryService guarded = new RuntimeProfileDeliveryService(
                untouched, verifier, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(guarded.findCurrent("not-a-hash", PRINCIPAL)).isEmpty();
        verifyNoInteractions(untouched);
    }
}
