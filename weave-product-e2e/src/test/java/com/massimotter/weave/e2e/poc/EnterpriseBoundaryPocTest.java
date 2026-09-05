package com.massimotter.weave.e2e.poc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.AdmissionRejected;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.BindingUnavailable;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.CanonicalResourceRef;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.CredentialOwner;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.ExternalOidcTrust;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.HomeCoreProviderControl;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.InteractionSurfaces;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.MatrixCredentialBoundary;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.OidcAssertion;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.StaleBinding;
import com.massimotter.weave.e2e.poc.EnterpriseBoundaryPoc.WeaveProviderResolver;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnterpriseBoundaryPocTest {
  private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
  private static final String ISSUER = "https://identity.example.test/realms/acme";
  private static final String AUDIENCE = "https://weave.example.test";

  @Test
  void externalOidcKeepsIdentityStableAndSupportsControlledSigningKeyRotation() throws Exception {
    KeyPair oldKey = keyPair();
    KeyPair newKey = keyPair();
    var overlapTrust =
        new ExternalOidcTrust(
            ISSUER,
            AUDIENCE,
            Map.of("old", oldKey.getPublic(), "new", newKey.getPublic()));

    var beforeRotation =
        overlapTrust.verify(token("old", oldKey, "subject-42", "org:acme", AUDIENCE), NOW);
    var afterRotation =
        overlapTrust.verify(token("new", newKey, "subject-42", "org:acme", AUDIENCE), NOW);

    assertThat(afterRotation.accountRef()).isEqualTo(beforeRotation.accountRef());
    assertThat(afterRotation.organizationRef()).isEqualTo("org:acme");

    var completedRotation =
        new ExternalOidcTrust(ISSUER, AUDIENCE, Map.of("new", newKey.getPublic()));
    assertThatThrownBy(
            () ->
                completedRotation.verify(
                    token("old", oldKey, "subject-42", "org:acme", AUDIENCE), NOW))
        .isInstanceOf(AdmissionRejected.class)
        .hasMessageContaining("signature");
  }

  @Test
  void externalOidcRejectsWrongAudienceIssuerAndMissingOrganization() throws Exception {
    KeyPair key = keyPair();
    var trust = new ExternalOidcTrust(ISSUER, AUDIENCE, Map.of("active", key.getPublic()));

    assertThatThrownBy(
            () ->
                trust.verify(
                    token("active", key, "subject-42", "org:acme", "https://other.test"),
                    NOW))
        .isInstanceOf(AdmissionRejected.class)
        .hasMessageContaining("audience");
    assertThatThrownBy(
            () ->
                trust.verify(
                    token(
                        "active",
                        key,
                        "subject-42",
                        "org:acme",
                        AUDIENCE,
                        "https://issuer.test"),
                    NOW))
        .isInstanceOf(AdmissionRejected.class)
        .hasMessageContaining("issuer");
    assertThatThrownBy(
            () ->
                trust.verify(
                    token("active", key, "subject-42", null, AUDIENCE), NOW))
        .isInstanceOf(AdmissionRejected.class)
        .hasMessageContaining("organization_ref");
  }

  @Test
  void externalOidcRejectsExpiredFutureAndTamperedAssertions() throws Exception {
    KeyPair key = keyPair();
    var trust = new ExternalOidcTrust(ISSUER, AUDIENCE, Map.of("active", key.getPublic()));
    OidcAssertion valid = token("active", key, "subject-42", "org:acme", AUDIENCE);
    OidcAssertion tampered =
        new OidcAssertion(
            valid.keyId(),
            valid.algorithm(),
            valid.issuer(),
            "other-subject",
            valid.organizationRef(),
            valid.audiences(),
            valid.notBefore(),
            valid.expiresAt(),
            valid.signature());

    assertThatThrownBy(() -> trust.verify(tampered, NOW))
        .isInstanceOf(AdmissionRejected.class)
        .hasMessageContaining("signature");
    assertThatThrownBy(() -> trust.verify(valid, NOW.plusSeconds(61)))
        .isInstanceOf(AdmissionRejected.class)
        .hasMessageContaining("expiry");
    assertThatThrownBy(() -> trust.verify(valid, NOW.minusSeconds(2)))
        .isInstanceOf(AdmissionRejected.class)
        .hasMessageContaining("not-before");
  }

  @Test
  void providerSwitchKeepsCanonicalReferenceAndPinsInFlightWorkToItsRevision() {
    var homeCore = new HomeCoreProviderControl();
    var resolver = new WeaveProviderResolver(homeCore);
    var principal = principal("org:acme");
    var resource = new CanonicalResourceRef("org:acme", "files", "weave://files/report-2026");

    homeCore.activate(
        "org:acme", "files", 0, "nextcloud-webdav", "homecore://binding/acme/files/1");
    var inFlight = resolver.begin(principal, resource);
    homeCore.activate(
        "org:acme", "files", 1, "sharepoint-graph", "homecore://binding/acme/files/2");
    var nextOperation = resolver.begin(principal, resource);

    assertThat(resolver.resume(principal, inFlight)).isEqualTo(inFlight);
    assertThat(inFlight.bindingRevision()).isEqualTo(1);
    assertThat(nextOperation.bindingRevision()).isEqualTo(2);
    assertThat(nextOperation.resource()).isEqualTo(inFlight.resource());
    assertThat(nextOperation.resource().value()).doesNotContain("nextcloud", "sharepoint");
  }

  @Test
  void providerResolutionFailsClosedAcrossTenantsStaleChangesAndRevocation() {
    var homeCore = new HomeCoreProviderControl();
    var resolver = new WeaveProviderResolver(homeCore);
    var resource =
        new CanonicalResourceRef("org:acme", "files", "weave://files/confidential");
    homeCore.activate(
        "org:acme", "files", 0, "nextcloud-webdav", "homecore://binding/acme/files/1");
    var lease = resolver.begin(principal("org:acme"), resource);

    assertThatThrownBy(() -> resolver.begin(principal("org:other"), resource))
        .isInstanceOf(AdmissionRejected.class)
        .hasMessageContaining("organization");
    assertThatThrownBy(
            () ->
                homeCore.activate(
                    "org:acme",
                    "files",
                    0,
                    "sharepoint-graph",
                    "homecore://binding/acme/files/2"))
        .isInstanceOf(StaleBinding.class);

    homeCore.revoke("org:acme", "files", 1);
    assertThatThrownBy(() -> resolver.resume(principal("org:acme"), lease))
        .isInstanceOf(BindingUnavailable.class);
  }

  @Test
  void mcpProjectsWorkToolsWhileMatrixRemainsTheWeaverChatChannel() {
    var surfaces =
        new InteractionSurfaces(
            Set.of(
                "files.search",
                "files.read",
                "calendar.search_events",
                "calendar.create_event"),
            "matrix",
            "/.well-known/matrix/client");

    assertThat(surfaces.mcpTools()).noneMatch(tool -> tool.startsWith("chat."));
    assertThat(surfaces.weaverChatChannel()).isEqualTo("matrix");
  }

  @Test
  void chatCannotAccidentallyEnterTheMcpCatalog() {
    assertThatThrownBy(
            () ->
                new InteractionSurfaces(
                    Set.of("files.search", "chat.send_message"),
                    "matrix",
                    "/.well-known/matrix/client"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outside MCP");
  }

  @Test
  void matrixMemberAndWeaverCredentialsRemainSeparatedFromWeaveServer() {
    var boundary =
        new MatrixCredentialBoundary(
            CredentialOwner.HOME_CORE_IAM,
            CredentialOwner.CLIENT_MATRIX_SDK,
            CredentialOwner.CLIENT_MATRIX_SDK,
            CredentialOwner.MATRIX_HOMESERVER_ENCRYPTED_BACKUP,
            CredentialOwner.WEAVER_CELL_SECRET_STORE,
            false);

    assertThat(boundary.memberMatrixTokenHolder()).isEqualTo(CredentialOwner.CLIENT_MATRIX_SDK);
    assertThat(boundary.weaverMatrixTokenHolder())
        .isEqualTo(CredentialOwner.WEAVER_CELL_SECRET_STORE);
    assertThat(boundary.memberAndWeaverShareCredential()).isFalse();

    assertThatThrownBy(
            () ->
                new MatrixCredentialBoundary(
                    CredentialOwner.HOME_CORE_IAM,
                    CredentialOwner.WEAVE_SERVER,
                    CredentialOwner.WEAVE_SERVER,
                    CredentialOwner.MATRIX_HOMESERVER_ENCRYPTED_BACKUP,
                    CredentialOwner.WEAVER_CELL_SECRET_STORE,
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("direct-channel boundary");
  }

  private EnterpriseBoundaryPoc.Principal principal(String organizationRef) {
    return new EnterpriseBoundaryPoc.Principal(
        ISSUER, "subject-42", organizationRef, "acct_stable-test-reference");
  }

  private OidcAssertion token(
      String keyId,
      KeyPair key,
      String subject,
      String organizationRef,
      String audience) {
    return token(keyId, key, subject, organizationRef, audience, ISSUER);
  }

  private OidcAssertion token(
      String keyId,
      KeyPair key,
      String subject,
      String organizationRef,
      String audience,
      String issuer) {
    return OidcAssertion.sign(
        keyId,
        key.getPrivate(),
        issuer,
        subject,
        organizationRef,
        List.of(audience),
        NOW.minusSeconds(1),
        NOW.plusSeconds(60));
  }

  private KeyPair keyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
