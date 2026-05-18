package com.massimotter.weave.backend.service.connector;

import com.massimotter.weave.backend.config.ConnectorRuntimeProperties;
import com.massimotter.weave.backend.model.connector.ConnectorBoundaryResponse;
import com.massimotter.weave.backend.model.connector.ConnectorManifestValidationRequest;
import com.massimotter.weave.backend.model.connector.ConnectorManifestValidationResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConnectorRuntimeService {

    private final ConnectorRuntimeProperties properties;

    public ConnectorRuntimeService(ConnectorRuntimeProperties properties) {
        this.properties = properties;
    }

    public ConnectorBoundaryResponse boundary() {
        return new ConnectorBoundaryResponse(
                properties.publicSdkEnabled(),
                properties.publicSdkEnabled() ? "experimental-internal" : "deferred",
                List.of(
                        "Connectors run server-side only; Flutter clients never load connector code.",
                        "Credentials are brokered through secret references and never serialized in manifests.",
                        "Context-scoped capabilities, cursors, webhooks, commands, support-safe errors, and redaction policy are required before live provider promotion.",
                        "Provider writes and agentic/team writes stay disabled unless a later promotion spec explicitly enables them."),
                List.of("OpenProject read-sync contract proof", "audit and consent gates", "signed package and pinned dependency policy"));
    }

    public ConnectorManifestValidationResponse validate(ConnectorManifestValidationRequest request) {
        List<String> errors = new ArrayList<>();
        if (request.capabilities() == null || request.capabilities().isEmpty()) {
            errors.add("capabilities must declare at least one scoped capability");
        }
        if (request.releaseStatus() != null && !List.of("disabled", "skeleton", "read-sync-preview").contains(request.releaseStatus())) {
            errors.add("releaseStatus must be disabled, skeleton, or read-sync-preview until live provider promotion is specified");
        }
        if (request.providerWritesEnabled() != null && request.providerWritesEnabled()) {
            errors.add("providerWritesEnabled must remain false until a promotion spec enables writes");
        }
        if (request.redactionPolicy() == null || request.redactionPolicy().isBlank()) {
            errors.add("redactionPolicy must declare how support bundles redact provider data");
        } else if (!request.redactionPolicy().contains("redacted")) {
            errors.add("redactionPolicy must be support-safe and redacted");
        }
        if (request.cursorRefs() == null || request.cursorRefs().isEmpty()) {
            errors.add("cursorRefs must declare at least one cursor or sync reference");
        }
        if (request.secretRefs() != null) {
            request.secretRefs().forEach((name, ref) -> {
                if (ref == null || ref.isBlank()) {
                    errors.add("secretRefs." + name + " must be a non-empty secret reference");
                } else if (looksLikeSecretValue(ref)) {
                    errors.add("secretRefs." + name + " appears to contain secret material instead of a reference");
                }
            });
        }
        return new ConnectorManifestValidationResponse(
                errors.isEmpty(),
                false,
                false,
                List.copyOf(errors),
                List.of("Public connector SDK remains deferred; internal skeleton validation exists to prove OpenProject-first read-sync before live provider promotion."));
    }

    private boolean looksLikeSecretValue(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("xox") || lower.startsWith("sk-") || lower.contains("bearer ") || value.length() > 80;
    }
}
