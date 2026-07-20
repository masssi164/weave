package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeProfileException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.Test;

class Ed25519JcsRuntimeProfileSignerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void emitsCanonicalFlattenedEd25519JwsAndPayloadHash() throws Exception {
        KeyPair key = keyPair();
        SignedRuntimeProfile signed = signer("runtime-profile-key-1", key).sign(profile());

        byte[] headerBytes = decode(signed.protectedHeader());
        byte[] payloadBytes = decode(signed.payload());
        JsonNode header = JSON.readTree(headerBytes);
        JsonNode payload = JSON.readTree(payloadBytes);

        assertThat(header.path("alg").asText()).isEqualTo("EdDSA");
        assertThat(header.path("typ").asText()).isEqualTo("weave.runtime-profile+jws");
        assertThat(header.path("kid").asText()).isEqualTo("runtime-profile-key-1");
        assertThat(header.path("contractVersion").asText()).isEqualTo(RuntimeProfile.VERSION);
        assertThat(payload.path("profileVersion").asText()).isEqualTo(RuntimeProfile.VERSION);
        assertThat(payload.path("zeroDurableCellBytes").asBoolean()).isTrue();
        assertThat(payload.path("workloadIdentity").path("clientId").asText())
                .isEqualTo("weaver-cell-example");
        assertThat(payload.path("mcp").path("servers").get(0).path("grantType").asText())
                .isEqualTo("client_credentials");
        assertThat(payloadBytes).isEqualTo(new JsonCanonicalizer(
                new String(payloadBytes, StandardCharsets.UTF_8)).getEncodedUTF8());
        assertThat(signed.profileHash()).isEqualTo("sha256:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payloadBytes)));

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key.getPublic());
        verifier.update((signed.protectedHeader() + "." + signed.payload()).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(decode(signed.signature()))).isTrue();
    }

    @Test
    void overlapKeyResigningPreservesSemanticHashButChangesTheEnvelope() throws Exception {
        SignedRuntimeProfile first = signer("runtime-profile-key-1", keyPair()).sign(profile());
        SignedRuntimeProfile second = signer("runtime-profile-key-2", keyPair()).sign(profile());

        assertThat(second.profileHash()).isEqualTo(first.profileHash());
        assertThat(second.protectedHeader()).isNotEqualTo(first.protectedHeader());
        assertThat(second.signature()).isNotEqualTo(first.signature());
    }

    @Test
    void mismatchedConfiguredKeyPairFailsClosed() throws Exception {
        KeyPair privatePair = keyPair();
        KeyPair publicPair = keyPair();
        Ed25519JcsRuntimeProfileSigner signer = new Ed25519JcsRuntimeProfileSigner(
                JSON,
                () -> new RuntimeProfileSigningKeyProvider.SigningKey(
                        "runtime-profile-key-1", privatePair.getPrivate(), publicPair.getPublic()));

        assertThatThrownBy(() -> signer.sign(profile()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signing failed");
    }

    @Test
    void v1AndNonDisposableProfilesAreRejectedBeforeSigning() {
        assertThatThrownBy(() -> profile("weave.runtime-profile/v1", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v2");
        assertThatThrownBy(() -> profile(RuntimeProfile.VERSION, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero durable");
    }

    @Test
    void verifierReturnsTheTypedProfileOnlyForCurrentTrustedCanonicalEvidence() throws Exception {
        KeyPair key = keyPair();
        SignedRuntimeProfile signed = signer("runtime-profile-key-1", key).sign(profile());
        Ed25519JcsRuntimeProfileVerifier verifier = verifier("runtime-profile-key-1", key);

        RuntimeProfile verified = verifier.verify(signed, Instant.parse("2026-07-20T09:05:00Z"));

        assertThat(verified).isEqualTo(profile());
        assertThatThrownBy(() -> verifier.verify(signed, signed.expiresAt()))
                .isInstanceOf(InvalidRuntimeProfileException.class)
                .extracting("code").isEqualTo("profile-expired-or-not-yet-valid");
    }

    @Test
    void verifierRejectsUnknownKeysTamperingAndMetadataSubstitution() throws Exception {
        KeyPair key = keyPair();
        SignedRuntimeProfile signed = signer("runtime-profile-key-1", key).sign(profile());
        Ed25519JcsRuntimeProfileVerifier verifier = verifier("another-key", key);

        assertThatThrownBy(() -> verifier.verify(signed, Instant.parse("2026-07-20T09:05:00Z")))
                .isInstanceOf(InvalidRuntimeProfileException.class)
                .extracting("code").isEqualTo("untrusted-key");

        Ed25519JcsRuntimeProfileVerifier trusted = verifier("runtime-profile-key-1", key);
        SignedRuntimeProfile tampered = new SignedRuntimeProfile(
                signed.protectedHeader(), replaceLast(signed.payload()), signed.signature(), signed.profileHash(),
                signed.profileId(), signed.cellRef(), signed.keyId(), signed.issuedAt(), signed.expiresAt());
        assertThatThrownBy(() -> trusted.verify(tampered, Instant.parse("2026-07-20T09:05:00Z")))
                .isInstanceOf(InvalidRuntimeProfileException.class);

        SignedRuntimeProfile substituted = new SignedRuntimeProfile(
                signed.protectedHeader(), signed.payload(), signed.signature(), signed.profileHash(),
                signed.profileId(), "cell:another", signed.keyId(), signed.issuedAt(), signed.expiresAt());
        assertThatThrownBy(() -> trusted.verify(substituted, Instant.parse("2026-07-20T09:05:00Z")))
                .isInstanceOf(InvalidRuntimeProfileException.class)
                .extracting("code").isEqualTo("metadata-mismatch");
    }

    @Test
    void verifierRejectsAValidSignatureOverNoncanonicalJson() throws Exception {
        KeyPair key = keyPair();
        SignedRuntimeProfile canonical = signer("runtime-profile-key-1", key).sign(profile());
        byte[] canonicalPayload = decode(canonical.payload());
        byte[] noncanonicalPayload = ("{\n "
                + new String(canonicalPayload, StandardCharsets.UTF_8).substring(1))
                .getBytes(StandardCharsets.UTF_8);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(noncanonicalPayload);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key.getPrivate());
        signer.update((canonical.protectedHeader() + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII));
        SignedRuntimeProfile noncanonical = new SignedRuntimeProfile(
                canonical.protectedHeader(), encodedPayload,
                Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()),
                "sha256:" + HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(noncanonicalPayload)),
                canonical.profileId(), canonical.cellRef(), canonical.keyId(),
                canonical.issuedAt(), canonical.expiresAt());

        assertThatThrownBy(() -> verifier("runtime-profile-key-1", key).verify(
                noncanonical, Instant.parse("2026-07-20T09:05:00Z")))
                .isInstanceOf(InvalidRuntimeProfileException.class)
                .extracting("code").isEqualTo("noncanonical-payload");
    }

    private static Ed25519JcsRuntimeProfileSigner signer(String keyId, KeyPair keyPair) {
        return new Ed25519JcsRuntimeProfileSigner(
                JSON,
                () -> new RuntimeProfileSigningKeyProvider.SigningKey(
                        keyId, keyPair.getPrivate(), keyPair.getPublic()));
    }

    private static Ed25519JcsRuntimeProfileVerifier verifier(String keyId, KeyPair keyPair) {
        RuntimeProfileTrustKeyProvider trust = new RuntimeProfileTrustKeyProvider() {
            @Override
            public Optional<TrustKey> resolve(String requestedKeyId, Instant now) {
                return keyId.equals(requestedKeyId)
                        ? Optional.of(new TrustKey(
                                keyId, keyPair.getPublic(), Instant.parse("2026-07-20T08:00:00Z"),
                                Instant.parse("2026-07-20T10:00:00Z")))
                        : Optional.empty();
            }

            @Override
            public List<TrustKey> publishedKeys(Instant now) {
                return resolve(keyId, now).stream().toList();
            }
        };
        return new Ed25519JcsRuntimeProfileVerifier(JSON, trust);
    }

    private static String replaceLast(String value) {
        char replacement = value.charAt(value.length() - 1) == 'A' ? 'B' : 'A';
        return value.substring(0, value.length() - 1) + replacement;
    }

    private static KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static RuntimeProfile profile() {
        return profile(RuntimeProfile.VERSION, true);
    }

    private static RuntimeProfile profile(String version, boolean zeroDurableCellBytes) {
        return new RuntimeProfile(
                version,
                "rp_example_001",
                "org:example",
                "person:example",
                new RuntimeMemberBinding("https://auth.weave.test/realms/weave", "member-example"),
                "cell:example",
                new RuntimeProfile.WorkloadIdentity(
                        "https://auth.weave.test/realms/weave",
                        "service-account-weaver-cell-example",
                        "weaver-cell-example",
                        "weaver-runtime",
                        RuntimeProfile.AuthenticationMethod.PRIVATE_KEY_JWT),
                Instant.parse("2026-07-20T09:00:00Z"),
                Instant.parse("2026-07-20T09:15:00Z"),
                "entitlement:42",
                "workspace:17",
                "webdav-manifest:workspace:17",
                "runtime-state://org/example/person/example/state/9",
                zeroDurableCellBytes,
                new RuntimeProfile.ModelPolicy(
                        List.of("organization-managed"),
                        List.of("assistant-default"),
                        List.of("assistant-safe-fallback"),
                        32768,
                        "eu"),
                new RuntimeProfile.MatrixPolicy(
                        "matrix-account:example",
                        "matrix-facade:org:example",
                        "credentialref://weave/matrix/example",
                        List.of("room:example"),
                        RuntimeProfile.AutoJoin.ALLOWLIST,
                        true),
                new RuntimeProfile.McpPolicy(
                        List.of(new RuntimeProfile.McpServer(
                                "weave-domain-tools",
                                "https://api.weave.test/mcp",
                                "https://api.weave.test/mcp",
                                "io.modelcontextprotocol/oauth-client-credentials",
                                "client_credentials",
                                List.of("files.read", "calendar.search_events"),
                                "credentialref://weave/mcp/example",
                                List.of("files.read", "calendar.search_events"))),
                        List.of("files.read", "calendar.search_events")),
                new RuntimeProfile.ApprovalPolicy(
                        "openclaw",
                        new RuntimeProfile.PluginRouting(
                                true, RuntimeProfile.PluginRoutingMode.SAME_CHAT, List.of("room:example")),
                        RuntimeProfile.ExecMode.ASK,
                        RuntimeProfile.PersistentTrustPolicy.BOUNDED),
                new RuntimeProfile.SandboxPolicy(
                        RuntimeProfile.SandboxMode.REQUIRED,
                        RuntimeProfile.NetworkPolicy.ALLOWLIST,
                        List.of("weave-matrix", "weave-mcp"),
                        RuntimeProfile.FilesystemPolicy.WORKSPACE_ONLY,
                        List.of()),
                new RuntimeProfile.AutomationPolicy(false, RuntimeProfile.SchedulePolicy.DISABLED));
    }
}
