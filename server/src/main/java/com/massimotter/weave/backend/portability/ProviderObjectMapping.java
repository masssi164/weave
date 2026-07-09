package com.massimotter.weave.backend.portability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public record ProviderObjectMapping(
        String domain,
        String canonicalRef,
        ProviderRef providerRef,
        String sourceVersion,
        ProviderConformanceProfile.MappingClass mappingClass,
        List<String> notes) {

    public ProviderObjectMapping {
        domain = requireText(domain, "domain");
        canonicalRef = requireText(canonicalRef, "canonical reference");
        if (providerRef == null || mappingClass == null) {
            throw new IllegalArgumentException("provider reference and mapping class are required");
        }
        sourceVersion = sourceVersion == null || sourceVersion.isBlank() ? null : sourceVersion.trim();
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public record ProviderRef(String adapterKey, String opaqueId) {
        public ProviderRef {
            adapterKey = requireText(adapterKey, "adapter key");
            opaqueId = requireText(opaqueId, "opaque provider id");
        }

        public String supportSafeRef() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest((adapterKey + ":" + opaqueId).getBytes(StandardCharsets.UTF_8));
                return "provider-ref:sha256:" + HexFormat.of().formatHex(hash, 0, 12);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
