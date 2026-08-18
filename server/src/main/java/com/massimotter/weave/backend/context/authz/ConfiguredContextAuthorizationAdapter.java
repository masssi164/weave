package com.massimotter.weave.backend.context.authz;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic internal ReBAC adapter for the Context/Space seam.
 *
 * This adapter is an immutable projection of the explicitly configured bootstrap policy and is route-free.
 * It owns no mutable runtime authority and fails closed when no configured relation grants access.
 */
public final class ConfiguredContextAuthorizationAdapter implements ContextAuthorizationPort {

    private final List<ContextMembership> memberships;
    private final List<ContextRelationTuple> tuples;
    private final List<ContextGraphEdge> edges;

    public ConfiguredContextAuthorizationAdapter(
            List<ContextMembership> memberships,
            List<ContextRelationTuple> tuples,
            List<ContextGraphEdge> edges) {
        this.memberships = List.copyOf(Objects.requireNonNull(memberships, "memberships must not be null"));
        this.tuples = List.copyOf(Objects.requireNonNull(tuples, "tuples must not be null"));
        this.edges = List.copyOf(Objects.requireNonNull(edges, "edges must not be null"));
    }

    @Override
    public ContextAuthorizationDecision check(ContextAuthorizationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        ContextAuthorizationDecision tupleDecision = checkTuples(request);
        if (tupleDecision.allowed()) {
            return tupleDecision;
        }

        ContextAuthorizationDecision membershipDecision = checkMemberships(request);
        if (membershipDecision.allowed()) {
            return membershipDecision;
        }

        return ContextAuthorizationDecision.deny("no matching context membership or relation tuple");
    }

    private ContextAuthorizationDecision checkTuples(ContextAuthorizationRequest request) {
        for (ContextRelationTuple tuple : tuples) {
            if (!sameTenant(tuple.tenantId(), request.tenantId())) {
                continue;
            }
            if (!request.matchesContextObjectRef(tuple.objectRef())) {
                continue;
            }
            if (!tuple.subjectRef().equals(request.principalRef())) {
                continue;
            }

            var relation = ContextRelation.fromWireValue(tuple.relation());
            if (relation.isEmpty()) {
                continue;
            }
            if (relation.get().grants(request.permission())) {
                return ContextAuthorizationDecision.allow("context relation tuple grants " + relation.get().wireValue());
            }
        }
        return ContextAuthorizationDecision.deny("no matching context relation tuple");
    }

    private ContextAuthorizationDecision checkMemberships(ContextAuthorizationRequest request) {
        Set<String> projectedContextIds = contextAndAncestors(request.tenantId(), request.contextId());
        for (ContextMembership membership : memberships) {
            if (!sameTenant(membership.tenantId(), request.tenantId())) {
                continue;
            }
            if (!membership.principalRef().equals(request.principalRef())) {
                continue;
            }
            if (!projectedContextIds.contains(membership.contextId())) {
                continue;
            }
            if (membership.role().grants(request.permission())) {
                if (membership.contextId().equals(request.contextId())) {
                    return ContextAuthorizationDecision.allow("direct context membership grants " + membership.role());
                }
                return ContextAuthorizationDecision.allow("projected parent context membership grants " + membership.role());
            }
        }
        return ContextAuthorizationDecision.deny("no matching context membership");
    }

    private Set<String> contextAndAncestors(String tenantId, String contextId) {
        Set<String> visible = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        visible.add(contextId);
        queue.add(contextId);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (ContextGraphEdge edge : edges) {
                if (!sameTenant(edge.tenantId(), tenantId) || !edge.projectsMembership()) {
                    continue;
                }
                if (edge.toContextId().equals(current) && visible.add(edge.fromContextId())) {
                    queue.add(edge.fromContextId());
                }
            }
        }

        return visible;
    }

    private boolean sameTenant(String left, String right) {
        return left.equals(right);
    }
}
