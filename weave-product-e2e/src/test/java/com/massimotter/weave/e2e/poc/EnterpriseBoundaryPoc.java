package com.massimotter.weave.e2e.poc;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contract-only model for evaluating the proposed Home Core/Weave enterprise boundary.
 *
 * <p>This class is deliberately test-scoped. It must not become a second runtime configuration
 * or a substitute for an accepted specification-corpus change.
 */
final class EnterpriseBoundaryPoc {

  record Principal(String issuer, String subject, String organizationRef, String accountRef) {}

  record OidcAssertion(
      String keyId,
      String algorithm,
      String issuer,
      String subject,
      String organizationRef,
      List<String> audiences,
      Instant notBefore,
      Instant expiresAt,
      byte[] signature) {
    OidcAssertion {
      audiences = List.copyOf(audiences);
      signature = signature.clone();
    }

    @Override
    public byte[] signature() {
      return signature.clone();
    }

    byte[] signedPayload() {
      return String.join(
              "\u0000",
              keyId,
              algorithm,
              issuer,
              subject,
              organizationRef == null ? "" : organizationRef,
              String.join(" ", audiences),
              Long.toString(notBefore.getEpochSecond()),
              Long.toString(expiresAt.getEpochSecond()))
          .getBytes(StandardCharsets.UTF_8);
    }

    static OidcAssertion sign(
        String keyId,
        PrivateKey privateKey,
        String issuer,
        String subject,
        String organizationRef,
        List<String> audiences,
        Instant notBefore,
        Instant expiresAt) {
      OidcAssertion unsigned =
          new OidcAssertion(
              keyId,
              "RS256",
              issuer,
              subject,
              organizationRef,
              audiences,
              notBefore,
              expiresAt,
              new byte[0]);
      try {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(unsigned.signedPayload());
        return new OidcAssertion(
            keyId,
            "RS256",
            issuer,
            subject,
            organizationRef,
            audiences,
            notBefore,
            expiresAt,
            signer.sign());
      } catch (GeneralSecurityException failure) {
        throw new IllegalArgumentException("assertion could not be signed", failure);
      }
    }
  }

  static final class ExternalOidcTrust {
    private final String issuer;
    private final String audience;
    private final Map<String, PublicKey> verificationKeys;

    ExternalOidcTrust(String issuer, String audience, Map<String, PublicKey> verificationKeys) {
      this.issuer = required(issuer, "issuer");
      this.audience = required(audience, "audience");
      this.verificationKeys = Map.copyOf(verificationKeys);
    }

    Principal verify(OidcAssertion assertion, Instant now) {
      Objects.requireNonNull(assertion, "assertion must not be null");
      if (!"RS256".equals(assertion.algorithm())) {
        throw rejected("algorithm");
      }
      PublicKey verificationKey = verificationKeys.get(assertion.keyId());
      if (verificationKey == null || !validSignature(assertion, verificationKey)) {
        throw rejected("signature");
      }
      if (!issuer.equals(assertion.issuer())) {
        throw rejected("issuer");
      }
      if (assertion.audiences().size() != 1
          || !audience.equals(assertion.audiences().get(0))) {
        throw rejected("audience");
      }
      if (!assertion.expiresAt().isAfter(now)) {
        throw rejected("expiry");
      }
      if (assertion.notBefore().isAfter(now)) {
        throw rejected("not-before");
      }
      String subject = admitted(assertion.subject(), "subject");
      String organizationRef = admitted(assertion.organizationRef(), "organization_ref");
      return new Principal(issuer, subject, organizationRef, accountRef(issuer, subject));
    }

    private boolean validSignature(OidcAssertion assertion, PublicKey verificationKey) {
      try {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(verificationKey);
        verifier.update(assertion.signedPayload());
        return verifier.verify(assertion.signature());
      } catch (GeneralSecurityException failure) {
        throw new AdmissionRejected("token-validation", failure);
      }
    }
  }

  enum BindingState {
    ACTIVE,
    RETIRED,
    REVOKED
  }

