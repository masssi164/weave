package com.massimotter.weave.backend.agentruntime.port;

import java.security.PrivateKey;
import java.security.PublicKey;

public interface RuntimeProfileSigningKeyProvider {
    SigningKey activeKey();

    record SigningKey(String keyId, PrivateKey privateKey, PublicKey publicKey) {
        public SigningKey {
            if (keyId == null || keyId.isBlank() || privateKey == null || publicKey == null) {
                throw new IllegalArgumentException("complete runtime-profile signing key material is required");
            }
            if (!"EdDSA".equalsIgnoreCase(privateKey.getAlgorithm())
                    && !"Ed25519".equalsIgnoreCase(privateKey.getAlgorithm())) {
                throw new IllegalArgumentException("runtime profiles require an Ed25519 private key");
            }
        }
    }
}
