package com.massimotter.weave.backend.service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "weave_product_profile_overrides")
class ProductProfileOverrideJpaEntity {

    @Id
    @Column(name = "primary_identity_key", nullable = false, length = 528)
    private String primaryIdentityKey;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "avatar", length = 512)
    private String avatar;

    @Column(name = "locale", length = 40)
    private String locale;

    @Column(name = "timezone", length = 80)
    private String timezone;

    @Column(name = "accessibility_preferences_json", nullable = false)
    private String accessibilityPreferencesJson;

    @Column(name = "profile_visibility", length = 80)
    private String profileVisibility;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProductProfileOverrideJpaEntity() {
    }

    ProductProfileOverrideJpaEntity(String primaryIdentityKey) {
        this.primaryIdentityKey = primaryIdentityKey;
    }

    void replaceWith(
            String displayName,
            String avatar,
            String locale,
            String timezone,
            String accessibilityPreferencesJson,
            String profileVisibility) {
        this.displayName = displayName;
        this.avatar = avatar;
        this.locale = locale;
        this.timezone = timezone;
        this.accessibilityPreferencesJson = accessibilityPreferencesJson;
        this.profileVisibility = profileVisibility;
    }

    String displayName() {
        return displayName;
    }

    String avatar() {
        return avatar;
    }

    String locale() {
        return locale;
    }

    String timezone() {
        return timezone;
    }

    String accessibilityPreferencesJson() {
        return accessibilityPreferencesJson;
    }

    String profileVisibility() {
        return profileVisibility;
    }
}

interface ProductProfileOverrideJpaRepository
        extends JpaRepository<ProductProfileOverrideJpaEntity, String> {
}
