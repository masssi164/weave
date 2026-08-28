package com.massimotter.weave.backend.runner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.runner.application.RunnerClaimHttpSemantics.ClaimHttpResponse;
import com.massimotter.weave.backend.runner.application.RunnerTaskClaimService.ClaimCommand;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.CancellationDisposition;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.CancellationRequest;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Claim;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Completion;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.CompletionDisposition;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Heartbeat;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.LeaseDirective;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.NewTask;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.TaskSnapshot;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity.RunnerAuthenticationException;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunnerTaskClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T14:00:00Z");
    private static final String BUNDLE_DIGEST =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CERTIFICATE_FINGERPRINT =
            "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final RunnerId RUNNER_A = new RunnerId("runner_claim_service_a01");
    private static final RunnerId RUNNER_B = new RunnerId("runner_claim_service_b01");
    private static final CapabilityRef CAPABILITY =
            new CapabilityRef(new CapabilityId("internal.asset.lookup"), "1.0.0");

    @Test
    void mismatchedRunnerIdentityFailsBeforeTaskStoreAccess() {
        RecordingTaskStore store = new RecordingTaskStore();
        RunnerTaskClaimService service = service(store);

        assertThrows(
                RunnerAuthenticationException.class,
                () -> service.claim(
                        identity(RUNNER_A, NOW.plusSeconds(300)),
                        List.of("wait=0"),
                        command(RUNNER_B)));
        assertFalse(store.claimed);
    }

    @Test
    void expiredCertificateFailsBeforeTaskStoreAccess() {
        RecordingTaskStore store = new RecordingTaskStore();
        RunnerTaskClaimService service = service(store);

        assertThrows(
                RunnerAuthenticationException.class,
                () -> service.claim(
                        identity(RUNNER_A, NOW),
                        List.of("wait=0"),
                        command(RUNNER_A)));
        assertFalse(store.claimed);
    }

    @Test
    void authenticatedClaimUsesIdentityOrganizationAndDocumentedDefaultWait() {
        RecordingTaskStore store = new RecordingTaskStore();
        store.nextLease = Optional.of(lease());
        RunnerTaskClaimService service = service(store);

        ClaimHttpResponse<Lease> response = service.claim(
                identity(RUNNER_A, NOW.plusSeconds(300)),
                List.of(),
                command(RUNNER_A));

        assertEquals(200, response.status());
        assertEquals("wait=25", response.headers().get("Preference-Applied"));
        assertTrue(response.body().isPresent());
        assertEquals("org:trusted", store.lastClaim.organizationRef());
        assertEquals(RUNNER_A, store.lastClaim.runnerId());
        assertEquals(BUNDLE_DIGEST, store.lastClaim.bundleDigest());
        assertEquals(NOW, store.lastClaim.now());
    }

    @Test
    void authenticatedImmediateEmptyClaimReturnsTheCanonicalNoContentHeaders() {
        RecordingTaskStore store = new RecordingTaskStore();
        RunnerTaskClaimService service = service(store);

        ClaimHttpResponse<Lease> response = service.claim(
                identity(RUNNER_A, NOW.plusSeconds(300)),
                List.of("wait=0"),
                command(RUNNER_A));

        assertEquals(204, response.status());
        assertEquals("wait=0", response.headers().get("Preference-Applied"));
        assertEquals("1", response.headers().get("Retry-After"));
        assertTrue(response.body().isEmpty());
    }

    private RunnerTaskClaimService service(RecordingTaskStore store) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RunnerTaskQueue queue = new RunnerTaskQueue(
                store,
                new InMemoryRunnerTaskAvailabilitySignal(),
                clock);
        return new RunnerTaskClaimService(queue, clock, Duration.ofSeconds(30));
    }

    private RunnerWorkloadIdentity identity(RunnerId runnerId, Instant expiresAt) {
        return new RunnerWorkloadIdentity(
                runnerId,
                "org:trusted",
                CERTIFICATE_FINGERPRINT,
                NOW.minusSeconds(300),
                expiresAt);
    }

    private ClaimCommand command(RunnerId runnerId) {
        return new ClaimCommand(runnerId, BUNDLE_DIGEST, Set.of(CAPABILITY), 1);
    }

    private Lease lease() {
        return new Lease(
                UUID.fromString("00000000-0000-0000-0000-000000002000"),
                UUID.fromString("00000000-0000-0000-0000-000000002001"),
                1,
                RUNNER_A,
                CAPABILITY,
                BUNDLE_DIGEST,
                1,
                "authenticated-claim-idempotency-key",
                "{\"assetId\":\"A-42\"}",
                "[]",
                "[]",
                NOW,
                NOW.plusSeconds(30),
                NOW.plusSeconds(300),
                TRACEPARENT);
    }

    private static final class RecordingTaskStore implements RunnerTaskStore {

        private boolean claimed;
        private Claim lastClaim;
        private Optional<Lease> nextLease = Optional.empty();

        @Override
        public void enqueue(NewTask task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Lease> claim(Claim claim) {
            claimed = true;
            lastClaim = claim;
            return nextLease;
        }

        @Override
        public LeaseDirective heartbeat(Heartbeat heartbeat) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CancellationDisposition requestCancellation(CancellationRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionDisposition complete(Completion completion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TaskSnapshot> find(UUID taskId) {
            return Optional.empty();
        }
    }
}
