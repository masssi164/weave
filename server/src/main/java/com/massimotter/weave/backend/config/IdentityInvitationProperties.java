package com.massimotter.weave.backend.config;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.identity.invitations")
public class IdentityInvitationProperties {
  private String storageMode = "memory";
  private Duration defaultLifetime = Duration.ofDays(7);
  private String eventsHmacSecret = "";
  private Duration eventFreshness = Duration.ofMinutes(5);
  private final Keycloak keycloak = new Keycloak();
  private final BootstrapOwner bootstrapOwner = new BootstrapOwner();

  public String storageMode() {
    return storageMode;
  }

  public void setStorageMode(String storageMode) {
    this.storageMode = storageMode;
  }

  public Duration defaultLifetime() {
    return defaultLifetime;
  }

  public void setDefaultLifetime(Duration defaultLifetime) {
    this.defaultLifetime = defaultLifetime;
  }

  public String eventsHmacSecret() {
    return eventsHmacSecret;
  }

  public void setEventsHmacSecret(String eventsHmacSecret) {
    this.eventsHmacSecret = eventsHmacSecret;
  }

  public Duration eventFreshness() {
    return eventFreshness;
  }

  public void setEventFreshness(Duration eventFreshness) {
    this.eventFreshness = eventFreshness;
  }

  public Keycloak keycloak() {
    return keycloak;
  }

  /** JavaBean accessor required by Spring Boot's nested configuration-property binder. */
  public Keycloak getKeycloak() {
    return keycloak;
  }

  public BootstrapOwner bootstrapOwner() {
    return bootstrapOwner;
  }

  /** JavaBean accessor required by Spring Boot's nested configuration-property binder. */
  public BootstrapOwner getBootstrapOwner() {
    return bootstrapOwner;
  }

  public static class BootstrapOwner {
    private boolean enabled;
    private String tokenFile = "";
    private String tenantId = "";

    public boolean enabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String tokenFile() {
      return tokenFile;
    }

    public void setTokenFile(String tokenFile) {
      this.tokenFile = tokenFile == null ? "" : tokenFile.trim();
    }

    public String tenantId() {
      return tenantId;
    }

    public void setTenantId(String tenantId) {
      this.tenantId = tenantId == null ? "" : tenantId.trim();
    }
  }

  public static class Keycloak {
    private URI baseUrl = URI.create("http://weave-keycloak:8080");
    private String realm = "weave";
    private String organizationId = "";
    private String organizationAlias = "weave";
    private String oauthRegistrationId = "weave-identity-admin";
    private Duration timeout = Duration.ofSeconds(10);
    private String referenceHmacSecretFile = "";
    private Map<String, String> capabilityGroupProjections =
        new LinkedHashMap<>(
            Map.of(
                "calendar.manage_events", "weave-calendar-editors",
                "boards.update_task", "weave-board-editors",
                "meetings.host", "weave-meeting-hosts",
                "documents.edit", "weave-document-editors",
                "decisions.record", "weave-decision-recorders",
                "agent-runtime.entitled", "weave-weaver-runtime"));

    public URI baseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String realm() {
      return realm;
    }

    public void setRealm(String realm) {
      this.realm = realm;
    }

    public String organizationId() {
      return organizationId;
    }

    public void setOrganizationId(String organizationId) {
      this.organizationId = organizationId;
    }

    public String organizationAlias() {
      return organizationAlias;
    }

    public void setOrganizationAlias(String organizationAlias) {
      this.organizationAlias = organizationAlias;
    }

    public String oauthRegistrationId() {
      return oauthRegistrationId;
    }

    public void setOauthRegistrationId(String oauthRegistrationId) {
      this.oauthRegistrationId = oauthRegistrationId;
    }

    public Duration timeout() {
      return timeout;
    }

    public String referenceHmacSecretFile() {
      return referenceHmacSecretFile;
    }

    public void setReferenceHmacSecretFile(String referenceHmacSecretFile) {
      this.referenceHmacSecretFile =
          referenceHmacSecretFile == null ? "" : referenceHmacSecretFile.trim();
    }

    public void setTimeout(Duration timeout) {
      this.timeout = timeout;
    }

    /**
     * Backend-only mapping from stable Weave capability identifiers to flat Keycloak group names.
     *
     * <p>The values never cross an API boundary.
     */
    public Map<String, String> capabilityGroupProjections() {
      return Map.copyOf(capabilityGroupProjections);
    }

    public void setCapabilityGroupProjections(Map<String, String> capabilityGroupProjections) {
      this.capabilityGroupProjections =
          capabilityGroupProjections == null
              ? new LinkedHashMap<>()
              : new LinkedHashMap<>(capabilityGroupProjections);
    }
  }
}
