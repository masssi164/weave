package com.massimotter.weave.backend.admin.application;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import com.massimotter.weave.backend.admin.domain.CoreAdminMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AdminCapabilityCatalogTest {
    @Test
    void sharedOnlyPolicyDoesNotInventOptionalDomainGrants() {
        var catalog = new AdminCapabilityCatalog(List.of(CoreAdminMetadata.capabilities()));
        assertThat(catalog.knownCapabilities()).contains("admin.policy.edit", "agent-runtime.entitled")
                .doesNotContain("files.read", "chat.send");
        assertThat(catalog.grants(List.of("member", "guest"))).isEmpty();
        assertThat(catalog.profiles().get("member-default")).isEmpty();
        assertThat(catalog.profiles().get("workspace-admin"))
                .containsExactly("admin.provider.configure", "admin.policy.edit");
    }

    @Test
    void explicitProfileOrderAndRoleGrantsArePreserved() {
        var first = new AdminCapabilityDeclaration("first", 10, List.of("first.read"),
                List.of("first.read"), List.of(), List.of("first.read"),
                List.of(new ProfileGrant("member-default", 20, List.of("first.read"))));
        var second = new AdminCapabilityDeclaration("second", 20, List.of("second.read"),
                List.of("second.read"), List.of(), List.of(),
                List.of(new ProfileGrant("member-default", 10, List.of("second.read"))));
        var catalog = new AdminCapabilityCatalog(List.of(second, first));
        assertThat(catalog.grants(List.of("member"))).containsExactly("first.read");
        assertThat(catalog.grants(List.of("admin"))).containsExactly("first.read", "second.read");
        assertThat(catalog.profiles().get("member-default")).containsExactly("second.read", "first.read");
    }

    @Test
    void duplicateOwnersAndConflictingCapabilityOwnershipFail() {
        var core = CoreAdminMetadata.capabilities();
        assertThatThrownBy(() -> new AdminCapabilityCatalog(List.of(core, core)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owner");
        var conflicting = new AdminCapabilityDeclaration("another", 1, List.of("admin.policy.edit"),
                List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> new AdminCapabilityCatalog(List.of(core, conflicting)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ownership");
    }

    @Test
    void undeclaredRoleOrProfileGrantsCannotEscapeOwnerBoundary() {
        var invalid = new AdminCapabilityDeclaration("owner", 1, List.of("allowed"),
                List.of(), List.of(), List.of(), List.of(new ProfileGrant("member-default", 1, List.of("other"))));
        assertThatThrownBy(() -> new AdminCapabilityCatalog(List.of(invalid)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("declaring owner");
    }
}
