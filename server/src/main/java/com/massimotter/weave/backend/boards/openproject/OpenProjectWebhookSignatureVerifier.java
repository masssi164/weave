package com.massimotter.weave.backend.boards.openproject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies OpenProject outgoing webhook signatures before any webhook payload can
 * be normalized into Weave Boards events. OpenProject signs the raw JSON request
 * body with HMAC-SHA1 and sends it as {@code X-OP-Signature: sha1=<hex>}.
 */
public final class OpenProjectWebhookSignatureVerifier {

    public static final String SIGNATURE_HEADER = "X-OP-Signature";

    private static final String SIGNATURE_PREFIX = "sha1=";
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    public boolean verify(String signatureHeader, byte[] rawBody, String signatureSecret) {
        if (signatureHeader == null || signatureHeader.isBlank()
                || rawBody == null
                || signatureSecret == null || signatureSecret.isBlank()) {
            return false;
        }
        String signature = signatureHeader.trim();
        if (!signature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        byte[] expected = hmacSha1(rawBody, signatureSecret.trim());
        byte[] supplied = decodeHex(signature.substring(SIGNATURE_PREFIX.length()));
        return supplied.length == expected.length && MessageDigest.isEqual(supplied, expected);
    }

    public String signForTest(byte[] rawBody, String signatureSecret) {
        if (rawBody == null || signatureSecret == null || signatureSecret.isBlank()) {
            throw new IllegalArgumentException("rawBody and signatureSecret are required");
        }
        return SIGNATURE_PREFIX + HexFormat.of().formatHex(hmacSha1(rawBody, signatureSecret.trim()));
    }

    private byte[] hmacSha1(byte[] rawBody, String signatureSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(rawBody);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify OpenProject webhook signature", exception);
        }
    }

    private byte[] decodeHex(String value) {
        String hex = value == null ? "" : value.trim();
        if (hex.length() != 40 || !hex.matches("[0-9a-fA-F]+")) {
            return new byte[0];
        }
        return HexFormat.of().parseHex(hex.toLowerCase());
    }
}
