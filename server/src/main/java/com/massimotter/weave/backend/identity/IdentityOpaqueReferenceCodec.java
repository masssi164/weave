package com.massimotter.weave.backend.identity;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Produces deterministic, organization-bound and non-reversible administration references.
 *
 * <p>The HMAC key is loaded from a mounted SecretRef. Raw Keycloak identifiers never cross the
 * public control-plane boundary and are resolved by comparing a bounded live Keycloak projection.
 */
@Component
public final class IdentityOpaqueReferenceCodec {
  private static final String ALGORITHM = "HmacSHA256";

  private final IdentityInvitationProperties properties;

  public IdentityOpaqueReferenceCodec(IdentityInvitationProperties properties) {
    this.properties = properties;
  }

  public String member(String organizationId, String keycloakSubject) {
    return reference("mem", organizationId, keycloakSubject);
  }

  public String invitation(String organizationId, String keycloakInvitationId) {
    return reference("inv", organizationId, keycloakInvitationId);
  }

  public String cursor(String organizationId, String keycloakSubject) {
    return reference("cur", organizationId, keycloakSubject);
  }

  private String reference(String kind, String organizationId, String rawIdentifier) {
    if (organizationId == null
        || organizationId.isBlank()
        || rawIdentifier == null
        || rawIdentifier.isBlank()) {
      throw new IllegalArgumentException("Identity reference inputs must not be blank");
    }
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret(), ALGORITHM));
      byte[] digest =
          mac.doFinal(
              (kind + '\u001f' + organizationId + '\u001f' + rawIdentifier)
                  .getBytes(StandardCharsets.UTF_8));
      return kind
          + "_"
          + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 32);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Identity reference HMAC is unavailable", exception);
    }
  }

  private byte[] secret() {
    String secretFile = properties.keycloak().referenceHmacSecretFile();
    if (secretFile.isBlank()) {
      throw new IllegalStateException(
          "Keycloak administration reference SecretRef is not configured");
    }
    try {
      String value = Files.readString(Path.of(secretFile), StandardCharsets.UTF_8).trim();
      if (value.length() < 32) {
        throw new IllegalStateException(
            "Keycloak administration reference SecretRef must contain at least 32 characters");
      }
      return value.getBytes(StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Keycloak administration reference SecretRef is unreadable", exception);
    }
  }
}
