package com.massimotter.weave.backend.providerbinding.application;

import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.discovery.provider.ProviderState;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderSelectionStatusTest {
    @Test
    void installedDefaultsCannotBecomeActiveWithoutAdministrationSelection() {
        var result = ProviderSelectionStatus.project(status(true), "records", Optional.empty());
        assertThat(result.enabled()).isFalse();
        assertThat(result.state()).isEqualTo(ProviderState.DISABLED);
        assertThat(result.readiness()).isEqualTo("awaiting_admin_selection");
    }

    @Test
    void aDeclaredAliasUsesTheSameStatusPolicyWithoutExposingSecretReferences() {
        var result = ProviderSelectionStatus.project(status(true), "records", Optional.of(selection("records", "ALIAS")));
        assertThat(result.enabled()).isTrue();
        assertThat(result.state()).isEqualTo(ProviderState.READY);
        assertThat(result.diagnostics()).containsEntry("selectedByAdmin", true).containsEntry("secretRefConfigured", true);
        assertThat(result.diagnostics().values()).doesNotContain("secretref://weave/private/provider");
    }

    @Test
    void anotherAdapterSelectionDoesNotEnableTheInstalledAdapter() {
        var result = ProviderSelectionStatus.project(status(true), "records", Optional.of(selection("records", "other")));
        assertThat(result.enabled()).isFalse();
        assertThat(result.readiness()).isEqualTo("not_selected_by_admin");
    }

    @Test
    void administratorSelectionCannotClaimUnsafeDiagnosticsAreSupportSafe() {
        var result = ProviderSelectionStatus.project(status(false), "records", Optional.of(selection("records", "primary")));
        assertThat(result.supportSafe()).isFalse();
    }

    @Test
    void categoryOwnershipCannotBeCrossedByASelectionFromAnotherDomain() {
        assertThatThrownBy(() -> ProviderSelectionStatus.project(status(true), "records",
                Optional.of(selection("other-domain", "primary"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owner");
    }

    private static ProviderSelection selection(String category, String provider) {
        return new ProviderSelection(category, provider, null, "secretref://weave/private/provider",
                "actor:admin", Instant.EPOCH, true, true, false, List.of());
    }

    private static ProviderStatusResponse status(boolean supportSafe) {
        return new ProviderStatusResponse(ProviderModule.FILES, "primary", ProviderState.READY, "ready",
                true, true, false, true, supportSafe, false, "Installed adapter", Set.of(), Set.of(),
                List.of(), "redacted", List.of("alias"), ProviderRealityLevel.CONFIGURED, Map.of());
    }
}
