package com.massimotter.weave.backend.spaces.relationship;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** One installed domain's current, authorized direct Space relationships. */
public interface SpaceRelationshipContribution {
    String domain();
    String relationPrefix();
    String targetKind();
    boolean validTargetId(String value);
    List<Target> list(Context context, String afterTargetId, int limit);

    record Context(Jwt jwt, String organizationId, String spaceRef, String principalRef) {
        @Override public String toString() { return "SpaceRelationshipContext[redacted]"; }
    }
    record Target(String cursorId, String targetRef) {}

    /** No provider details cross the shared Space error boundary. */
    final class Unavailable extends RuntimeException {
        public Unavailable() { super("Current resource access could not be verified."); }
    }
}
