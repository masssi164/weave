package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable provider- and path-independent identity for one scoped virtual Files root. */
public final class FilesRootIdentity {

    private static final String DIGEST_CONTEXT = "weave/files-root-id/v1\0";

    private FilesRootIdentity() {}

    public static FileId forScope(FilesScope scope) {
        FilesScope required = java.util.Objects.requireNonNull(scope, "scope");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DIGEST_CONTEXT.getBytes(StandardCharsets.UTF_8));
            digest.update(required.organizationRef().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(required.spaceRef().getBytes(StandardCharsets.UTF_8));
            return new FileId("files-root:" + HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
