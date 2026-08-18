package com.massimotter.weave.backend.agentruntime.port;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Read/quarantine boundary for the reserved per-cell workload-client namespace. */
public interface RuntimeWorkloadIdentityInventory {
    Snapshot scan();

    void quarantineManaged(QuarantineManagedCommand command);

    enum ManagementState {
        MANAGED,
        UNOWNED,
        MALFORMED
    }

    record ClientObservation(
            String providerRef,
            String clientId,
            boolean enabled,
            ManagementState managementState,
            String ownerFingerprint,
            String organizationFingerprint,
            String personFingerprint,
            String cellFingerprint,
            boolean serviceAccountsEnabled,
            String serviceAccountSubject,
            String authenticationMethod,
            Set<String> acceptedKeyIds) {

        private static final Pattern CLIENT_ID = Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");
        private static final Pattern FINGERPRINT = Pattern.compile("sha256:[a-f0-9]{64}");

        public ClientObservation {
            requireText(providerRef, "providerRef");
            if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
                throw new IllegalArgumentException("clientId must use the reserved per-cell namespace");
            }
            if (managementState == null) {
                throw new IllegalArgumentException("managementState is required");
            }
            if (managementState == ManagementState.MANAGED) {
                requireFingerprint(ownerFingerprint, "ownerFingerprint");
                requireFingerprint(organizationFingerprint, "organizationFingerprint");
                requireFingerprint(personFingerprint, "personFingerprint");
                requireFingerprint(cellFingerprint, "cellFingerprint");
            }
            if (authenticationMethod == null || authenticationMethod.isBlank()) {
                authenticationMethod = "unknown";
            }
            acceptedKeyIds = acceptedKeyIds == null
                    ? Set.of()
                    : Set.copyOf(new LinkedHashSet<>(acceptedKeyIds));
        }

        private static void requireFingerprint(String value, String field) {
            if (value == null || !FINGERPRINT.matcher(value).matches()) {
                throw new IllegalArgumentException(field + " must be a sha256 fingerprint");
            }
        }
    }

    record Snapshot(String revision, List<ClientObservation> clients) {
        private static final Pattern FINGERPRINT = Pattern.compile("sha256:[a-f0-9]{64}");

        public Snapshot {
            if (revision == null || !FINGERPRINT.matcher(revision).matches()) {
                throw new IllegalArgumentException("inventory revision must be a sha256 fingerprint");
            }
            clients = clients == null
                    ? List.of()
                    : clients.stream()
                            .sorted(Comparator.comparing(ClientObservation::clientId)
                                    .thenComparing(ClientObservation::providerRef))
                            .toList();
            if (clients.stream().map(ClientObservation::providerRef).distinct().count() != clients.size()) {
                throw new IllegalArgumentException("inventory provider references must be unique");
            }
        }
    }

    record QuarantineManagedCommand(
            String providerRef,
            String clientId,
            String ownerFingerprint,
            String auditRef) {
        public QuarantineManagedCommand {
            requireText(providerRef, "providerRef");
            if (clientId == null || !clientId.matches("weaver-cell-[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("clientId must use the reserved per-cell namespace");
            }
            if (ownerFingerprint == null || !ownerFingerprint.matches("sha256:[a-f0-9]{64}")) {
                throw new IllegalArgumentException("ownerFingerprint must be a sha256 fingerprint");
            }
            requireText(auditRef, "auditRef");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
