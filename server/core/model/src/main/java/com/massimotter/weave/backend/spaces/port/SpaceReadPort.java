package com.massimotter.weave.backend.spaces.port;

import java.util.List;
import java.util.Optional;

/** Current member-visible metadata from the same durable Space and membership authority. */
public interface SpaceReadPort {
    record SpaceSummary(String spaceRef, String displayName) {}
    List<SpaceSummary> visibleSpaces(String organizationRef, String principalRef, String afterSpaceRef, int limit);
    Optional<SpaceSummary> visibleSpace(String organizationRef, String principalRef, String spaceRef);
}
