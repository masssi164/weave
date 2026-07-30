package com.massimotter.weave.backend.identity;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
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
  private static final int MINIMUM_KEY_BYTES = 32;
  private static final int MAXIMUM_KEY_FILE_BYTES = 4_096;

  private final byte[] secret;

  public IdentityOpaqueReferenceCodec(IdentityInvitationProperties properties) {
    this.secret = loadSecret(properties);
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

  public void requireReady() {
    // Construction validates and loads the exact SecretRef before the server can become ready.
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
      mac.init(new SecretKeySpec(secret.clone(), ALGORITHM));
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

  private static byte[] loadSecret(IdentityInvitationProperties properties) {
    String secretFile = properties.keycloak().referenceHmacSecretFile();
    if (secretFile.isBlank()) {
      throw new IllegalStateException(
          "Keycloak administration reference SecretRef is not configured");
    }
    Path path = Path.of(secretFile);
    try {
      if (Files.isSymbolicLink(path)
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
          || !Files.isReadable(path)) {
        throw new IllegalStateException(
            "Keycloak administration reference SecretRef is unreadable");
      }
      byte[] value = Files.readAllBytes(path);
      if (value.length > MAXIMUM_KEY_FILE_BYTES) {
        throw new IllegalStateException(
            "Keycloak administration reference SecretRef exceeds the safe bound");
      }
      int start = 0;
      int end = value.length;
      while (start < end && asciiWhitespace(value[start])) {
        start++;
      }
      while (end > start && asciiWhitespace(value[end - 1])) {
        end--;
      }
      byte[] trimmed = Arrays.copyOfRange(value, start, end);
      Arrays.fill(value, (byte) 0);
      if (trimmed.length < MINIMUM_KEY_BYTES) {
        Arrays.fill(trimmed, (byte) 0);
        throw new IllegalStateException(
            "Keycloak administration reference SecretRef must contain at least 32 bytes");
      }
      return trimmed;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Keycloak administration reference SecretRef is unreadable", exception);
    }
  }

  private static boolean asciiWhitespace(byte value) {
    return value == ' ' || value == '\t' || value == '\r' || value == '\n';
  }
}
