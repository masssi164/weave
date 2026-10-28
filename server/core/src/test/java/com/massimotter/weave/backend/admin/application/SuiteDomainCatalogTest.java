package com.massimotter.weave.backend.admin.application;

import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SuiteDomainCatalogTest {
    @Test
    void omittedDomainsDoNotCreateReadinessGroups() {
        assertThat(new SuiteDomainCatalog(List.of()).groups()).isEmpty();
        var only = new SuiteDomainCatalog(List.of(fragment("one", 0, "one"))).groups().getFirst();
        assertThat(only.definition().providerCategoryKeys()).containsExactly("one");
        assertThat(only.definition().readCapabilities()).containsExactly("one.read");
    }

    @Test
    void explicitIndependentFragmentsMergeInBusinessOrder() {
        var catalog = new SuiteDomainCatalog(List.of(fragment("two", 1, "two"), fragment("one", 0, "one")));
        assertThat(catalog.groups()).hasSize(1);
        assertThat(catalog.groups().getFirst().definition().providerCategoryKeys()).containsExactly("one", "two");
        assertThat(catalog.groups().getFirst().definition().canonicalObjectKinds()).containsExactly("one", "two");
    }

    @Test
    void duplicateOwnerAndConflictingGroupMetadataFail() {
        var one = fragment("one", 0, "one");
        assertThatThrownBy(() -> new SuiteDomainCatalog(List.of(one, one)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        var conflict = new SuiteDomainDeclaration("other", 2, 1, one.definition(), "source", List.of(), "action");
        assertThatThrownBy(() -> new SuiteDomainCatalog(List.of(one, conflict)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Conflicting");
    }

    private SuiteDomainDeclaration fragment(String owner, int order, String category) {
        return new SuiteDomainDeclaration(owner, 1, order,
                new SuiteDomainDefinition("group", "Group", List.of(category),
                        List.of(category + ".read"), List.of(), List.of(category)),
                "source", List.of(), "action");
    }
}
