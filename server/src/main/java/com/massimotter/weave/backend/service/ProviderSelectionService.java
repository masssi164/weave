package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.admin.ProviderSelectionRequest;
import com.massimotter.weave.backend.model.admin.ProviderSelectionResponse;
import com.massimotter.weave.backend.provider.ProviderCapabilityContracts;
import com.massimotter.weave.backend.provider.ProviderCategoryCatalog;
import com.massimotter.weave.backend.provider.ProviderChoiceModel;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProviderSelectionService {

    private final ProviderRegistry providerRegistry;
    private final ProviderSelectionRepository providerSelectionRepository;
    private final Clock clock;

    @Autowired
    public ProviderSelectionService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository) {
        this(providerRegistry, providerSelectionRepository, Clock.systemUTC());
    }

    ProviderSelectionService(
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            Clock clock) {
        this.providerRegistry = providerRegistry;
        this.providerSelectionRepository = providerSelectionRepository;
        this.clock = clock;
    }

    public ProviderSelection validate(ProviderSelectionRequest request, String actorRef) {
        if (request == null || request.category() == null || request.category().isBlank()
                || request.providerKey() == null || request.providerKey().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-selection-invalid",
                    "Provider selection requires category and provider key.",
                    Map.of("reason", "category and providerKey are required"));
        }
        String category = request.category().trim();
        String providerKey = request.providerKey().trim();
        if (ProviderCategoryCatalog.category(category).isEmpty()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-category-unknown",
                    "Provider category is not part of the Weave canonical control-plane contract.",
                    Map.of("category", category));
        }
        if (!providerMatchesCategory(providerKey, category)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-selection-category-mismatch",
                    "Provider key is not registered as a support-safe candidate for the selected category.",
                    Map.of("category", category, "providerKey", providerKey));
        }
        return new ProviderSelection(
                category,
                providerKey,
                selectionChoiceModel(request.choiceModel()),
                selectionSecretRef(request.secretRef()),
                actorRef,
                Instant.now(clock),
                !request.dryRun(),
                true,
                requiresMigrationDryRun(request),
                safeLossyMappingNotes(request.lossyMappingNotes()));
    }

    public ProviderSelection save(ProviderSelection selection) {
        return providerSelectionRepository.save(selection);
    }

    public ProviderSelectionResponse toResponse(ProviderSelection selection, boolean dryRun, String readiness) {
        return new ProviderSelectionResponse(
                selection.category(),
                selection.providerKey(),
                selection.choiceModel(),
                selection.secretRef(),
                selection.selectedBy(),
                selection.selectedAt(),
                selection.applied() && !dryRun,
                dryRun,
                true,
                !selection.applied() || dryRun,
                selection.migrationDryRunRequired(),
                selection.lossyMappingNotes(),
                readiness,
                providerSelectionRepository.persistencePosture(),
                selection.selectedAt());
    }

    public boolean providerMatchesCategory(String providerKey, String category) {
        boolean registeredCandidate = providerRegistry.status().providers().stream()
                .filter(provider -> ProviderCategoryCatalog.providerMatchesCategory(provider, category))
                .anyMatch(provider -> providerKeyMatches(provider, providerKey));
        if (registeredCandidate) {
            return true;
        }
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return ProviderCapabilityContracts.providerCandidates(category).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    public String readinessFor(String category, ProviderRegistryResponse registry) {
        return registry.categories().stream()
                .filter(value -> value.category().equals(category))
                .map(value -> value.readiness().value())
                .findFirst()
                .orElse("unknown");
    }

    private boolean providerKeyMatches(ProviderStatusResponse provider, String providerKey) {
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return provider.providerKey().equals(providerKey)
                || provider.candidates().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
    }

    private String selectionChoiceModel(String value) {
        try {
            return ProviderChoiceModel.normalize(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-selection-choice-model-invalid",
                    "Provider selection choice model is not part of the Weave provider choice contract.",
                    Map.of("choiceModel", "invalid-choice-model-redacted"));
        }
    }

    private String selectionSecretRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("secretref://")) {
            return trimmed;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "provider-selection-secretref-invalid",
                "Provider selections may reference credentials only through SecretRef URIs.",
                Map.of("secretRef", "invalid-secret-ref-redacted"));
    }

    private boolean requiresMigrationDryRun(ProviderSelectionRequest request) {
        return request.lossyMappingNotes() != null && !request.lossyMappingNotes().isEmpty()
                || ProviderChoiceModel.requiresMigrationDryRun(request.choiceModel());
    }

    private List<String> safeLossyMappingNotes(List<String> notes) {
        if (notes == null) {
            return List.of();
        }
        return notes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::safeText)
                .distinct()
                .limit(10)
                .toList();
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        return value.trim()
                .replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer [redacted]")
                .replaceAll("(?i)xox[baprs]-[A-Za-z0-9-]+", "chat-token-[redacted]")
                .replaceAll("(?i)https?://[^\\s]+", "url-[redacted]")
                .replaceAll("(?i)secret(ref)?://[^\\s]+", "secret-ref-[redacted]");
    }
}
