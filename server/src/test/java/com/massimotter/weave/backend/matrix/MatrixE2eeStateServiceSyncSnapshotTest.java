package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        assertThat(queued.projectedToDeviceEventCount()).isZero();
        assertThat(queued.supportSafe()).isTrue();

        assertThat(service.sync(target, 0).toDeviceEvents()).hasSize(1);
        var projected = service.supportSafeToDeviceEvidence();
        assertThat(projected.projectedToDeviceEventCount()).isEqualTo(1);
        assertThat(projected.syncResponsesWithToDeviceEvents()).isEqualTo(1);
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

            MatrixE2eeStateService.E2eeSyncCursor cursor = new MatrixE2eeStateService.E2eeSyncCursor(0, 0);
            Set<Integer> deliveredIndexes = new HashSet<>();
            while (deliveredIndexes.size() < eventCount) {
                MatrixProtocolCoreService.MatrixSyncCrypto page = service.sync(
                        target,
                        cursor.toDeviceSequence(),
                        cursor.deviceListSequence(),
                        null);
                assertThat(page.toDeviceEvents()).hasSizeLessThanOrEqualTo(100);
                assertThat(page.nextSequence()).isGreaterThanOrEqualTo(cursor.toDeviceSequence()).isLessThanOrEqualTo(eventCount);
                for (Map<String, Object> event : page.toDeviceEvents()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> content = (Map<String, Object>) event.get("content");
                    deliveredIndexes.add((Integer) content.get("index"));
                }
                String encoded = service.combinedCursor("chat-cursor", page);
                cursor = service.cryptoCursor(encoded);
                if (page.toDeviceEvents().isEmpty()) break;
            }

            assertThat(deliveredIndexes).containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.range(0, eventCount).boxed().toList());
            assertThat(cursor.toDeviceSequence()).isEqualTo(eventCount);
            assertThat(service.sync(target, cursor.toDeviceSequence(), cursor.deviceListSequence(), null).toDeviceEvents()).isEmpty();
            assertThat(service.combinedCursor("chat-cursor", new MatrixE2eeStateService.E2eeSyncCursor(eventCount, eventCount)))
                    .isEqualTo("chat-cursor|e2ee:v2:" + eventCount + ":" + eventCount);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private void assertSnapshotDoesNotSkip(MatrixProtocolCoreService.MatrixSyncCrypto snapshot) {
        assertThat(snapshot.nextSequence()).isBetween(0L, 500L);
        assertThat(snapshot.toDeviceEvents()).hasSizeLessThanOrEqualTo(100);
        if (!snapshot.toDeviceEvents().isEmpty()) {
            assertThat(snapshot.nextSequence()).isPositive();
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
