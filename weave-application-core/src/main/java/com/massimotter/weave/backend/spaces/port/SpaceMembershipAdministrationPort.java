package com.massimotter.weave.backend.spaces.port;

import java.util.Set;
import java.util.List;

/** Version-fenced, member-scoped changes to current durable Space access. */
public interface SpaceMembershipAdministrationPort {
    MemberState get(String organizationRef, String spaceRef,
            String actorAccountRef, String targetAccountRef);

    List<MemberState> list(String organizationRef, String spaceRef,
            String actorAccountRef, String afterAccountRef, int limit);

    MemberState grant(String organizationRef, String spaceRef, String actorAccountRef,
            String targetAccountRef, Set<SpaceAccessPort.Permission> permissions,
            String ifMatch, boolean requireAbsent);

    void revoke(String organizationRef, String spaceRef, String actorAccountRef,
            String targetAccountRef, String ifMatch);

    record MemberState(String accountRef, Set<SpaceAccessPort.Permission> permissions,
            String strongEtag) {
        public MemberState {
            permissions = Set.copyOf(permissions);
        }
    }

    class Absent extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    class Stale extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    class Denied extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    class LastAdministrator extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
