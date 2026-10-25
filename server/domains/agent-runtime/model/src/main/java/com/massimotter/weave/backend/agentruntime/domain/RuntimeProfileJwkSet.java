package com.massimotter.weave.backend.agentruntime.domain;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record RuntimeProfileJwkSet(List<Jwk> keys) {
    public RuntimeProfileJwkSet {
        keys = keys == null ? List.of() : List.copyOf(keys);
        if (Set.copyOf(keys.stream().map(Jwk::kid).toList()).size() != keys.size()) {
            throw new IllegalArgumentException("RuntimeProfile JWKS key ids must be unique");
        }
    }

    public record Jwk(String kty, String crv, String x, String use, String alg, String kid) {
        private static final Pattern BASE64URL_32_BYTES = Pattern.compile("[A-Za-z0-9_-]{43}");

        public Jwk {
            if (!"OKP".equals(kty) || !"Ed25519".equals(crv) || !"sig".equals(use) || !"EdDSA".equals(alg)
                    || x == null || !BASE64URL_32_BYTES.matcher(x).matches()
                    || kid == null || kid.isBlank()) {
                throw new IllegalArgumentException("invalid RuntimeProfile Ed25519 JWK");
            }
        }
    }
}
