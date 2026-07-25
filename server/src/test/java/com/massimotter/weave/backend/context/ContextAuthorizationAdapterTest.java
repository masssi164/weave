package com.massimotter.weave.backend.context;

import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextGraphEdge;
import com.massimotter.weave.backend.context.authz.ContextGraphRelation;
import com.massimotter.weave.backend.context.authz.ContextMembership;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.context.authz.ContextRelation;
import com.massimotter.weave.backend.context.authz.ContextRelationTuple;
import com.massimotter.weave.backend.context.authz.ContextRole;
import com.massimotter.weave.backend.context.authz.ConfiguredContextAuthorizationAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextAuthorizationAdapterTest {

    private static final String TENANT = "tenant-a";
    private static final String OTHER_TENANT = "tenant-b";
    private static final String WORKSPACE = "ctx-workspace";
    private static final String CHANNEL = "ctx-channel";
    private static final String MASSIMO = "user:massimo";

    @Test
    void directMembershipMapsToDeterministicViewEditAdminPermissions() {
        var adapter = new ConfiguredContextAuthorizationAdapter(
                List.of(
                        new ContextMembership(TENANT, WORKSPACE, "user:owner", ContextRole.OWNER, "keycloak"),
                        new ContextMembership(TENANT, WORKSPACE, "user:member", ContextRole.MEMBER, "keycloak"),
                        new ContextMembership(TENANT, WORKSPACE, "user:guest", ContextRole.GUEST, "invite")),
                List.of(),
                List.of());

        assertThat(adapter.check(request("user:owner", ContextPermission.ADMIN)).allowed()).isTrue();
        assertThat(adapter.check(request("user:owner", ContextPermission.EDIT)).allowed()).isTrue();
        assertThat(adapter.check(request("user:owner", ContextPermission.VIEW)).allowed()).isTrue();

        assertThat(adapter.check(request("user:member", ContextPermission.EDIT)).allowed()).isTrue();
        assertThat(adapter.check(request("user:member", ContextPermission.ADMIN)).allowed()).isFalse();

        assertThat(adapter.check(request("user:guest", ContextPermission.VIEW)).allowed()).isTrue();
        assertThat(adapter.check(request("user:guest", ContextPermission.EDIT)).allowed()).isFalse();
    }

    @Test
    void tenantIsolationIgnoresMembershipsEdgesAndTuplesFromOtherTenants() {
        var adapter = new ConfiguredContextAuthorizationAdapter(
                List.of(new ContextMembership(OTHER_TENANT, WORKSPACE, MASSIMO, ContextRole.OWNER, "keycloak")),
                List.of(ContextRelationTuple.contextTuple(OTHER_TENANT, WORKSPACE, ContextRelation.CONTEXT_ADMIN, MASSIMO)),
                List.of(new ContextGraphEdge(OTHER_TENANT, WORKSPACE, CHANNEL, ContextGraphRelation.CONTAINS)));

        assertThat(adapter.check(new ContextAuthorizationRequest(TENANT, WORKSPACE, MASSIMO, ContextPermission.ADMIN)).allowed())
                .isFalse();
        assertThat(adapter.check(new ContextAuthorizationRequest(TENANT, CHANNEL, MASSIMO, ContextPermission.VIEW)).allowed())
                .isFalse();
    }

    @Test
    void membershipProjectsFromParentContextAcrossContainsEdges() {
        var adapter = new ConfiguredContextAuthorizationAdapter(
                List.of(new ContextMembership(TENANT, WORKSPACE, MASSIMO, ContextRole.MEMBER, "keycloak")),
                List.of(),
                List.of(new ContextGraphEdge(TENANT, WORKSPACE, CHANNEL, ContextGraphRelation.CONTAINS)));

        assertThat(adapter.check(new ContextAuthorizationRequest(TENANT, CHANNEL, MASSIMO, ContextPermission.VIEW)).allowed())
                .isTrue();
        assertThat(adapter.check(new ContextAuthorizationRequest(TENANT, CHANNEL, MASSIMO, ContextPermission.EDIT)).allowed())
                .isTrue();
        assertThat(adapter.check(new ContextAuthorizationRequest(TENANT, CHANNEL, MASSIMO, ContextPermission.ADMIN)).allowed())
                .isFalse();
    }

    @Test
    void contextRelationTuplesGrantKnownRelationsAndFailClosedForUnknownRelation() {
        var adapter = new ConfiguredContextAuthorizationAdapter(
                List.of(),
                List.of(
                        ContextRelationTuple.contextTuple(TENANT, WORKSPACE, ContextRelation.CONTEXT_VIEWER, "user:viewer"),
                        new ContextRelationTuple(TENANT, "context:" + WORKSPACE, "raw_provider_admin", "user:provider", null)),
                List.of());

        assertThat(adapter.check(request("user:viewer", ContextPermission.VIEW)).allowed()).isTrue();
        assertThat(adapter.check(request("user:viewer", ContextPermission.EDIT)).allowed()).isFalse();
        assertThat(adapter.check(request("user:provider", ContextPermission.ADMIN)).allowed()).isFalse();
    }

    @Test
    void rawProviderBindingTuplesDoNotBypassContextAuthorization() {
        var adapter = new ConfiguredContextAuthorizationAdapter(
                List.of(),
                List.of(new ContextRelationTuple(TENANT, "provider_binding:openproject:project-1", "context_admin", MASSIMO, null)),
                List.of());

        assertThat(adapter.check(new ContextAuthorizationRequest(TENANT, WORKSPACE, MASSIMO, ContextPermission.ADMIN)).allowed())
                .isFalse();
    }

    @Test
    void authorizationRequestsRejectProviderBindingIdentifiersAsContextIds() {
        assertThatThrownBy(() -> new ContextAuthorizationRequest(
                        TENANT,
                        "provider_binding:openproject:project-1",
                        MASSIMO,
                        ContextPermission.VIEW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a provider binding");
    }

    private ContextAuthorizationRequest request(String principal, ContextPermission permission) {
        return new ContextAuthorizationRequest(TENANT, WORKSPACE, principal, permission);
    }
}
