package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.context.authz.ContextGraphEdge;
import com.massimotter.weave.backend.context.authz.ContextGraphRelation;
import com.massimotter.weave.backend.context.authz.ContextMembership;
import com.massimotter.weave.backend.context.authz.ContextRelationTuple;
import com.massimotter.weave.backend.context.authz.ContextRole;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap source for the Context/Space authorization graph.
 *
 * <p>Production remains fail-closed by default: no memberships, tuples, or edges are created unless the
 * deployment explicitly provides them. Local/dev/live-E2E stacks may seed deterministic Context Graph facts
 * through environment variables while preserving the same ReBAC enforcement path as production.
 */
@ConfigurationProperties(prefix = "weave.context.authorization")
public record ContextAuthorizationProperties(
        String tenantClaim,
        String tenantFallbackClaim,
        String defaultTenantId,
        String principalClaim,
        String principalRefPrefix,
        List<Membership> memberships,
        List<RelationTuple> relationTuples,
        List<GraphEdge> graphEdges) {

    public ContextAuthorizationProperties {
        tenantClaim = defaultIfBlank(tenantClaim, "weave_tenant_id");
        tenantFallbackClaim = defaultIfBlank(tenantFallbackClaim, "tenant_id");
        defaultTenantId = defaultIfBlank(defaultTenantId, "tenant-default");
        principalClaim = defaultIfBlank(principalClaim, "sub");
        principalRefPrefix = defaultIfBlank(principalRefPrefix, "user:");
        memberships = memberships == null ? List.of() : List.copyOf(memberships);
        relationTuples = relationTuples == null ? List.of() : List.copyOf(relationTuples);
        graphEdges = graphEdges == null ? List.of() : List.copyOf(graphEdges);
    }

    public List<ContextMembership> toMemberships() {
        return memberships.stream()
                .map(Membership::toDomain)
                .toList();
    }

    public List<ContextRelationTuple> toRelationTuples() {
        return relationTuples.stream()
                .map(RelationTuple::toDomain)
                .toList();
    }

    public List<ContextGraphEdge> toGraphEdges() {
        return graphEdges.stream()
                .map(GraphEdge::toDomain)
                .toList();
    }

    public String principalRef(String claimValue) {
        if (claimValue == null || claimValue.isBlank()) {
            return null;
        }
        String normalized = claimValue.trim();
        if (normalized.startsWith(principalRefPrefix)) {
            return normalized;
        }
        return principalRefPrefix + normalized;
    }

    private static String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    public record Membership(String tenantId, String contextId, String principalRef, ContextRole role, String source) {
        ContextMembership toDomain() {
            return new ContextMembership(tenantId, contextId, principalRef, role, source);
        }
    }

    public record RelationTuple(String tenantId, String objectRef, String relation, String subjectRef, String caveat) {
        ContextRelationTuple toDomain() {
            return new ContextRelationTuple(tenantId, objectRef, relation, subjectRef, caveat);
        }
    }

    public record GraphEdge(String tenantId, String fromContextId, String toContextId, ContextGraphRelation relation) {
        ContextGraphEdge toDomain() {
            return new ContextGraphEdge(tenantId, fromContextId, toContextId, relation);
        }
    }
}
