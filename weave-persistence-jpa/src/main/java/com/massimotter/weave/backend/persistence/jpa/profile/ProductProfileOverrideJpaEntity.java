package com.massimotter.weave.backend.persistence.jpa.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "weave_product_profile_overrides")
public class ProductProfileOverrideJpaEntity {

  @Id
  @Column(name = "primary_identity_key", length = 528, nullable = false)
  private String primaryIdentityKey;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Column(name = "avatar", length = 512)
  private String avatar;

  @Column(name = "locale", length = 40)
  private String locale;

  @Column(name = "timezone", length = 80)
  private String timezone;

  @Column(name = "accessibility_preferences_json", nullable = false, length = Integer.MAX_VALUE)
  private String accessibilityPreferencesJson;

  @Column(name = "profile_visibility", length = 80)
  private String profileVisibility;

  protected ProductProfileOverrideJpaEntity() {}

  public ProductProfileOverrideJpaEntity(
      String primaryIdentityKey,
      String displayName,
      String avatar,
      String locale,
      String timezone,
      String accessibilityPreferencesJson,
      String profileVisibility) {
    this.primaryIdentityKey = primaryIdentityKey;
    this.displayName = displayName;
    this.avatar = avatar;
    this.locale = locale;
    this.timezone = timezone;
    this.accessibilityPreferencesJson = accessibilityPreferencesJson;
    this.profileVisibility = profileVisibility;
  }

  public String primaryIdentityKey() {
    return primaryIdentityKey;
  }

  public String displayName() {
    return displayName;
  }

  public String avatar() {
    return avatar;
  }

  public String locale() {
    return locale;
  }

  public String timezone() {
    return timezone;
  }

  public String accessibilityPreferencesJson() {
    return accessibilityPreferencesJson;
  }

  public String profileVisibility() {
    return profileVisibility;
  }

  public void replaceOverride(
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
}
