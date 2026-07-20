package com.massimotter.weave.backend.agentruntime.adapter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeProfileException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Set;
import org.erdtman.jcs.JsonCanonicalizer;

public final class Ed25519JcsRuntimeProfileVerifier implements RuntimeProfileVerifier {
    private static final int MAX_PROTECTED_LENGTH = 4096;
    private static final int MAX_PAYLOAD_LENGTH = 1_048_576;
    private static final int MAX_SIGNATURE_LENGTH = 128;

    private final ObjectMapper objectMapper;
    private final RuntimeProfileTrustKeyProvider trustKeys;

    public Ed25519JcsRuntimeProfileVerifier(ObjectMapper objectMapper, RuntimeProfileTrustKeyProvider trustKeys) {
        if (objectMapper == null || trustKeys == null) {
            throw new IllegalArgumentException("RuntimeProfile verifier dependencies are required");
        }
        this.objectMapper = objectMapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.trustKeys = trustKeys;
    }

    @Override
    public RuntimeProfile verify(SignedRuntimeProfile envelope, Instant now) {
        if (envelope == null || now == null) {
            throw invalid("missing-envelope");
        }
        try {
            byte[] protectedBytes = decode(envelope.protectedHeader(), MAX_PROTECTED_LENGTH);
            byte[] payloadBytes = decode(envelope.payload(), MAX_PAYLOAD_LENGTH);
            byte[] signatureBytes = decode(envelope.signature(), MAX_SIGNATURE_LENGTH);
            if (signatureBytes.length != 64) {
                throw invalid("invalid-signature");
            }

            JsonNode header = objectMapper.readTree(strictUtf8(protectedBytes));
            requireHeader(header, envelope.keyId());
            RuntimeProfileTrustKeyProvider.TrustKey trustKey = trustKeys.resolve(envelope.keyId(), now)
                    .filter(key -> key.validAt(now))
                    .orElseThrow(() -> invalid("untrusted-key"));

            String payloadJson = strictUtf8(payloadBytes);
            byte[] canonicalPayload = new JsonCanonicalizer(payloadJson).getEncodedUTF8();
            if (!MessageDigest.isEqual(payloadBytes, canonicalPayload)) {
                throw invalid("noncanonical-payload");
            }
            String hash = "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payloadBytes));
            if (!hash.equals(envelope.profileHash())) {
                throw invalid("profile-hash-mismatch");
            }

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(trustKey.publicKey());
            verifier.update((envelope.protectedHeader() + "." + envelope.payload())
                    .getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(signatureBytes)) {
                throw invalid("invalid-signature");
            }

            RuntimeProfile profile = RuntimeProfileJsonDecoder.decode(objectMapper.readTree(payloadJson));
            if (!profile.profileId().equals(envelope.profileId())
                    || !profile.cellRef().equals(envelope.cellRef())
                    || !profile.issuedAt().equals(envelope.issuedAt())
                    || !profile.expiresAt().equals(envelope.expiresAt())) {
                throw invalid("metadata-mismatch");
            }
            if (now.isBefore(profile.issuedAt()) || !now.isBefore(profile.expiresAt())) {
                throw invalid("profile-expired-or-not-yet-valid");
            }
            return profile;
        } catch (InvalidRuntimeProfileException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("malformed-profile");
        } catch (Exception exception) {
            throw invalid("verification-failed");
        }
    }

    private static void requireHeader(JsonNode header, String expectedKeyId) {
        if (header == null || !header.isObject()) {
            throw invalid("invalid-protected-header");
        }
        Set<String> actual = new HashSet<>();
        Iterator<String> names = header.fieldNames();
        names.forEachRemaining(actual::add);
        if (!actual.equals(Set.of("alg", "typ", "kid", "contractVersion"))
                || !"EdDSA".equals(text(header, "alg"))
                || !Ed25519JcsRuntimeProfileSigner.TYPE.equals(text(header, "typ"))
                || !RuntimeProfile.VERSION.equals(text(header, "contractVersion"))
                || !expectedKeyId.equals(text(header, "kid"))) {
            throw invalid("invalid-protected-header");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid("invalid-protected-header");
        }
        return value.textValue();
    }

    private static byte[] decode(String value, int maximumEncodedLength) {
        if (value == null || value.length() > maximumEncodedLength) {
            throw invalid("oversize-envelope");
        }
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid-base64url");
        }
    }

    private static String strictUtf8(byte[] value) throws Exception {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString();
    }

    private static InvalidRuntimeProfileException invalid(String code) {
        return new InvalidRuntimeProfileException(code);
    }
}
