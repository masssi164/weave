package com.massimotter.weave.core.transfer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One typed canonical object plus transfer metadata that remains provider-independent. */
public record CanonicalTransferObject<T>(
        CanonicalObjectRef reference,
        T payload,
        String payloadSha256,
        Provenance provenance,
        List<CanonicalObjectRef> dependencies,
        List<FidelityFinding> fidelityFindings) {

    public CanonicalTransferObject {
        reference = TransferValidation.require(reference, "reference");
        payload = TransferValidation.require(payload, "payload");
        payloadSha256 = TransferValidation.requireSha256(payloadSha256, "payloadSha256");
        provenance = TransferValidation.require(provenance, "provenance");
        dependencies = List.copyOf(TransferValidation.require(dependencies, "dependencies"));
        fidelityFindings = List.copyOf(TransferValidation.require(
                fidelityFindings, "fidelityFindings"));

        Set<String> dependencyKeys = new HashSet<>();
        for (CanonicalObjectRef dependency : dependencies) {
            TransferValidation.require(dependency, "dependency");
            if (reference.stableKey().equals(dependency.stableKey())) {
                throw new IllegalArgumentException("an object must not depend on itself");
            }
            if (!dependencyKeys.add(dependency.stableKey())) {
                throw new IllegalArgumentException("duplicate dependency " + dependency.stableKey());
            }
        }

        Set<String> fieldPaths = new HashSet<>();
        for (FidelityFinding finding : fidelityFindings) {
            TransferValidation.require(finding, "fidelityFinding");
            if (!fieldPaths.add(finding.fieldPath())) {
                throw new IllegalArgumentException(
                        "duplicate fidelity finding for " + finding.fieldPath());
            }
        }
    }
}
