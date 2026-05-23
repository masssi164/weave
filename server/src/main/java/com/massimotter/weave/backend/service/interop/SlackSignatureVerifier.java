package com.massimotter.weave.backend.service.interop;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class SlackSignatureVerifier {

    public boolean verify(String signingSecret, String timestamp, String rawBody, String signature) {
        if (!hasText(signingSecret) || !hasText(timestamp) || rawBody == null || !hasText(signature)) {
            return false;
        }
        String base = "v0:" + timestamp + ":" + rawBody;
        String expected = "v0=" + hmacSha256(signingSecret, base);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to verify Slack signature", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
