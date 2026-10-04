package com.massimotter.weave.backend.service.files;

import java.util.Set;

/**
 * Internal, support-safe partial source inspection. Counts are null when unmeasured; a blocked
 * result is never activation evidence.
 */
public record FilesAccessInspection(
        boolean davObserved,
        boolean sharesObserved,
        Integer observedShareCount,
        Integer userShareCount,
        Integer groupShareCount,
        Integer linkShareCount,
        Integer otherShareCount,
        Integer conditionedShareCount,
        boolean ownerObserved,
        boolean actorPermissionsObserved,
        boolean versionObserved,
        boolean providerFileIdObserved,
        boolean davShareIndicatorObserved,
        Set<String> blockingReasons) {

    public FilesAccessInspection {
        if (!sharesObserved) {
            if (observedShareCount != null || userShareCount != null || groupShareCount != null
                    || linkShareCount != null || otherShareCount != null || conditionedShareCount != null) {
                throw new IllegalArgumentException("unobserved shares must have unmeasured counts");
            }
        } else {
            if (observedShareCount == null || userShareCount == null || groupShareCount == null
                    || linkShareCount == null || otherShareCount == null || conditionedShareCount == null
                    || observedShareCount < 0 || userShareCount < 0 || groupShareCount < 0
                    || linkShareCount < 0 || otherShareCount < 0 || conditionedShareCount < 0
                    || observedShareCount != userShareCount + groupShareCount + linkShareCount + otherShareCount
                    || conditionedShareCount > observedShareCount) {
                throw new IllegalArgumentException("observed share counts must be consistent and nonnegative");
            }
        }
        blockingReasons = Set.copyOf(blockingReasons);
        if (blockingReasons.isEmpty()) {
            throw new IllegalArgumentException("partial access inspection cannot authorize activation");
        }
    }

    public boolean blocked() {
        return true;
    }
}
