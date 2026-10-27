package com.massimotter.weave.backend.chat.port;

import com.massimotter.weave.backend.security.port.MemberSessionStateStore;
import java.time.Instant;
import java.util.Optional;

/** Persistence port for restart-critical Matrix facade client state. */
public interface MatrixFacadeClientStateStore extends MemberSessionStateStore {

    Optional<IdentityProjection> identityProjection(
            String tenantId,
            String identityIssuer,
            String matrixUserId);

    /** Registered same-organization identities in stable order, bounded for member discovery. */
    default java.util.List<IdentityProjection> identityProjections(
            String tenantId, String identityIssuer, String afterUserId, int limit) {
        throw new IllegalStateException("Matrix identity discovery is unavailable");
    }

    void saveIdentityProjection(IdentityProjection projection);

    record IdentityProjection(
            String tenantId,
            String identityIssuer,
            String matrixUserId,
            String actorRef,
            String authorizationPrincipalRef,
            Instant updatedAt) {
    }
}
