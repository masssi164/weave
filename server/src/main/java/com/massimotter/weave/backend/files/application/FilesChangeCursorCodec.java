package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Integrity envelope for stateless, scope-bound native Files change continuations. */
@Component
public final class FilesChangeCursorCodec {

    static final String TOKEN_PREFIX = "files-change-cursor/v1.";
    private static final String ALGORITHM = "HmacSHA256";
    private static final String KEY_CONTEXT = "weave/files-change-cursor/v1/signing-key";
    private static final int PAYLOAD_VERSION = 1;
    private static final int MAXIMUM_TOKEN_CHARACTERS = 4_096;
    private static final int MAXIMUM_SCOPE_BYTES = 1_024;
    private static final int MAXIMUM_LIMIT = 100;

    private final byte[] signingKey;

    /**
     * Reuses the already configured mounted identity-reference SecretRef through a one-way,
     * purpose-specific derivation. No raw configured secret is exposed to this codec.
     */
    @Autowired
    public FilesChangeCursorCodec(IdentityOpaqueReferenceCodec references) {
        this(references.cursor("weave-files", KEY_CONTEXT).getBytes(StandardCharsets.US_ASCII));
    }

    FilesChangeCursorCodec(byte[] rootKeyMaterial) {
        byte[] source = Objects.requireNonNull(rootKeyMaterial, "rootKeyMaterial").clone();
        if (source.length < 32) {
            Arrays.fill(source, (byte) 0);
            throw new IllegalArgumentException("Files change cursor key material is too short");
        }
        try {
            Mac derivation = Mac.getInstance(ALGORITHM);
            derivation.init(new SecretKeySpec(source, ALGORITHM));
            this.signingKey = derivation.doFinal(KEY_CONTEXT.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Files change continuation HMAC is unavailable", exception);
        } finally {
            Arrays.fill(source, (byte) 0);
        }
    }

    String encode(CursorState state) {
        CursorState required = Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(PAYLOAD_VERSION);
                writeText(output, required.organizationRef());
                writeText(output, required.spaceRef());
                output.writeLong(required.capturedHighWater());
                output.writeLong(required.lastReturnedRevision());
                output.writeInt(required.limit());
            }
            byte[] payload = bytes.toByteArray();
            byte[] signature = sign(payload);
            try {
                Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
                return TOKEN_PREFIX
                        + encoder.encodeToString(payload)
                        + "."
                        + encoder.encodeToString(signature);
            } finally {
                Arrays.fill(payload, (byte) 0);
                Arrays.fill(signature, (byte) 0);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Files change continuation encoding failed", exception);
        }
    }

    CursorState decode(String token) {
        try {
            if (token == null
                    || token.isBlank()
                    || token.length() > MAXIMUM_TOKEN_CHARACTERS
                    || !token.startsWith(TOKEN_PREFIX)) {
                throw FilesChangeReadException.invalidContinuation();
            }
            String envelope = token.substring(TOKEN_PREFIX.length());
            int separator = envelope.indexOf('.');
            if (separator < 1 || separator != envelope.lastIndexOf('.')) {
                throw FilesChangeReadException.invalidContinuation();
            }
            String encodedPayload = envelope.substring(0, separator);
            String encodedSignature = envelope.substring(separator + 1);
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] payload = decoder.decode(encodedPayload);
            byte[] suppliedSignature = decoder.decode(encodedSignature);
            try {
                requireCanonicalBase64(encodedPayload, payload);
                requireCanonicalBase64(encodedSignature, suppliedSignature);
                byte[] expectedSignature = sign(payload);
                try {
                    if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                        throw FilesChangeReadException.invalidContinuation();
                    }
                } finally {
                    Arrays.fill(expectedSignature, (byte) 0);
                }
                return decodePayload(payload);
            } finally {
                Arrays.fill(payload, (byte) 0);
                Arrays.fill(suppliedSignature, (byte) 0);
            }
        } catch (FilesChangeReadException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException exception) {
            throw FilesChangeReadException.invalidContinuation();
        }
    }

    private CursorState decodePayload(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != PAYLOAD_VERSION) {
                throw FilesChangeReadException.invalidContinuation();
            }
            CursorState state = new CursorState(
                    readText(input),
                    readText(input),
                    input.readLong(),
                    input.readLong(),
                    input.readInt());
            if (input.available() != 0) {
                throw FilesChangeReadException.invalidContinuation();
            }
            return state;
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, ALGORITHM));
            mac.update(TOKEN_PREFIX.getBytes(StandardCharsets.US_ASCII));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Files change continuation HMAC is unavailable", exception);
        }
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > MAXIMUM_SCOPE_BYTES) {
            throw new IllegalArgumentException("Files change cursor scope is invalid");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > MAXIMUM_SCOPE_BYTES || length > input.available()) {
            throw FilesChangeReadException.invalidContinuation();
        }
        byte[] bytes = input.readNBytes(length);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.isBlank() || !Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw FilesChangeReadException.invalidContinuation();
        }
        return value;
    }

    private static void requireCanonicalBase64(String encoded, byte[] decoded) {
        String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
        if (!MessageDigest.isEqual(
                encoded.getBytes(StandardCharsets.US_ASCII),
                canonical.getBytes(StandardCharsets.US_ASCII))) {
            throw FilesChangeReadException.invalidContinuation();
        }
    }

    record CursorState(
            String organizationRef,
            String spaceRef,
            long capturedHighWater,
            long lastReturnedRevision,
            int limit) {

        CursorState {
            organizationRef = required(organizationRef);
            spaceRef = required(spaceRef);
            if (capturedHighWater < 1
                    || lastReturnedRevision < 0
                    || lastReturnedRevision >= capturedHighWater
                    || limit < 1
                    || limit > MAXIMUM_LIMIT) {
                throw FilesChangeReadException.invalidContinuation();
            }
        }

        private static String required(String value) {
            if (value == null || value.isBlank() || !value.equals(value.trim())) {
                throw FilesChangeReadException.invalidContinuation();
            }
            return value;
        }
    }
}
