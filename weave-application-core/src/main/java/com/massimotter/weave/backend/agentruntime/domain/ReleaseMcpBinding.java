package com.massimotter.weave.backend.agentruntime.domain;

import com.massimotter.weave.backend.identity.IdentityReferences;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Protected, revocable workload-to-member binding for the bounded release MCP catalog. */
public record ReleaseMcpBinding(
        String bindingRef,
        String workloadIssuer,
        String workloadSubject,
        String workloadClientId,
        String organizationRef,
        String personRef,
        RuntimeMemberBinding memberBinding,
        String revision,
        Instant expiresAt,
        Set<String> allowedToolClasses,
        boolean active) {

    private static final Set<String> RELEASE_TOOLS =
            Set.of("files.read", "calendar.read", "calendar.write");

    public ReleaseMcpBinding {
        requireText(bindingRef, "bindingRef");
        if (!bindingRef.matches("mcp-binding:[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("bindingRef must use the MCP release namespace");
        }
        RuntimeMemberBinding.requireHttpsUri(workloadIssuer, "workloadIssuer");
        requireText(workloadSubject, "workloadSubject");
        requireText(workloadClientId, "workloadClientId");
        if (!workloadClientId.matches("weaver-(?:mcp|cell)-[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("workloadClientId must use an approved workload namespace");
        }
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        Objects.requireNonNull(memberBinding, "memberBinding");
        if (!personRef.equals(IdentityReferences.accountId(
                memberBinding.issuer(), memberBinding.subject()))) {
            throw new IllegalArgumentException("personRef must match the immutable member identity");
        }
        if (revision == null || !revision.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("binding revision must be a SHA-256 reference");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        allowedToolClasses = Set.copyOf(Objects.requireNonNull(allowedToolClasses, "allowedToolClasses"));
        if (allowedToolClasses.isEmpty() || !RELEASE_TOOLS.containsAll(allowedToolClasses)) {
            throw new IllegalArgumentException("only bounded release tool classes may be bound");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw new IllegalArgumentException(field + " is required and bounded");
        }
    }
}
