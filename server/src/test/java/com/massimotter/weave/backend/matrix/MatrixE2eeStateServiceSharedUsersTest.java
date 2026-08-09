package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatrixE2eeStateServiceSharedUsersTest {

    @Test
    void newlySharedEncryptedRoomUserIsReportedAfterTheirEarlierDeviceUpload() {
        MatrixE2eeStateService service = service();
        var observer = identity("@observer:api.weave.test", "WEAVEOBSERVERDEVICE", "tenant-a");
        var peer = identity("@peer:api.weave.test", "WEAVEPEERDEVICE", "tenant-a");

        service.uploadKeys(peer, keyUpload(peer));
        long cursorAfterPeerUpload = service.currentSequence(observer);

        var firstSharedSync = service.sync(observer, cursorAfterPeerUpload, Set.of(peer.userId()));
        var retriedSharedSync = service.sync(observer, cursorAfterPeerUpload, Set.of(peer.userId()));

        assertThat(firstSharedSync.deviceListsChanged()).containsExactly(peer.userId());
        assertThat(firstSharedSync.deviceListsLeft()).isEmpty();
        assertThat(firstSharedSync.nextSequence()).isGreaterThan(cursorAfterPeerUpload);
        assertThat(retriedSharedSync.deviceListsChanged()).containsExactly(peer.userId());
        assertThat(service.sync(observer, firstSharedSync.nextSequence(), Set.of(peer.userId())).deviceListsChanged()).isEmpty();
    }

    @Test
    void leavingLastEncryptedRoomIsReportedAndUnrelatedTenantDevicesStayPrivate() {
        MatrixE2eeStateService service = service();
        var observer = identity("@observer:api.weave.test", "WEAVEOBSERVERDEVICE", "tenant-a");
        var peer = identity("@peer:api.weave.test", "WEAVEPEERDEVICE", "tenant-a");
        var unrelated = identity("@unrelated:api.weave.test", "WEAVEUNRELATEDDEVICE", "tenant-a");

        service.uploadKeys(peer, keyUpload(peer));
        service.uploadKeys(unrelated, keyUpload(unrelated));
        var joined = service.sync(observer, 0, Set.of(peer.userId()));
        var left = service.sync(observer, joined.nextSequence(), Set.of());
        var retriedLeft = service.sync(observer, joined.nextSequence(), Set.of());

        assertThat(joined.deviceListsChanged()).contains(peer.userId()).doesNotContain(unrelated.userId());
        assertThat(left.deviceListsChanged()).isEmpty();
        assertThat(left.deviceListsLeft()).containsExactly(peer.userId());
        assertThat(retriedLeft.deviceListsLeft()).containsExactly(peer.userId());

        String acknowledged = service.combinedCursor("chat-cursor", left);
        MatrixE2eeStateService.E2eeSyncCursor acknowledgedCrypto = service.cryptoCursor(acknowledged);
        assertThat(service.sync(
                observer,
                acknowledgedCrypto.toDeviceSequence(),
                acknowledgedCrypto.deviceListSequence(),
                Set.of()).deviceListsLeft()).isEmpty();
    }

    private MatrixE2eeStateService service() {
        return new MatrixE2eeStateService(new InMemoryMatrixE2eeRelationalStore());
    }

    private MatrixFacadeClientStateService.MatrixIdentity identity(String userId, String deviceId, String tenantId) {
        return new MatrixFacadeClientStateService.MatrixIdentity(
                userId,
                new ChatActorRef("user:" + userId),
                deviceId,
                tenantId,
                "https://auth.weave.test/realms/weave");
    }

    private Map<String, Object> keyUpload(MatrixFacadeClientStateService.MatrixIdentity identity) {
        return Map.of(
                "device_keys",
                Map.of(
                        "user_id", identity.userId(),
                        "device_id", identity.deviceId(),
                        "algorithms", List.of("m.olm.v1.curve25519-aes-sha2"),
                        "keys", Map.of("curve25519:" + identity.deviceId(), "public-key")));
    }
}
