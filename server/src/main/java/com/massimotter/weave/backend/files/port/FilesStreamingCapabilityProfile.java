package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.ContentProfile;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runtime-observed CapabilityProfile v1 projection for bounded Files content. */
public record FilesStreamingCapabilityProfile(
        String apiVersion,
        String profileRef,
        String providerRef,
        String adapterVersion,
        String conformanceVersion,
        String domain,
        Instant observedAt,
        Instant expiresAt,
        Capability streamingRead,
        Capability streamingWrite,
        ContentProfile limits,
        List<String> evidenceRefs) {

    public static final String READ = "files.content_streaming_read";
    public static final String WRITE = "files.content_streaming_write";
    public static final String EVIDENCE_REF =
            "weave:docs/evidence/native-files-bounded-streaming.md";

    public FilesStreamingCapabilityProfile {
        apiVersion = required(apiVersion, "apiVersion");
        profileRef = required(profileRef, "profileRef");
        providerRef = required(providerRef, "providerRef");
        adapterVersion = required(adapterVersion, "adapterVersion");
        conformanceVersion = required(conformanceVersion, "conformanceVersion");
        domain = required(domain, "domain");
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null")
                .truncatedTo(ChronoUnit.SECONDS);
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null")
                .truncatedTo(ChronoUnit.SECONDS);
        if (!expiresAt.isAfter(observedAt)) {
            throw new IllegalArgumentException("expiresAt must be after observedAt");
        }
        streamingRead = Objects.requireNonNull(streamingRead, "streamingRead must not be null");
        streamingWrite = Objects.requireNonNull(streamingWrite, "streamingWrite must not be null");
        evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null"));
        if (evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs must not be empty");
        }
    }

    public static FilesStreamingCapabilityProfile weaveNative(
            ContentProfile limits,
            boolean verified,
            Instant observedAt) {
        Capability capability = verified
                ? new Capability("native", "F0", true, EVIDENCE_REF,
                        "The bounded complete-representation profile is runtime-qualified; full Files F4/F5 remains open.")
                : new Capability("blocked", "F4", false, null,
                        "The bounded content profile is not currently runtime-qualified.");
        return new FilesStreamingCapabilityProfile(
                "weave.capability-profile/v1",
                "capability-profile:files:weave-native:bounded-content-v1",
                "provider:files:weave-native",
                "weave-native/bounded-content-v1",
                "weave.files-bounded-content/v1",
                "files",
                observedAt,
                observedAt.plusSeconds(60),
                capability,
                capability,
                limits,
                List.of(EVIDENCE_REF, "gap:files-weave-native-authenticated-integrated-real-http-proof"));
    }

    public static FilesStreamingCapabilityProfile blocked(
            String adapterKey,
            Instant observedAt) {
        String key = required(adapterKey, "adapterKey");
        Capability blocked = new Capability(
                "blocked",
                "F4",
                false,
                null,
                "The selected Files adapter has not qualified the bounded content profile.");
        return new FilesStreamingCapabilityProfile(
                "weave.capability-profile/v1",
                "capability-profile:files:" + key + ":bounded-content-v1",
                "provider:files:" + key,
                key + "/unqualified-bounded-content",
                "weave.files-bounded-content/v1",
                "files",
                observedAt,
                observedAt.plusSeconds(60),
                blocked,
                blocked,
                null,
                List.of("gap:files-" + key + "-bounded-content-qualification"));
    }

    /** Observes the selected runtime without exposing provider/storage failure details. */
    public static FilesStreamingCapabilityProfile observe(
            FilesProviderPort adapter,
            FilesStreamingContentPort streaming) {
        Objects.requireNonNull(adapter, "adapter must not be null");
        Objects.requireNonNull(streaming, "streaming must not be null");
        ContentProfile limits = null;
        boolean verified = false;
        try {
            limits = streaming.contentProfile();
            streaming.requireStreamingReady();
            var conformance = adapter.conformanceProfile();
            verified = "weave-native".equals(conformance.adapterKey())
                    && conformance.supports(READ)
                    && conformance.supports(WRITE);
        } catch (RuntimeException unavailable) {
            // The blocked profile deliberately carries no provider/storage failure details.
        }
        return weaveNative(limits, verified, Instant.now());
    }

    public boolean qualified() {
        return streamingRead.qualified() && streamingWrite.qualified() && limits != null;
    }

    /** JSON-compatible, support-safe projection matching CapabilityProfile v1. */
    public Map<String, Object> projection() {
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("apiVersion", apiVersion);
        projected.put("profileRef", profileRef);
        projected.put("providerRef", providerRef);
        projected.put("adapterVersion", adapterVersion);
        projected.put("conformanceVersion", conformanceVersion);
        projected.put("domain", domain);
        projected.put("observedAt", observedAt.toString());
        projected.put("expiresAt", expiresAt.toString());
        projected.put("capabilities", Map.of(
                READ, streamingRead.projection(),
                WRITE, streamingWrite.projection()));
        if (limits != null) {
            projected.put("limits", Map.of(
                    "maximumContentBytes", limits.maximumContentBytes(),
                    "transferBufferBytes", limits.transferBufferBytes(),
                    "maximumIngressConcurrency", limits.maximumIngressConcurrency(),
                    "maximumEgressConcurrency", limits.maximumEgressConcurrency()));
        }
        projected.put("deploymentModels", List.of("self-hosted"));
        projected.put("evidenceRefs", evidenceRefs);
        return Map.copyOf(projected);
    }

    public record Capability(
            String status,
            String fidelity,
            boolean verified,
            String evidenceRef,
            String notes) {

        public Capability {
            status = required(status, "status");
            fidelity = required(fidelity, "fidelity");
            notes = required(notes, "notes");
            if (verified && (evidenceRef == null || evidenceRef.isBlank())) {
                throw new IllegalArgumentException("verified capability requires evidenceRef");
            }
        }

        boolean qualified() {
            return verified
                    && ("native".equals(status) || "emulated".equals(status))
                    && ("F0".equals(fidelity) || "F1".equals(fidelity));
        }

        Map<String, Object> projection() {
            Map<String, Object> projected = new LinkedHashMap<>();
            projected.put("status", status);
            projected.put("fidelity", fidelity);
            projected.put("verified", verified);
            projected.put("notes", notes);
            if (evidenceRef != null) {
                projected.put("evidenceRef", evidenceRef);
            }
            return Map.copyOf(projected);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
