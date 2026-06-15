package com.massimotter.weave.backend.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderChoiceModelTest {

    @Test
    void supportedChoiceModelsAreCentralizedAndIncludeHybridComposite() {
        assertThat(ProviderChoiceModel.supportedValues()).containsExactly(
                "recommended_self_hosted_default",
                "external_existing_provider",
                "managed_cloud_provider",
                "hybrid_composite");
    }

    @Test
    void normalizesBlankChoiceModelToRecommendedDefaultAndRejectsUnknownValues() {
        assertThat(ProviderChoiceModel.normalize("  ")).isEqualTo("recommended_self_hosted_default");
        assertThat(ProviderChoiceModel.normalize(" hybrid_composite ")).isEqualTo("hybrid_composite");

        assertThatThrownBy(() -> ProviderChoiceModel.normalize("hardcoded_default"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider choice contract value");
    }

    @Test
    void providerSelectionRoundTripsHybridComposite() {
        ProviderSelection selection = new ProviderSelection(
                " CHAT ",
                "slack",
                " hybrid_composite ",
                "secretref://weave/provider/slack",
                "actor:admin-123",
                null,
                true,
                false,
                true,
                null);

        assertThat(selection.category()).isEqualTo("chat");
        assertThat(selection.choiceModel()).isEqualTo("hybrid_composite");
        assertThat(selection.supportSafe()).isTrue();
        assertThat(selection.secretRef()).isEqualTo("secretref://weave/provider/slack");
    }
}
