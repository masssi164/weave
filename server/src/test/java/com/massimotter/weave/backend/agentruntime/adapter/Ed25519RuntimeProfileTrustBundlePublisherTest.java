package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfileJwkSet;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Ed25519RuntimeProfileTrustBundlePublisherTest {
    private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");

    @Test
    void publishesOnlyCurrentOverlapKeysAsDeterministicRfc8037Jwks() throws Exception {
        KeyPair previous = keyPair();
        KeyPair current = keyPair();
        RuntimeProfileTrustKeyProvider provider = provider(List.of(
                key("key-b", current, NOW.minusSeconds(60), NOW.plusSeconds(600)),
                key("key-a", previous, NOW.minusSeconds(600), NOW.plusSeconds(60)),
                key("expired", keyPair(), NOW.minusSeconds(600), NOW)));

        RuntimeProfileJwkSet jwks = new Ed25519RuntimeProfileTrustBundlePublisher(provider)
                .publish(NOW).orElseThrow();

        assertThat(jwks.keys()).extracting(RuntimeProfileJwkSet.Jwk::kid)
                .containsExactly("key-a", "key-b");
        assertThat(jwks.keys()).allSatisfy(key -> {
            assertThat(key.kty()).isEqualTo("OKP");
            assertThat(key.crv()).isEqualTo("Ed25519");
            assertThat(key.alg()).isEqualTo("EdDSA");
            assertThat(Base64.getUrlDecoder().decode(key.x())).hasSize(32);
        });
        assertEquivalentPublicKey(previous, jwks.keys().getFirst());
        assertEquivalentPublicKey(current, jwks.keys().get(1));
    }

    @Test
    void noCurrentKeysPublishesNoTrustBundleAndDuplicatesFailClosed() throws Exception {
        RuntimeProfileTrustKeyProvider empty = provider(List.of(
                key("expired", keyPair(), NOW.minusSeconds(60), NOW)));
        assertThat(new Ed25519RuntimeProfileTrustBundlePublisher(empty).publish(NOW)).isEmpty();

        KeyPair pair = keyPair();
        RuntimeProfileTrustKeyProvider ambiguous = provider(List.of(
                key("duplicate", pair, NOW.minusSeconds(1), NOW.plusSeconds(60)),
                key("duplicate", pair, NOW.minusSeconds(1), NOW.plusSeconds(120))));
        assertThatThrownBy(() -> new Ed25519RuntimeProfileTrustBundlePublisher(ambiguous).publish(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ambiguous");
    }

    private static RuntimeProfileTrustKeyProvider provider(
            List<RuntimeProfileTrustKeyProvider.TrustKey> keys) {
        return new RuntimeProfileTrustKeyProvider() {
            @Override
            public Optional<TrustKey> resolve(String keyId, Instant now) {
                return keys.stream().filter(key -> key.keyId().equals(keyId) && key.validAt(now)).findFirst();
            }

            @Override
            public List<TrustKey> publishedKeys(Instant now) {
                return new ArrayList<>(keys);
            }
        };
    }

    private static RuntimeProfileTrustKeyProvider.TrustKey key(
            String id, KeyPair pair, Instant from, Instant until) {
        return new RuntimeProfileTrustKeyProvider.TrustKey(id, pair.getPublic(), from, until);
    }

    private static KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static void assertEquivalentPublicKey(KeyPair original, RuntimeProfileJwkSet.Jwk jwk)
            throws Exception {
        byte[] encoded = Base64.getUrlDecoder().decode(jwk.x());
        boolean xOdd = (encoded[31] & 0x80) != 0;
        encoded[31] &= 0x7f;
        byte[] bigEndian = new byte[encoded.length];
        for (int index = 0; index < encoded.length; index++) {
            bigEndian[index] = encoded[encoded.length - 1 - index];
        }
        PublicKey reconstructed = KeyFactory.getInstance("Ed25519").generatePublic(
                new EdECPublicKeySpec(NamedParameterSpec.ED25519,
                        new EdECPoint(xOdd, new BigInteger(1, bigEndian))));
        byte[] message = "trust-bundle-proof".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(original.getPrivate());
        signer.update(message);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(reconstructed);
        verifier.update(message);
        assertThat(verifier.verify(signer.sign())).isTrue();
    }
}
