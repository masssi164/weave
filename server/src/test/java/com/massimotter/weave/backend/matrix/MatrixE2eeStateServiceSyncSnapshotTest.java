package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class MatrixE2eeStateServiceSyncSnapshotTest {

    @Test
    void supportSafeEvidenceDistinguishesQueuedAndProjectedOlmEnvelopes() {
        MatrixE2eeStateService service = service();
        var sender = identity("@sender:api.weave.test", "WEAVESENDERDEVICE");
        var target = identity("@target:api.weave.test", "WEAVETARGETDEVICE");
        Map<String, Object> olmEnvelope = Map.of("body", "opaque", "type", 0);
        Map<String, Object> encryptedContent = Map.of(
                "algorithm", "m.olm.v1.curve25519-aes-sha2",
                "ciphertext", Map.of("opaque-recipient-key", olmEnvelope));
        service.sendToDevice(sender, "m.room.encrypted", "txn-room-key",
                Map.of("messages", Map.of(target.userId(), Map.of(target.deviceId(), encryptedContent))));

        var queued = service.supportSafeToDeviceEvidence();
        assertThat(queued.contractVersion()).isEqualTo("matrix-to-device-proof-v1");
        assertThat(queued.queuedEventCount()).isEqualTo(1);
        assertThat(queued.encryptedEventCount()).isEqualTo(1);
        assertThat(queued.plaintextRoomKeyEventCount()).isZero();
        assertThat(queued.olmPreKeyEnvelopeCount()).isEqualTo(1);
        assertThat(queued.olmExistingSessionEnvelopeCount()).isZero();
        assertThat(queued.targetedDeviceCount()).isEqualTo(1);
        assertThat(queued.projectedEventCount()).isZero();
        assertThat(queued.supportSafe()).isTrue();

        assertThat(service.sync(target, 0).toDeviceEvents()).hasSize(1);
        var projected = service.supportSafeToDeviceEvidence();
        assertThat(projected.projectedEventCount()).isEqualTo(1);
        assertThat(projected.syncResponseCount()).isEqualTo(1);
    }

    @Test
    @Timeout(10)
    void syncCursorNeverAdvancesPastAnUnpublishedToDeviceEvent() throws Exception {
        MatrixE2eeStateService service = service();
        var sender = identity("@sender:api.weave.test", "WEAVESENDERDEVICE");
        var target = identity("@target:api.weave.test", "WEAVETARGETDEVICE");
        int eventCount = 500;
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean publishing = new AtomicBoolean(true);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> publisher = executor.submit(() -> {
                await(start);
                for (int index = 0; index < eventCount; index++) {
                    service.sendToDevice(
                            sender,
                            "m.room_key",
                            "txn-" + index,
                            Map.of("messages", Map.of(target.userId(), Map.of(target.deviceId(), Map.of("index", index)))));
                }
                publishing.set(false);
            });
            Future<?> observer = executor.submit(() -> {
                await(start);
                while (publishing.get()) {
                    assertSnapshotDoesNotSkip(service.sync(target, 0));
                    Thread.yield();
                }
            });

            start.countDown();
            publisher.get(8, TimeUnit.SECONDS);
            observer.get(8, TimeUnit.SECONDS);

            long cursor = 0;
            int delivered = 0;
            while (cursor < eventCount) {
                MatrixProtocolCoreService.MatrixSyncCrypto page = service.sync(target, cursor);
                assertThat(page.nextSequence()).isGreaterThan(cursor).isLessThanOrEqualTo(eventCount);
                assertThat(page.toDeviceEvents()).hasSizeLessThanOrEqualTo(100);
                delivered += page.toDeviceEvents().size();
                cursor = page.nextSequence();
            }

            assertThat(delivered).isEqualTo(eventCount);
            assertThat(cursor).isEqualTo(eventCount);
            assertThat(service.sync(target, cursor).toDeviceEvents()).isEmpty();
            assertThat(service.combinedCursor("chat-cursor", cursor))
                    .isEqualTo("chat-cursor|e2ee:" + eventCount);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private void assertSnapshotDoesNotSkip(MatrixProtocolCoreService.MatrixSyncCrypto snapshot) {
        assertThat(snapshot.nextSequence()).isBetween(0L, 500L);
        assertThat(snapshot.toDeviceEvents()).hasSizeLessThanOrEqualTo(100);
        if (snapshot.toDeviceEvents().size() == 100) {
            assertThat(snapshot.nextSequence()).isEqualTo(100L);
        }
    }

    private MatrixE2eeStateService service() {
        return new MatrixE2eeStateService(new InMemoryMatrixE2eeRelationalStore());
    }

    private MatrixFacadeClientStateService.MatrixIdentity identity(String userId, String deviceId) {
        return new MatrixFacadeClientStateService.MatrixIdentity(
                userId,
                new ChatActorRef("user:" + userId),
                deviceId,
                "tenant-a",
                null);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("The concurrent Matrix E2EE test did not start.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("The concurrent Matrix E2EE test was interrupted.", exception);
        }
    }
}
