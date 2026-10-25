package com.massimotter.weave.backend.matrix;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Binds an explicit Matrix device ID to client-held possession material. */
@Service
public final class MatrixDeviceProofService {
    public static final String DEVICE_PROOF_HEADER = "X-Weave-Matrix-Device-Proof";
    private static final String RECOVERY_VERSION = "weave.matrix-device-continuity.v1";
    private static final byte[] ED25519_X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    private final MatrixE2eePersistence persistence;
    private final SecureRandom random = new SecureRandom();

    public MatrixDeviceProofService(MatrixE2eePersistence persistence) {
        this.persistence = persistence;
    }

    public void require(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String requestedDeviceId,
            String deviceProof) {
        if (requestedDeviceId == null) {
            if (deviceProof != null) throw denied();
            return;
        }
        if (requestedDeviceId.isBlank()) throw denied();
        String proofHash = proofHash(deviceProof);
        if (!persistence.bindDeviceProof(
                identity.tenantId(), identity.userId(), identity.deviceId(), proofHash)) {
            MatrixE2eePersistence.DeviceRecord device = persistence.device(
                    identity.tenantId(), identity.userId(), identity.deviceId()).orElse(null);
            if (device != null && !device.revoked() && !device.deviceKeys().isEmpty()
                    && persistence.deviceProofHash(identity.tenantId(), identity.userId(), identity.deviceId()).isEmpty()) {
                throw new MatrixProtocolException("M_WEAVE_DEVICE_RECOVERY_REQUIRED",
                        "The installed Matrix device must prove continuity before reuse.");
            }
            throw denied();
        }
    }

    public Map<String, Object> issueRecovery(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String requestedDeviceId, String deviceProof) {
        requireExplicitDevice(identity, requestedDeviceId);
        String hash = proofHash(deviceProof);
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String challengeId = UUID.randomUUID().toString();
        String challenge = RECOVERY_VERSION + "\n" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + "\n" + hash;
        Instant expiresAt = Instant.now().plusSeconds(300);
        if (!persistence.issueDeviceRecoveryChallenge(
                identity.tenantId(), identity.userId(), identity.deviceId(),
                challengeId, challenge, hash, expiresAt)) throw denied();
        return Map.of("challenge_id", challengeId, "challenge", challenge);
    }

    public Map<String, Object> completeRecovery(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String requestedDeviceId, String deviceProof, Map<String, Object> request) {
        requireExplicitDevice(identity, requestedDeviceId);
        String hash = proofHash(deviceProof);
        if (!(request.get("challenge_id") instanceof String challengeId)
                || !(request.get("signature") instanceof String signatureText)) throw denied();
        try {
            UUID.fromString(challengeId);
        } catch (IllegalArgumentException invalid) {
            throw denied();
        }
        MatrixE2eePersistence.DeviceRecoveryChallenge challenge = persistence.deviceRecoveryChallenge(
                identity.tenantId(), identity.userId(), identity.deviceId(), challengeId)
                .orElseThrow(MatrixDeviceProofService::denied);
        if (!MessageDigest.isEqual(challenge.proofHash().getBytes(StandardCharsets.US_ASCII),
                hash.getBytes(StandardCharsets.US_ASCII))) throw denied();
        MatrixE2eePersistence.DeviceRecord device = persistence.device(
                identity.tenantId(), identity.userId(), identity.deviceId())
                .orElseThrow(MatrixDeviceProofService::denied);
        if (device.revoked()) throw denied();
        Object rawKeys = device.deviceKeys().get("keys");
        if (!(rawKeys instanceof Map<?, ?> keys)) throw denied();
        Object rawPublicKey = keys.get("ed25519:" + identity.deviceId());
        if (!(rawPublicKey instanceof String publicKey)) throw denied();
        verifyDeviceSignature(publicKey, challenge.text(), signatureText);
        if (!persistence.completeDeviceRecoveryChallenge(
                identity.tenantId(), identity.userId(), identity.deviceId(),
                challengeId, hash, publicKey)) throw denied();
        return Map.of();
    }

    private static void requireExplicitDevice(
            MatrixFacadeClientStateService.MatrixIdentity identity, String requestedDeviceId) {
        if (requestedDeviceId == null || !requestedDeviceId.equals(identity.deviceId())) throw denied();
    }

    private static void verifyDeviceSignature(String publicKeyText, String challenge, String signatureText) {
        if (publicKeyText.length() < 43 || publicKeyText.length() > 44
                || signatureText.length() < 86 || signatureText.length() > 88) throw denied();
        try {
            byte[] publicKey = Base64.getDecoder().decode(publicKeyText);
            byte[] signatureBytes = Base64.getDecoder().decode(signatureText);
            if (publicKey.length != 32 || signatureBytes.length != 64) throw denied();
            byte[] encoded = new byte[ED25519_X509_PREFIX.length + publicKey.length];
            System.arraycopy(ED25519_X509_PREFIX, 0, encoded, 0, ED25519_X509_PREFIX.length);
            System.arraycopy(publicKey, 0, encoded, ED25519_X509_PREFIX.length, publicKey.length);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded)));
            verifier.update(challenge.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signatureBytes)) throw denied();
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw denied();
        }
    }

    private static String proofHash(String deviceProof) {
        if (deviceProof == null || !deviceProof.matches("[A-Za-z0-9_-]{43,86}")) throw denied();
        byte[] secret;
        try {
            secret = Base64.getUrlDecoder().decode(deviceProof);
        } catch (IllegalArgumentException invalid) {
            throw denied();
        }
        if (secret.length < 32 || secret.length > 64) throw denied();
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(secret);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
        return HexFormat.of().formatHex(digest);
    }

    private static MatrixProtocolException denied() {
        return new MatrixProtocolException("M_UNKNOWN_TOKEN", "Matrix device possession could not be verified.");
    }
}
