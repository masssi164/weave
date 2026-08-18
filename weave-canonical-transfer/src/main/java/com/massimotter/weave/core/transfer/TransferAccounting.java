package com.massimotter.weave.core.transfer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Machine-checkable proof that every declared provider source field has one outcome. */
public final class TransferAccounting {

    private TransferAccounting() {
    }

    public static Report verifyExpectedFields(
            Map<CanonicalObjectRef, Set<String>> expectedSourceFields,
            CanonicalTransferBatch<?> batch) {
        TransferValidation.require(expectedSourceFields, "expectedSourceFields");
        TransferValidation.require(batch, "batch");

        Set<FieldRef> expected = new HashSet<>();
        for (Map.Entry<CanonicalObjectRef, Set<String>> entry : expectedSourceFields.entrySet()) {
            CanonicalObjectRef objectRef = TransferValidation.require(entry.getKey(), "objectRef");
            Set<String> fieldPaths = Set.copyOf(TransferValidation.require(
                    entry.getValue(), "fieldPaths"));
            for (String fieldPath : fieldPaths) {
                expected.add(new FieldRef(
                        objectRef,
                        TransferValidation.requireText(fieldPath, "fieldPath")));
            }
        }

        Map<FieldRef, FidelityFinding> outcomes = new HashMap<>();
        for (CanonicalTransferObject<?> object : batch.objects()) {
            for (FidelityFinding finding : object.fidelityFindings()) {
                FieldRef fieldRef = new FieldRef(object.reference(), finding.fieldPath());
                FidelityFinding previous = outcomes.put(fieldRef, finding);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate field outcome " + fieldRef);
                }
            }
        }

        Set<FieldRef> actual = outcomes.keySet();
        Set<FieldRef> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        Set<FieldRef> unexpected = new HashSet<>(actual);
        unexpected.removeAll(expected);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new IllegalStateException(
                    "unaccounted transfer fields; missing=" + missing + ", unexpected=" + unexpected);
        }

        EnumMap<FidelityFinding.Classification, Long> counts =
                new EnumMap<>(FidelityFinding.Classification.class);
        for (FidelityFinding.Classification classification
                : FidelityFinding.Classification.values()) {
            counts.put(classification, 0L);
        }
        for (FidelityFinding finding : outcomes.values()) {
            counts.compute(finding.classification(), (ignored, current) -> current == null ? 1L : current + 1L);
        }

        return new Report(
                Collections.unmodifiableMap(new HashMap<>(outcomes)),
                Collections.unmodifiableMap(counts));
    }

    public record FieldRef(CanonicalObjectRef objectRef, String fieldPath) {
        public FieldRef {
            objectRef = TransferValidation.require(objectRef, "objectRef");
            fieldPath = TransferValidation.requireText(fieldPath, "fieldPath");
        }
    }

    public record Report(
            Map<FieldRef, FidelityFinding> outcomes,
            Map<FidelityFinding.Classification, Long> counts) {
        public Report {
            outcomes = Map.copyOf(TransferValidation.require(outcomes, "outcomes"));
            counts = Map.copyOf(TransferValidation.require(counts, "counts"));
        }

        public long count(FidelityFinding.Classification classification) {
            return counts.getOrDefault(classification, 0L);
        }
    }
}
