package com.massimotter.weave.backend.spaces.port;

import java.util.List;

/** Current durable Space membership, independent of HTTP and persistence types. */
public interface SpaceAccessPort {
    enum Permission { VIEW, EDIT, ADMIN }

    boolean allows(String organizationRef, String spaceRef, String principalRef, Permission permission);

    /** Visible active Space IDs after an opaque stable Space ID, in ascending order. */
    List<String> visibleSpaceRefs(String organizationRef, String principalRef, String afterSpaceRef, int limit);
}
