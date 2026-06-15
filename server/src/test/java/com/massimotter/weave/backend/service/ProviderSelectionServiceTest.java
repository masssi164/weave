package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.admin.ProviderSelectionRequest;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderSelectionServiceTest {

    private final InMemoryProviderSelectionRepository repository = new InMemoryProviderSelectionRepository();
    private final ProviderRegistry registry = mock(ProviderRegistry.class);
    private final ProviderSelectionService service = new ProviderSelectionService(
            registry,
            repository,
            Clock.fixed(Instant.parse("2026-05-27T01:03:39Z"), ZoneOffset.UTC));

    @Test
    void hybridCompositeSelectionRoundTripsThroughSupportSafeResponse() {
        when(registry.status()).thenReturn(emptyRegistry());
        ProviderSelectionRequest request = new ProviderSelectionRequest(
                "chat",
                "matrix-chat",
                " hybrid_composite ",
                "secretref://weave/provider/matrix-chat/admin-token",
                false,
                List.of("manual note with Bearer raw-token and https://provider.example.invalid/path"),
                "operator-selected hybrid chat");

        var selection = service.validate(request, "user:admin-123");
        var applied = service.save(selection);
        var response = service.toResponse(applied, false, "admin_selected_pending_readiness");

        assertThat(response.category()).isEqualTo("chat");
        assertThat(response.providerKey()).isEqualTo("matrix-chat");
        assertThat(response.choiceModel()).isEqualTo("hybrid_composite");
        assertThat(response.secretRef()).isEqualTo("secretref://weave/provider/matrix-chat/admin-token");
        assertThat(response.applied()).isTrue();
        assertThat(response.dryRun()).isFalse();
        assertThat(response.migrationDryRunRequired()).isTrue();
        assertThat(response.lossyMappingNotes()).containsExactly("manual note with Bearer [redacted] and url-[redacted]");
        assertThat(response.toString()).doesNotContain("raw-token", "provider.example.invalid/path");
    }

    @Test
    void invalidCategoryAndProviderAreRejectedWithSupportSafeDetails() {
        when(registry.status()).thenReturn(emptyRegistry());

        assertThatThrownBy(() -> service.validate(new ProviderSelectionRequest(
                "unknown-category",
                "matrix-chat",
                "recommended_self_hosted_default",
                null,
                true,
                List.of(),
                "bad category"), "user:admin-123"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("provider-category-unknown");
                    assertThat(exception.details()).containsEntry("category", "unknown-category");
                    assertThat(exception.toString()).doesNotContain("secret", "token");
                });

        assertThatThrownBy(() -> service.validate(new ProviderSelectionRequest(
                "chat",
                "unregistered-provider",
                "recommended_self_hosted_default",
                "not-a-secret-value",
                true,
                List.of("Bearer should-not-leak"),
                "bad provider"), "user:admin-123"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("provider-selection-category-mismatch");
                    assertThat(exception.details()).containsEntry("category", "chat");
                    assertThat(exception.details()).containsEntry("providerKey", "unregistered-provider");
                    assertThat(exception.toString()).doesNotContain("should-not-leak", "not-a-secret-value");
                });
    }

    @Test
    void contractOnlyProviderCandidateBehaviorRemainsAccepted() {
        when(registry.status()).thenReturn(emptyRegistry());

        var selection = service.validate(new ProviderSelectionRequest(
                "model",
                "lmstudio-openai-compatible",
                "external_existing_provider",
                null,
                true,
                List.of(),
                "contract-only model candidate"), "user:admin-123");

        assertThat(selection.providerKey()).isEqualTo("lmstudio-openai-compatible");
        assertThat(selection.migrationDryRunRequired()).isTrue();
        assertThat(service.toResponse(selection, true, "dry_run_valid").dryRun()).isTrue();
    }

    @Test
    void rawSecretValuesNeverPassSecretRefValidation() {
        when(registry.status()).thenReturn(emptyRegistry());

        assertThatThrownBy(() -> service.validate(new ProviderSelectionRequest(
                "chat",
                "matrix-chat",
                "recommended_self_hosted_default",
                "Bearer raw-secret-token",
                false,
                List.of(),
                "raw secret attempt"), "user:admin-123"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("provider-selection-secretref-invalid");
                    assertThat(exception.details()).containsEntry("secretRef", "invalid-secret-ref-redacted");
                    assertThat(exception.toString()).doesNotContain("raw-secret-token", "Bearer raw");
                });
    }

    private ProviderRegistryResponse emptyRegistry() {
        return new ProviderRegistryResponse(
                "dogfood-production",
                ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                true,
                true,
                true,
                false,
                true,
                Instant.parse("2026-05-27T01:03:39Z"),
                null,
                null,
                List.of(),
                List.of(),
                List.of());
    }
}
