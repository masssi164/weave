package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
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

/** Integrity envelope for opaque, collection-bound RFC 6578 synchronization state tokens. */
@Component
public final class FilesWebDavSyncTokenCodec {

    static final String TOKEN_PREFIX = "urn:weave:files:sync:v1:";
    private static final String ALGORITHM = "HmacSHA256";
    private static final String KEY_CONTEXT = "weave/files-webdav-sync-token/v1/signing-key";
    private static final int PAYLOAD_VERSION = 1;
    private static final int MAXIMUM_TOKEN_CHARACTERS = 4_096;
    private static final int MAXIMUM_TEXT_BYTES = 1_024;

    private final byte[] signingKey;

    /** Derives an operation-specific key from the existing mounted identity-reference secret. */
    @Autowired
    public FilesWebDavSyncTokenCodec(IdentityOpaqueReferenceCodec references) {
        this(references.cursor("weave-files", KEY_CONTEXT).getBytes(StandardCharsets.US_ASCII));
    }

    FilesWebDavSyncTokenCodec(byte[] rootKeyMaterial) {
        byte[] source = Objects.requireNonNull(rootKeyMaterial, "rootKeyMaterial").clone();
        if (source.length < 32) {
            Arrays.fill(source, (byte) 0);
            throw new IllegalArgumentException("Files sync-token key material is too short");
        }
        try {
            Mac derivation = Mac.getInstance(ALGORITHM);
            derivation.init(new SecretKeySpec(source, ALGORITHM));
            signingKey = derivation.doFinal(KEY_CONTEXT.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Files sync-token HMAC is unavailable", exception);
        } finally {
            Arrays.fill(source, (byte) 0);
        }
    }

    public String encode(SyncTokenState state) {
        SyncTokenState required = Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(PAYLOAD_VERSION);
                writeText(output, required.organizationRef());
                writeText(output, required.spaceRef());
                writeText(output, required.collectionFileId());
                writeText(output, required.streamRef());
                output.writeLong(required.revision());
            }
            byte[] payload = bytes.toByteArray();
            byte[] signature = sign(payload);
            try {
                Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
                String token = TOKEN_PREFIX
                        + encoder.encodeToString(payload)
                        + "."
                        + encoder.encodeToString(signature);
                requireValidUri(token);
                return token;
            } finally {
                Arrays.fill(payload, (byte) 0);
                Arrays.fill(signature, (byte) 0);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Files sync-token encoding failed", exception);
        }
    }

    public SyncTokenState decode(String token) {
        try {
            requireValidUri(token);
            if (!token.startsWith(TOKEN_PREFIX)) {
                throw invalid();
            }
            String envelope = token.substring(TOKEN_PREFIX.length());
            int separator = envelope.indexOf('.');
            if (separator < 1 || separator != envelope.lastIndexOf('.')) {
                throw invalid();
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
                        throw invalid();
                    }
                } finally {
                    Arrays.fill(expectedSignature, (byte) 0);
                }
                return decodePayload(payload);
            } finally {
                Arrays.fill(payload, (byte) 0);
                Arrays.fill(suppliedSignature, (byte) 0);
            }
        } catch (InvalidSyncTokenException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException exception) {
            throw invalid();
        }
    }

    private SyncTokenState decodePayload(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != PAYLOAD_VERSION) {
                throw invalid();
            }
            SyncTokenState state = new SyncTokenState(
                    readText(input),
                    readText(input),
                    readText(input),
                    readText(input),
                    input.readLong());
            if (input.available() != 0) {
                throw invalid();
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
            throw new IllegalStateException("Files sync-token HMAC is unavailable", exception);
        }
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = required(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_TEXT_BYTES) {
            throw new IllegalArgumentException("Files sync-token field exceeds the supported bound");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > MAXIMUM_TEXT_BYTES || length > input.available()) {
            throw invalid();
        }
        byte[] bytes = input.readNBytes(length);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw invalid();
        }
        return required(value);
    }

    private static void requireCanonicalBase64(String encoded, byte[] decoded) {
        String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
        if (!MessageDigest.isEqual(
                encoded.getBytes(StandardCharsets.US_ASCII),
                canonical.getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
    }

    private static void requireValidUri(String token) {
        if (token == null
                || token.isBlank()
                || token.length() > MAXIMUM_TOKEN_CHARACTERS
                || token.chars().anyMatch(character -> character > 0x7f)) {
            throw invalid();
        }
        URI uri = URI.create(token);
        if (!uri.isAbsolute() || uri.getScheme() == null || !token.equals(uri.toASCIIString())) {
            throw invalid();
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalid();
        }
        return value;
    }

    private static InvalidSyncTokenException invalid() {
        return new InvalidSyncTokenException();
    }

    public record SyncTokenState(
            String organizationRef,
            String spaceRef,
            String collectionFileId,
            String streamRef,
            long revision) {

        public SyncTokenState {
            organizationRef = required(organizationRef);
            spaceRef = required(spaceRef);
            collectionFileId = required(collectionFileId);
            streamRef = required(streamRef);
            if (revision < 0) {
                throw invalid();
            }
        }

        public boolean matches(
                String expectedOrganizationRef,
                String expectedSpaceRef,
                String expectedCollectionFileId,
                String expectedStreamRef) {
            return organizationRef.equals(expectedOrganizationRef)
                    && spaceRef.equals(expectedSpaceRef)
                    && collectionFileId.equals(expectedCollectionFileId)
                    && streamRef.equals(expectedStreamRef);
        }
    }

    public static final class InvalidSyncTokenException extends RuntimeException {
        private InvalidSyncTokenException() {
            super("The Files synchronization token is invalid.");
        }
    }
}
