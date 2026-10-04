package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Derives restart-stable native Files acquire tokens from the mounted HMAC authority. */
@Component
public final class NativeFilesLockTokenCodec {

    private static final String TOKEN_PREFIX = "opaquelocktoken:";
    private static final String KEY_CONTEXT = "weave/files-lock-token/v1";

    private final IdentityOpaqueReferenceCodec references;

    public NativeFilesLockTokenCodec(IdentityOpaqueReferenceCodec references) {
        this.references = Objects.requireNonNull(references, "references");
    }

    public String acquireToken(String organizationRef, String operationRef) {
        return TOKEN_PREFIX + references.cursor(
                required(organizationRef, "organizationRef"),
                KEY_CONTEXT + '\u001f' + required(operationRef, "operationRef"));
    }

    public String digest(String token) {
        return FilesDigests.sha256(required(token, "token"));
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