  record ProviderBinding(
      String organizationRef,
      String domain,
      long revision,
      String adapterKey,
      String configurationHandle,
      BindingState state) {
    ProviderBinding {
      organizationRef = required(organizationRef, "organizationRef");
      domain = required(domain, "domain");
      adapterKey = required(adapterKey, "adapterKey");
      configurationHandle = required(configurationHandle, "configurationHandle");
      state = Objects.requireNonNull(state, "state must not be null");
      if (revision < 1) {
        throw new IllegalArgumentException("revision must be positive");
      }
      String normalizedHandle = configurationHandle.toLowerCase(Locale.ROOT);
      if (normalizedHandle.contains("secret") || normalizedHandle.contains("password")) {
        throw new IllegalArgumentException("configuration handle must be opaque and non-secret");
      }
    }

    ProviderBinding withState(BindingState nextState) {
      return new ProviderBinding(
          organizationRef, domain, revision, adapterKey, configurationHandle, nextState);
    }
  }

  static final class HomeCoreProviderControl {
    private final Map<BindingKey, List<ProviderBinding>> bindings = new LinkedHashMap<>();

    ProviderBinding activate(
        String organizationRef,
        String domain,
        long expectedRevision,
        String adapterKey,
        String configurationHandle) {
      BindingKey key = new BindingKey(organizationRef, domain);
      List<ProviderBinding> history =
          bindings.computeIfAbsent(key, ignored -> new ArrayList<>());
      ProviderBinding current = history.isEmpty() ? null : history.get(history.size() - 1);
      long actualRevision = current == null ? 0 : current.revision();
      if (actualRevision != expectedRevision
          || (current != null && current.state() != BindingState.ACTIVE)) {
        throw new StaleBinding(expectedRevision, actualRevision);
      }
      if (current != null) {
        history.set(history.size() - 1, current.withState(BindingState.RETIRED));
      }
      ProviderBinding next =
          new ProviderBinding(
              organizationRef,
              domain,
              actualRevision + 1,
              adapterKey,
              configurationHandle,
              BindingState.ACTIVE);
      history.add(next);
      return next;
    }

    ProviderBinding current(String organizationRef, String domain) {
      List<ProviderBinding> history = bindings.get(new BindingKey(organizationRef, domain));
      if (history == null
          || history.isEmpty()
          || history.get(history.size() - 1).state() != BindingState.ACTIVE) {
        throw new BindingUnavailable();
      }
      return history.get(history.size() - 1);
    }

    ProviderBinding revision(String organizationRef, String domain, long revision) {
      return bindings.getOrDefault(new BindingKey(organizationRef, domain), List.of()).stream()
          .filter(binding -> binding.revision() == revision)
          .findFirst()
          .orElseThrow(BindingUnavailable::new);
    }

    void revoke(String organizationRef, String domain, long revision) {
      BindingKey key = new BindingKey(organizationRef, domain);
      List<ProviderBinding> history = bindings.getOrDefault(key, List.of());
      for (int index = 0; index < history.size(); index++) {
        ProviderBinding candidate = history.get(index);
        if (candidate.revision() == revision) {
          history.set(index, candidate.withState(BindingState.REVOKED));
          return;
        }
      }
      throw new BindingUnavailable();
    }
  }

  record CanonicalResourceRef(String organizationRef, String domain, String value) {
    CanonicalResourceRef {
      organizationRef = required(organizationRef, "organizationRef");
      domain = required(domain, "domain");
      value = required(value, "value");
      if (!value.startsWith("weave://" + domain + "/")) {
        throw new IllegalArgumentException("resource reference is not canonical");
      }
    }
  }

  record RouteLease(CanonicalResourceRef resource, long bindingRevision, String adapterKey) {}

  static final class WeaveProviderResolver {
    private final HomeCoreProviderControl homeCore;

    WeaveProviderResolver(HomeCoreProviderControl homeCore) {
      this.homeCore = Objects.requireNonNull(homeCore, "homeCore must not be null");
    }

    RouteLease begin(Principal principal, CanonicalResourceRef resource) {
      requireSameOrganization(principal, resource);
      ProviderBinding current = homeCore.current(resource.organizationRef(), resource.domain());
      return new RouteLease(resource, current.revision(), current.adapterKey());
    }

    RouteLease resume(Principal principal, RouteLease lease) {
      requireSameOrganization(principal, lease.resource());
      ProviderBinding binding =
          homeCore.revision(
              lease.resource().organizationRef(),
              lease.resource().domain(),
              lease.bindingRevision());
      if (binding.state() == BindingState.REVOKED
          || !binding.adapterKey().equals(lease.adapterKey())) {
        throw new BindingUnavailable();
      }
      return lease;
    }

