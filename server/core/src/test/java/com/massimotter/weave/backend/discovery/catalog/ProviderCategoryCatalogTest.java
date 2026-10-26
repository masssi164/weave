package com.massimotter.weave.backend.discovery.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderCategoryCatalogTest {
    @Test
    void explicitFragmentsMergeWithoutCreatingARequiredDomainDependency() {
        var editor = fragment("editor", "documents", 1, Set.of(ProviderModule.OFFICE), List.of("editor"), true);
        var contacts = fragment("contacts", "documents", 1,
                Set.of(ProviderModule.CONTACTS, ProviderModule.FORMS), List.of(), true);
        var combined = new ProviderCategoryCatalog(List.of(editor, contacts));
        assertThat(combined.statusCategory("documents").modules())
                .containsExactlyInAnyOrder(ProviderModule.OFFICE, ProviderModule.CONTACTS, ProviderModule.FORMS);
        assertThat(combined.statusCategory("documents").contract().defaultAdapters()).containsExactly("editor");
        var contactsOnly = new ProviderCategoryCatalog(List.of(contacts));
        assertThat(contactsOnly.categoryForModule(ProviderModule.OFFICE)).isEmpty();
        assertThat(contactsOnly.statusCategory("documents").contract().defaultAdapters()).isEmpty();
        assertThat(contactsOnly.categoryKeys()).containsExactly("documents");
    }

    @Test
    void rejectedDuplicateOwnersAndModuleClaimsCannotSilentlyOverwriteDeclarations() {
        var first = fragment("owner", "one", 0, Set.of(ProviderModule.FILES), List.of(), true);
        assertThatThrownBy(() -> new ProviderCategoryCatalog(List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate category fragment");
        var other = fragment("other", "two", 1, Set.of(ProviderModule.FILES), List.of(), true);
        assertThatThrownBy(() -> new ProviderCategoryCatalog(List.of(first, other)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("module ownership");
    }

    @Test
    void inconsistentSharedCategoryPoliciesAreRejected() {
        var first = fragment("owner", "one", 0, Set.of(), List.of(), true);
        var conflict = fragment("other", "one", 0, Set.of(), List.of(), false);
        assertThatThrownBy(() -> new ProviderCategoryCatalog(List.of(first, conflict)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Conflicting category policy");
    }

    @Test
    void statusOnlyCategoriesAreNotSelectableAndUnknownMetadataCannotProduceAFallbackContract() {
        var catalog = new ProviderCategoryCatalog(List.of(
                fragment("owner", "status", 1, Set.of(), List.of(), false),
                fragment("owner", "selected", 0, Set.of(), List.of(), true)));
        assertThat(catalog.categories()).extracting(ProviderCategoryCatalog.Category::key)
                .containsExactly("selected", "status");
        assertThat(catalog.categoryKeys()).containsExactly("selected");
        assertThat(catalog.category("status")).isEmpty();
        assertThatThrownBy(() -> new ProviderCapabilityContracts(catalog).contract("absent", Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not installed");
    }

    private static ProviderCategoryFragment fragment(String owner, String key, int order,
            Set<ProviderModule> modules, List<String> adapters, boolean selectable) {
        return new ProviderCategoryFragment(owner, order, key, key, null, selectable, "Member impact",
                ProviderRealityLevel.CONTRACT_ONLY, modules,
                new ProviderCategoryContract(List.of(), adapters, List.of(), List.of(),
                        "Declared source", List.of(), "Declared export boundary", "Declared replacement boundary"));
    }
}
