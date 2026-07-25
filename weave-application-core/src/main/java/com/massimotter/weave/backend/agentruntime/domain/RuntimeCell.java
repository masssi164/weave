package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record RuntimeCell(
        UUID recordId,
        String organizationRef,
        String personRef,
        RuntimeMemberBinding memberBinding,
        String cellRef,
        RuntimeWorkloadBinding workloadBinding,
        RuntimeEntitlementState entitlementState,
        String entitlementRevision,
        RuntimeCellState desiredState,
        RuntimeCellState observedState,
        String runtimeProfileId,
        String runtimeProfileHash,
        String workspaceRevision,
        String workspaceManifestRef,
        String runtimeStateStoreRef,
        long fencingEpoch,
        UUID leaseId,
        Instant leaseExpiresAt,
        long version,
        String auditRef,
        Instant createdAt,
        Instant updatedAt) {

    private static final Pattern PROFILE_ID = Pattern.compile("rp_[A-Za-z0-9_-]+");
    private static final Pattern PROFILE_HASH = Pattern.compile("sha256:[a-f0-9]{64}");

    public RuntimeCell {
        Objects.requireNonNull(recordId, "recordId");
        RuntimeMemberBinding.requireText(organizationRef, "organizationRef");
        RuntimeMemberBinding.requireText(personRef, "personRef");
        Objects.requireNonNull(memberBinding, "memberBinding");
        RuntimeMemberBinding.requireText(cellRef, "cellRef");
        Objects.requireNonNull(workloadBinding, "workloadBinding");
        Objects.requireNonNull(entitlementState, "entitlementState");
        RuntimeMemberBinding.requireText(entitlementRevision, "entitlementRevision");
        Objects.requireNonNull(desiredState, "desiredState");
        Objects.requireNonNull(observedState, "observedState");
        requireOptionalPattern(runtimeProfileId, PROFILE_ID, "runtimeProfileId");
        requireOptionalPattern(runtimeProfileHash, PROFILE_HASH, "runtimeProfileHash");
        if ((runtimeProfileId == null) != (runtimeProfileHash == null)) {
            throw new IllegalArgumentException("runtime profile id and hash must be set together");
        }
        RuntimeMemberBinding.requireText(workspaceRevision, "workspaceRevision");
        RuntimeMemberBinding.requireText(workspaceManifestRef, "workspaceManifestRef");
        if (runtimeStateStoreRef == null || !runtimeStateStoreRef.startsWith("runtime-state://")) {
            throw new IllegalArgumentException("runtimeStateStoreRef must use runtime-state://");
        }
        if (fencingEpoch < 0 || version < 0) {
            throw new IllegalArgumentException("fencing epoch and version must not be negative");
        }
        if ((leaseId == null) != (leaseExpiresAt == null)) {
            throw new IllegalArgumentException("lease id and expiry must be set together");
        }
        RuntimeMemberBinding.requireText(auditRef, "auditRef");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    public static RuntimeCell provisioning(
            String organizationRef,
            String personRef,
            RuntimeMemberBinding memberBinding,
            String cellRef,
            RuntimeWorkloadBinding workloadBinding,
            String entitlementRevision,
            String workspaceRevision,
            String workspaceManifestRef,
            String runtimeStateStoreRef,
            String auditRef,
            Instant now) {
        return new RuntimeCell(UUID.randomUUID(), organizationRef, personRef, memberBinding, cellRef, workloadBinding,
                RuntimeEntitlementState.ENTITLED, entitlementRevision, RuntimeCellState.PROVISIONING,
                RuntimeCellState.ABSENT, null, null, workspaceRevision, workspaceManifestRef, runtimeStateStoreRef,
                0, null, null, 0, auditRef, now, now);
    }

    public RuntimeCell withLease(UUID nextLeaseId, Instant expiresAt, long nextFencingEpoch, long nextVersion, Instant now) {
        return new RuntimeCell(recordId, organizationRef, personRef, memberBinding, cellRef, workloadBinding,
                entitlementState, entitlementRevision, desiredState, observedState, runtimeProfileId,
                runtimeProfileHash, workspaceRevision, workspaceManifestRef, runtimeStateStoreRef, nextFencingEpoch,
                nextLeaseId, expiresAt, nextVersion, auditRef, createdAt, now);
    }

    public RuntimeCell withObservation(RuntimeCellState nextObservedState, long nextVersion, String nextAuditRef,
            Instant now) {
        return new RuntimeCell(recordId, organizationRef, personRef, memberBinding, cellRef, workloadBinding,
                entitlementState, entitlementRevision, desiredState, nextObservedState, runtimeProfileId,
                runtimeProfileHash, workspaceRevision, workspaceManifestRef, runtimeStateStoreRef, fencingEpoch,
                leaseId, leaseExpiresAt, nextVersion, nextAuditRef, createdAt, now);
    }

    private static void requireOptionalPattern(String value, Pattern pattern, String field) {
        if (value != null && !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
    }
}
