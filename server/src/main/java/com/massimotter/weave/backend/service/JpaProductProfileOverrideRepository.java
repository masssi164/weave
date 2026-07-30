package com.massimotter.weave.backend.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.persistence.jpa.profile.ProductProfileOverrideJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.profile.ProductProfileOverrideJpaRepository;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

public class JpaProductProfileOverrideRepository implements ProductProfileOverrideRepository {

    private static final TypeReference<Map<String, String>> ACCESSIBILITY_PREFERENCES = new TypeReference<>() {
    };

    private final ProductProfileOverrideJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaProductProfileOverrideRepository(
            ProductProfileOverrideJpaRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductProfileOverride findByPrimaryIdentityKey(String primaryIdentityKey) {
        if (primaryIdentityKey == null || primaryIdentityKey.isBlank()) {
            return null;
        }
        return repository.findById(primaryIdentityKey).map(this::toDomain).orElse(null);
    }

    @Override
    @Transactional
    public ProductProfileOverride saveForPrimaryIdentityKey(
            String primaryIdentityKey,
            ProductProfileOverride profile) {
        if (primaryIdentityKey == null || primaryIdentityKey.isBlank()) {
            throw new IllegalArgumentException("Product profile override key must be a non-blank primary identity key.");
        }
        if (profile == null) {
            throw new IllegalArgumentException("Product profile override must not be null.");
        }
        repository.saveAndFlush(new ProductProfileOverrideJpaEntity(
                primaryIdentityKey,
                profile.displayName(),
                profile.avatar(),
                profile.locale(),
                profile.timezone(),
                accessibilityPreferencesJson(profile.accessibilityPreferences()),
                profile.profileVisibility()));
        return profile;
    }

    public String persistencePosture() {
        return "portable-jpa-hibernate-validated";
    }

    private ProductProfileOverride toDomain(ProductProfileOverrideJpaEntity entity) {
        return new ProductProfileOverride(
                entity.displayName(),
                entity.avatar(),
                entity.locale(),
                entity.timezone(),
                accessibilityPreferences(entity.accessibilityPreferencesJson()),
                entity.profileVisibility());
    }

    private String accessibilityPreferencesJson(Map<String, String> preferences) {
        try {
            return objectMapper.writeValueAsString(preferences == null ? Map.of() : preferences);
        } catch (JacksonException exception) {
            throw new ProductProfileStoreException(
                    "Failed to serialize product profile accessibility preferences.", exception);
        }
    }

    private Map<String, String> accessibilityPreferences(String preferencesJson) {
        if (preferencesJson == null || preferencesJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(preferencesJson, ACCESSIBILITY_PREFERENCES);
        } catch (JacksonException exception) {
            throw new ProductProfileStoreException(
                    "Failed to load product profile accessibility preferences.", exception);
        }
    }
}
