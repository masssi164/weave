package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatrixE2eeStateServiceSignatureMergeTest {

    @Test
    void crossSigningUploadPreservesDeviceSelfSignatureAndCanonicalKeyMaterial() {
        MatrixE2eeStateService service = service();
        var owner = identity("@owner:api.weave.test", "WEAVEOWNERDEVICE");
        var observer = identity("@observer:api.weave.test", "WEAVEOBSERVERDEVICE");
        String selfSigningKeyId = "ed25519:self-signing-public-key";
        Map<String, Object> canonicalDeviceKeys = deviceKeys(owner);

        service.uploadKeys(owner, Map.of("device_keys", canonicalDeviceKeys));
        service.uploadSignatures(owner, Map.of(
                owner.userId(),
                Map.of(owner.deviceId(), Map.of(
                        "user_id", owner.userId(),
                        "device_id", owner.deviceId(),
                        "algorithms", canonicalDeviceKeys.get("algorithms"),
                        "keys", canonicalDeviceKeys.get("keys"),
                        "signatures", Map.of(
                                owner.userId(),
                                Map.of(selfSigningKeyId, "self-signing-signature"))))));

        Map<String, Object> queriedDevice = queriedDevice(service, observer, owner);
        Map<String, Object> signatures = objectMap(queriedDevice.get("signatures"));
        Map<String, Object> ownerSignatures = objectMap(signatures.get(owner.userId()));

        assertThat(queriedDevice)
                .containsEntry("user_id", owner.userId())
                .containsEntry("device_id", owner.deviceId())
                .containsEntry("algorithms", canonicalDeviceKeys.get("algorithms"))
                .containsEntry("keys", canonicalDeviceKeys.get("keys"));
        assertThat(ownerSignatures)
                .containsEntry("ed25519:" + owner.deviceId(), "device-self-signature")
                .containsEntry(selfSigningKeyId, "self-signing-signature");
    }

    @Test
    void signatureUploadPreservesCrossSigningKeyAndExistingSignatures() {
        MatrixE2eeStateService service = service();
        var owner = identity("@owner:api.weave.test", "WEAVEOWNERDEVICE");
        String masterKeyId = "ed25519:master-public-key";
        String signatureTargetId = "master-public-key";
        Map<String, Object> masterKey = Map.of(
                "user_id", owner.userId(),
                "usage", List.of("master"),
                "keys", Map.of(masterKeyId, "master-public-key"),
                "signatures", Map.of(
                        owner.userId(),
                        Map.of("ed25519:" + owner.deviceId(), "device-signature")));

        service.uploadCrossSigning(owner, Map.of("master_key", masterKey));
        service.uploadSignatures(owner, Map.of(
                owner.userId(),
                Map.of(signatureTargetId, Map.of(
                        "user_id", owner.userId(),
                        "usage", List.of("master"),
                        "keys", masterKey.get("keys"),
                        "signatures", Map.of(
                                owner.userId(),
                                Map.of("ed25519:user-signing-public-key", "user-signature"))))));

        Map<String, Object> response = service.queryKeys(owner, Map.of(
                "device_keys", Map.of(owner.userId(), List.of())));
        Map<String, Object> queriedMaster = objectMap(objectMap(response.get("master_keys")).get(owner.userId()));
        Map<String, Object> ownerSignatures = objectMap(objectMap(queriedMaster.get("signatures")).get(owner.userId()));

        assertThat(queriedMaster)
                .containsEntry("usage", masterKey.get("usage"))
                .containsEntry("keys", masterKey.get("keys"));
        assertThat(ownerSignatures)
                .containsEntry("ed25519:" + owner.deviceId(), "device-signature")
                .containsEntry("ed25519:user-signing-public-key", "user-signature");
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
                "https://auth.weave.test/realms/weave");
    }

    private Map<String, Object> deviceKeys(MatrixFacadeClientStateService.MatrixIdentity identity) {
        return Map.of(
                "user_id", identity.userId(),
                "device_id", identity.deviceId(),
                "algorithms", List.of("m.olm.v1.curve25519-aes-sha2", "m.megolm.v1.aes-sha2"),
                "keys", Map.of(
                        "curve25519:" + identity.deviceId(), "curve25519-public-key",
                        "ed25519:" + identity.deviceId(), "ed25519-public-key"),
                "signatures", Map.of(
                        identity.userId(),
                        Map.of("ed25519:" + identity.deviceId(), "device-self-signature")));
    }

    private Map<String, Object> queriedDevice(
            MatrixE2eeStateService service,
            MatrixFacadeClientStateService.MatrixIdentity observer,
            MatrixFacadeClientStateService.MatrixIdentity owner) {
        Map<String, Object> response = service.queryKeys(observer, Map.of(
                "device_keys", Map.of(owner.userId(), List.of())));
        return objectMap(objectMap(objectMap(response.get("device_keys")).get(owner.userId())).get(owner.deviceId()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }
}
