package com.massimotter.weave.backend.discovery.catalog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProviderCategorySelectionPolicyTest {
    @Test
    void missingCategoryPolicyFailsInsteadOfAllowingApply() {
        var existing = CoreDiscoveryMetadata.adminControlPlaneCategory();
        assertThatThrownBy(() -> new ProviderCategoryFragment(existing.owner(), existing.order(), existing.key(),
                existing.label(), existing.capabilityDomain(), existing.selectable(), existing.memberImpact(),
                existing.defaultReality(), existing.modules(), null, existing.contract()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("selectionPolicy");
    }

    @Test
    void deniedApplyRequiresExplicitSafeFailureDetails() {
        assertThatThrownBy(() -> new ProviderCategorySelectionPolicy(true, false, "blocked", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ProviderCategorySelectionPolicy.metadataOnly().directApplyAllowed()).isTrue();
        assertThat(ProviderCategorySelectionPolicy.boundMetadataOnly().bindingScoped()).isTrue();
    }
}
