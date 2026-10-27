package com.massimotter.weave.backend.spaces.port;

import java.util.List;

/** Accepted local Dogfood authority, distinct from a human acting account. */
public interface DogfoodSpacePreparationPort {
    void verifyOwner(String organizationRef, String ownerAccountRef);

    /** Recheck identity prerequisites under the same root lock before either absent-first write. */
    List<Grant> grantTwo(String organizationRef, String ownerAccountRef,
            List<Target> targets, Runnable recheckIdentity, java.util.function.Consumer<List<Grant>> audit);

    record Target(String accountRef, String expectedEtag) {}

    record Grant(String accountRef, String strongEtag, boolean created) {}

    final class OwnerConflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    final class VersionConflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    final class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
