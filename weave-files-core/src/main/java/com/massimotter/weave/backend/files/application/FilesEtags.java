package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/** Facade-owned strong ETag derivation shared by planning, journal snapshots, and WebDAV. */
public final class FilesEtags {

    private FilesEtags() {
    }

    public static String strong(FileObject item, FileVersion version) {
        FileObject required = Objects.requireNonNull(item, "item must not be null");
        String material = String.join("|",
                required.path().value(),
                required.kind().name(),
                String.valueOf(required.size()),
                required.modifiedAt() == null ? "" : required.modifiedAt().toString(),
                required.mediaType() == null ? "" : required.mediaType(),
                version == null || !version.known() ? "" : version.value());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return "\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash) + "\"";
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 digest is required for Files ETags", impossible);
        }
    }
}
