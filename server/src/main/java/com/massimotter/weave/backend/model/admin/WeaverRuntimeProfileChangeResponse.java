package com.massimotter.weave.backend.model.admin;

public record WeaverRuntimeProfileChangeResponse(
        String version,
        String runtimeProfileHash,
        String createdAt,
        String status,
        String summary) {
    public WeaverRuntimeProfileChangeResponse {
        version = version == null || version.isBlank() ? "vNext" : version.trim();
        runtimeProfileHash = runtimeProfileHash == null || runtimeProfileHash.isBlank() ? "hash-missing" : runtimeProfileHash.trim();
        createdAt = createdAt == null ? "" : createdAt.trim();
        status = status == null || status.isBlank() ? "draft" : status.trim();
        summary = summary == null || summary.isBlank() ? "RuntimeProfile change recorded by backend." : summary.trim();
    }
}
