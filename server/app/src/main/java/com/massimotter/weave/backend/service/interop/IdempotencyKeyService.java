package com.massimotter.weave.backend.service.interop;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyKeyService {

    public String key(String namespace, String stableInput) {
        String normalized = (namespace == null ? "interop" : namespace.trim().toLowerCase())
                + ":" + (stableInput == null ? "" : stableInput.trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "idem_" + HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
