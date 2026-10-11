package com.massimotter.weave.backend.transfer.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.massimotter.weave.backend.transfer.domain.CanonicalObjectId;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;

/** Enforces one explicit, non-conflicting portability outcome per canonical object field. */
public final class LossAccounting {
    private LossAccounting() {
    }

    public static void validateObjectReferences(
            Collection<CanonicalObjectId> knownObjectIds,
            List<LossRecord> losses) {
        Set<CanonicalObjectId> known = Set.copyOf(knownObjectIds);
        for (LossRecord loss : losses) {
            if (!known.contains(loss.objectId())) {
                throw new IllegalStateException(
                        "loss record references an object outside the current canonical batch: "
                                + loss.objectId().value());
            }
        }
    }

    public static List<LossRecord> mergeAndValidate(List<List<LossRecord>> groups) {
        Map<String, LossRecord> byObjectAndField = new LinkedHashMap<>();
        for (List<LossRecord> group : groups) {
            for (LossRecord candidate : group) {
                String key = candidate.objectId().value() + '\u0000' + candidate.field();
                LossRecord current = byObjectAndField.putIfAbsent(key, candidate);
                if (current != null
                        && (current.classification() != candidate.classification()
                        || !current.reason().equals(candidate.reason()))) {
                    throw new IllegalStateException(
                            "conflicting loss classification for "
                                    + candidate.objectId().value()
                                    + "/"
                                    + candidate.field());
                }
            }
        }
        List<LossRecord> merged = new ArrayList<>(byObjectAndField.values());
        merged.sort(Comparator
                .comparing((LossRecord loss) -> loss.objectId().value())
                .thenComparing(LossRecord::field));
        return List.copyOf(merged);
    }
}
