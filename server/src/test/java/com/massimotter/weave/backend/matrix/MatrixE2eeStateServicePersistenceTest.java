package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MatrixE2eeStateServicePersistenceTest {

    private final ObjectMapper objectMapper =
            tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

    @Test
    void publicKeysToDeviceEventsAndOpaqueBackupsSurviveServiceRestart() {
        InMemoryMatrixE2eeRelationalStore store = new InMemoryMatrixE2eeRelationalStore(objectMapper);
        MatrixE2eeStateService first = service(store);
        var trusted = identity("WEAVETRUSTEDDEVICE", "oidc-session-hash-a");
        var second = identity("WEAVESECONDDEVICE");

        first.requireActive(trusted);
        first.uploadKeys(trusted, keyUpload(trusted, "trusted-public-key"));
        first.uploadKeys(second, keyUpload(second, "second-public-key"));
        first.sendToDevice(trusted, "m.room_key", "txn-persisted", Map.of(
                "messages", Map.of(second.userId(), Map.of(
                        second.deviceId(), Map.of("ciphertext", "opaque-to-device")))));
        String backupVersion = (String) first.createBackupVersion(trusted, Map.of(
                "algorithm", "m.megolm_backup.v1.curve25519-aes-sha2",
                "auth_data", Map.of("public_key", "curve25519-public"))).get("version");
        first.putBackupKeys(
                trusted,
                backupVersion,
                "!room:api.weave.test",
                "session-1",
                Map.of("session_data", Map.of("ciphertext", "opaque-backup")));
        first.putAccountData(
                trusted,
                "m.secret_storage.default_key",
                Map.of("key", "weave-recovery-key-id"));

        MatrixE2eeStateService restarted = service(store);
        Map<String, Object> queried = restarted.queryKeys(trusted, Map.of(
                "device_keys", Map.of(trusted.userId(), List.of())));
        Map<String, Object> backup = restarted.backupKeys(
                trusted,
                backupVersion,
                "!room:api.weave.test",
                "session-1");
        var sync = restarted.sync(second, 0);
        Map<String, Object> accountData = restarted.accountData(
                trusted,
                "m.secret_storage.default_key");
        long restoredSequence = sync.nextSequence();

        restarted.sendToDevice(trusted, "m.room_key", "txn-persisted", Map.of(
                "messages", Map.of(second.userId(), Map.of(
                        second.deviceId(), Map.of("ciphertext", "must-not-be-published-twice")))));

        assertThat(queried.toString()).contains("trusted-public-key", "second-public-key");
        assertThat(sync.toDeviceEvents()).singleElement().satisfies(event ->
                assertThat(event.toString()).contains("opaque-to-device"));
        assertThat(backup.toString()).contains("opaque-backup").doesNotContain("plaintext", "recoveryKey");
        assertThat(accountData).containsEntry("key", "weave-recovery-key-id");
        assertThat(restarted.currentSequence()).isEqualTo(restoredSequence);
        assertThat(restarted.sync(second, restoredSequence).toDeviceEvents()).isEmpty();
        assertThatThrownBy(() -> restarted.requireActive(identity(
                        "WEAVERENAMEDDEVICE",
                        "oidc-session-hash-a")))
                .isInstanceOfSatisfying(MatrixProtocolException.class, exception ->
                        assertThat(exception.errcode()).isEqualTo("M_UNKNOWN_TOKEN"));
        assertThat(store.load("tenant-a")).isPresent();
        assertThat(store.load("tenant-a").orElseThrow().sequence()).isEqualTo(restoredSequence);
    }

    @Test
    void fallbackKeyClaimAndUsedStateSurviveRestartWithoutMutatingStatusQueries() {
        InMemoryMatrixE2eeRelationalStore store = new InMemoryMatrixE2eeRelationalStore(objectMapper);
        MatrixE2eeStateService service = service(store);
        var target = identity("WEAVEFALLBACKTARGET");
        var claimant = identity("WEAVEFALLBACKCLAIMANT");

        service.uploadKeys(target, fallbackKeyUpload(target, "fallback-public-key"));
        long sequenceAfterUpload = service.currentSequence();
        Map<String, Object> status = service.uploadKeys(target, Map.of());

        assertThat(service.currentSequence()).isEqualTo(sequenceAfterUpload);
        assertThat(status).containsEntry("one_time_key_counts", Map.of());
        assertThat(service.sync(target, 0).unusedFallbackKeyTypes())
                .containsExactly("signed_curve25519");

        Map<String, Object> claimed = service.claimKeys(claimant, Map.of(
                "one_time_keys", Map.of(
                        target.userId(), Map.of(target.deviceId(), "signed_curve25519"))));

        assertThat(claimed.toString()).contains("fallback-public-key");
        assertThat(service.sync(target, 0).unusedFallbackKeyTypes()).isEmpty();

        MatrixE2eeStateService restarted = service(store);
        assertThat(restarted.sync(target, 0).unusedFallbackKeyTypes()).isEmpty();
        assertThat(restarted.claimKeys(claimant, Map.of(
                "one_time_keys", Map.of(
                        target.userId(), Map.of(target.deviceId(), "signed_curve25519")))).toString())
                .contains("fallback-public-key");

        restarted.uploadKeys(target, fallbackKeyUpload(target, "replacement-fallback-key"));
        assertThat(restarted.sync(target, 0).unusedFallbackKeyTypes())
                .containsExactly("signed_curve25519");
    }

    private MatrixE2eeStateService service(MatrixE2eeRelationalStore store) {
        return new MatrixE2eeStateService(objectMapper, store);
    }

    private MatrixFacadeClientStateService.MatrixIdentity identity(String deviceId) {
        return identity(deviceId, null);
    }

    private MatrixFacadeClientStateService.MatrixIdentity identity(String deviceId, String oidcSessionHash) {
        return new MatrixFacadeClientStateService.MatrixIdentity(
                "@user:api.weave.test",
                new ChatActorRef("user:subject"),
                deviceId,
                "tenant-a",
                "https://auth.example/realms/a",
                oidcSessionHash);
    }

    private Map<String, Object> keyUpload(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String publicKey) {
        return Map.of("device_keys", Map.of(
                "user_id", identity.userId(),
                "device_id", identity.deviceId(),
                "algorithms", List.of("m.megolm.v1.aes-sha2"),
                "keys", Map.of("ed25519:" + identity.deviceId(), publicKey),
                "signatures", Map.of()));
    }

    private Map<String, Object> fallbackKeyUpload(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String publicKey) {
        return Map.of(
                "device_keys", keyUpload(identity, publicKey).get("device_keys"),
                "fallback_keys", Map.of(
                        "signed_curve25519:FALLBACK", Map.of(
                                "key", publicKey,
                                "fallback", true,
                                "signatures", Map.of())));
    }
}
