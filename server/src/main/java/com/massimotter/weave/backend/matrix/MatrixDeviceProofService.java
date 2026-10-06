package com.massimotter.weave.backend.matrix;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** Binds an explicit Matrix device ID to client-held possession material. */
@Service
public final class MatrixDeviceProofService {
    public static final String DEVICE_PROOF_HEADER = "X-Weave-Matrix-Device-Proof";

    private final MatrixE2eePersistence persistence;

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
        if (requestedDeviceId.isBlank() || deviceProof == null ||
                !deviceProof.matches("[A-Za-z0-9_-]{43,86}")) throw denied();
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
        if (!persistence.bindDeviceProof(
                identity.tenantId(), identity.userId(), identity.deviceId(), HexFormat.of().formatHex(digest))) {
            throw denied();
        }
    }

    private static MatrixProtocolException denied() {
        return new MatrixProtocolException("M_UNKNOWN_TOKEN", "Matrix device possession could not be verified.");
    }
}