    private static void requireSameOrganization(
        Principal principal, CanonicalResourceRef resource) {
      if (!principal.organizationRef().equals(resource.organizationRef())) {
        throw new AdmissionRejected("organization");
      }
    }
  }

  record InteractionSurfaces(
      Set<String> mcpTools, String weaverChatChannel, String matrixDiscovery) {
    InteractionSurfaces {
      mcpTools = Set.copyOf(mcpTools);
      weaverChatChannel = required(weaverChatChannel, "weaverChatChannel");
      matrixDiscovery = required(matrixDiscovery, "matrixDiscovery");
      if (mcpTools.stream().anyMatch(tool -> tool.startsWith("chat."))) {
        throw new IllegalArgumentException("Chat must stay on the Matrix channel, outside MCP");
      }
      if (!"matrix".equals(weaverChatChannel)) {
        throw new IllegalArgumentException("Weaver chat channel must be Matrix");
      }
      if (!"/.well-known/matrix/client".equals(matrixDiscovery)) {
        throw new IllegalArgumentException("Matrix discovery must use the standard client route");
      }
    }
  }

  enum CredentialOwner {
    HOME_CORE_IAM,
    CLIENT_MATRIX_SDK,
    MATRIX_HOMESERVER_ENCRYPTED_BACKUP,
    WEAVER_CELL_SECRET_STORE,
    WEAVE_SERVER
  }

  record MatrixCredentialBoundary(
      CredentialOwner humanOidcAuthority,
      CredentialOwner memberMatrixTokenHolder,
      CredentialOwner memberE2eePrivateKeyHolder,
      CredentialOwner encryptedKeyBackupHolder,
      CredentialOwner weaverMatrixTokenHolder,
      boolean memberAndWeaverShareCredential) {
    MatrixCredentialBoundary {
      if (humanOidcAuthority != CredentialOwner.HOME_CORE_IAM
          || memberMatrixTokenHolder != CredentialOwner.CLIENT_MATRIX_SDK
          || memberE2eePrivateKeyHolder != CredentialOwner.CLIENT_MATRIX_SDK
          || encryptedKeyBackupHolder != CredentialOwner.MATRIX_HOMESERVER_ENCRYPTED_BACKUP
          || weaverMatrixTokenHolder != CredentialOwner.WEAVER_CELL_SECRET_STORE
          || memberAndWeaverShareCredential) {
        throw new IllegalArgumentException("Matrix credentials violate the direct-channel boundary");
      }
      if (List.of(
              memberMatrixTokenHolder,
              memberE2eePrivateKeyHolder,
              encryptedKeyBackupHolder,
              weaverMatrixTokenHolder)
          .contains(CredentialOwner.WEAVE_SERVER)) {
        throw new IllegalArgumentException("Weave Server must not terminate Matrix credentials");
      }
    }
  }

