package com.massimotter.weave.backend.identity;

import com.massimotter.weave.backend.model.IdentityKeyFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable, provider-neutral projections of the authoritative issuer+subject identity key. */
public final class IdentityReferences {
    private IdentityReferences() {
    }

    public static String primaryIdentityKey(String issuer, String subject) {
        requireSegment(issuer, "issuer", IdentityKeyFormat.MAX_ISSUER_LENGTH);
        requireSegment(subject, "subject", IdentityKeyFormat.MAX_SUBJECT_LENGTH);
        return "issuer+subject:" + issuer + "#" + subject;
    }

    public static String accountId(String issuer, String subject) {
        String primaryIdentityKey = primaryIdentityKey(issuer, subject);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(primaryIdentityKey.getBytes(StandardCharsets.UTF_8));
            return "acct_" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required for stable account identifiers", impossible);
        }
    }

    private static void requireSegment(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength
                || value.indexOf('#') >= 0 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(field + " is not a valid immutable identity-key segment");
        }
    }
}