  public static void main(String[] arguments) throws GeneralSecurityException {
    Instant now = Instant.parse("2026-09-05T12:00:00Z");
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair oldKey = generator.generateKeyPair();
    KeyPair newKey = generator.generateKeyPair();
    String issuer = "https://identity.example.test/realms/acme";
    String audience = "https://weave.example.test";
    OidcAssertion oldAssertion =
        OidcAssertion.sign(
            "old",
            oldKey.getPrivate(),
            issuer,
            "subject-42",
            "org:acme",
            List.of(audience),
            now.minusSeconds(1),
            now.plusSeconds(60));
    OidcAssertion newAssertion =
        OidcAssertion.sign(
            "new",
            newKey.getPrivate(),
            issuer,
            "subject-42",
            "org:acme",
            List.of(audience),
            now.minusSeconds(1),
            now.plusSeconds(60));
    ExternalOidcTrust overlapTrust =
        new ExternalOidcTrust(
            issuer,
            audience,
            Map.of("old", oldKey.getPublic(), "new", newKey.getPublic()));
    Principal principal = overlapTrust.verify(oldAssertion, now);
    Principal rotatedPrincipal = overlapTrust.verify(newAssertion, now);
    if (!principal.accountRef().equals(rotatedPrincipal.accountRef())) {
      throw new AssertionError("OIDC key rotation changed the stable account reference");
    }
    ExternalOidcTrust completedRotation =
        new ExternalOidcTrust(issuer, audience, Map.of("new", newKey.getPublic()));
    expect(AdmissionRejected.class, () -> completedRotation.verify(oldAssertion, now));

    HomeCoreProviderControl homeCore = new HomeCoreProviderControl();
    homeCore.activate(
        "org:acme", "files", 0, "nextcloud-webdav", "homecore://binding/acme/files/1");
    WeaveProviderResolver resolver = new WeaveProviderResolver(homeCore);
    CanonicalResourceRef resource =
        new CanonicalResourceRef("org:acme", "files", "weave://files/report-2026");
    RouteLease first = resolver.begin(principal, resource);
    homeCore.activate(
        "org:acme", "files", 1, "sharepoint-graph", "homecore://binding/acme/files/2");
    RouteLease second = resolver.begin(principal, resource);
    resolver.resume(principal, first);
    if (first.bindingRevision() != 1
        || second.bindingRevision() != 2
        || !first.resource().equals(second.resource())) {
      throw new AssertionError("provider switch did not preserve the canonical reference");
    }
    expect(
        AdmissionRejected.class,
        () ->
            resolver.begin(
                new Principal(issuer, "subject-7", "org:other", "acct_other"), resource));
    expect(
        StaleBinding.class,
        () ->
            homeCore.activate(
                "org:acme",
                "files",
                1,
                "s3-compatible",
                "homecore://binding/acme/files/3"));
    homeCore.revoke("org:acme", "files", first.bindingRevision());
    expect(BindingUnavailable.class, () -> resolver.resume(principal, first));

    new InteractionSurfaces(
        Set.of("files.search", "files.read", "calendar.search_events"),
        "matrix",
        "/.well-known/matrix/client");
    new MatrixCredentialBoundary(
        CredentialOwner.HOME_CORE_IAM,
        CredentialOwner.CLIENT_MATRIX_SDK,
        CredentialOwner.CLIENT_MATRIX_SDK,
        CredentialOwner.MATRIX_HOMESERVER_ENCRYPTED_BACKUP,
        CredentialOwner.WEAVER_CELL_SECRET_STORE,
        false);
    expect(
        IllegalArgumentException.class,
        () ->
            new InteractionSurfaces(
                Set.of("files.search", "chat.send_message"),
                "matrix",
                "/.well-known/matrix/client"));
    expect(
        IllegalArgumentException.class,
        () ->
            new MatrixCredentialBoundary(
                CredentialOwner.HOME_CORE_IAM,
                CredentialOwner.WEAVE_SERVER,
                CredentialOwner.WEAVE_SERVER,
                CredentialOwner.MATRIX_HOMESERVER_ENCRYPTED_BACKUP,
                CredentialOwner.WEAVER_CELL_SECRET_STORE,
                true));
    System.out.println("enterprise-boundary-poc: PASS (contract_only)");
  }

  private static void expect(Class<? extends RuntimeException> type, Runnable operation) {
    try {
      operation.run();
    } catch (RuntimeException failure) {
      if (type.isInstance(failure)) {
        return;
      }
      throw failure;
    }
    throw new AssertionError("expected " + type.getSimpleName());
  }

  private record BindingKey(String organizationRef, String domain) {
    BindingKey {
      organizationRef = required(organizationRef, "organizationRef");
      domain = required(domain, "domain");
    }
  }

  @SuppressWarnings("serial")
  static final class AdmissionRejected extends RuntimeException {
    AdmissionRejected(String boundary) {
      super("admission rejected at " + boundary);
    }

    AdmissionRejected(String boundary, Throwable cause) {
      super("admission rejected at " + boundary, cause);
    }
  }

  @SuppressWarnings("serial")
  static final class StaleBinding extends RuntimeException {
    StaleBinding(long expected, long actual) {
      super("stale binding revision: expected " + expected + " but found " + actual);
    }
  }

  @SuppressWarnings("serial")
  static final class BindingUnavailable extends RuntimeException {}

  private static AdmissionRejected rejected(String boundary) {
    return new AdmissionRejected(boundary);
  }

  private static String accountRef(String issuer, String subject) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(
                  ("issuer+subject:" + issuer + "#" + subject)
                      .getBytes(StandardCharsets.UTF_8));
      return "acct_" + HexFormat.of().formatHex(digest, 0, 16);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required", impossible);
    }
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String admitted(String value, String field) {
    try {
      return required(value, field);
    } catch (IllegalArgumentException failure) {
      throw rejected(field);
    }
  }
}
