package com.massimotter.weave.mcp;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Creates one short-lived RFC 7523 private_key_jwt assertion from a mounted private JWK. */
final class PrivateKeyJwtClientAssertion {
    static final String ASSERTION_TYPE =
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final Set<String> ALLOWED_JWK_FIELDS = Set.of(
            "kty", "kid", "alg", "use", "key_ops", "n", "e", "d", "p", "q", "dp", "dq", "qi");

    private PrivateKeyJwtClientAssertion() {}

    static String create(
            McpWorkloadProperties properties,
            JsonMapper mapper,
            byte[] privateJwk,
            Instant issuedAt) {
        byte[] header = null;
        byte[] claims = null;
        byte[] signingInput = null;
        byte[] signature = null;
        try {
            PrivateKey privateKey = signingKey(properties, mapper, privateJwk);

            Map<String, Object> protectedHeader = new LinkedHashMap<>();
            protectedHeader.put("alg", "PS256");
            protectedHeader.put("kid", properties.exchangeClientKeyId());
            protectedHeader.put("typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", properties.exchangeClientId());
            payload.put("sub", properties.exchangeClientId());
            payload.put("aud", properties.tokenUri().toString());
            payload.put("jti", UUID.randomUUID().toString());
            payload.put("iat", issuedAt.getEpochSecond());
            payload.put("exp", issuedAt.plus(properties.exchangeClientAssertionTtl()).getEpochSecond());

            header = mapper.writeValueAsBytes(protectedHeader);
            claims = mapper.writeValueAsBytes(payload);
            String encodedHeader = base64Url(header);
            String encodedClaims = base64Url(claims);
            signingInput = (encodedHeader + "." + encodedClaims).getBytes(StandardCharsets.US_ASCII);
            Signature signer = Signature.getInstance("RSASSA-PSS");
            signer.setParameter(ps256Parameters());
            signer.initSign(privateKey);
            signer.update(signingInput);
            signature = signer.sign();
            return encodedHeader + "." + encodedClaims + "." + base64Url(signature);
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw new McpAdmissionException(McpAdmissionException.Kind.UNAVAILABLE);
        } finally {
            clear(header);
            clear(claims);
            clear(signingInput);
            clear(signature);
        }
    }

    static void validate(
            McpWorkloadProperties properties,
            JsonMapper mapper,
            byte[] privateJwk) {
        try {
            signingKey(properties, mapper, privateJwk);
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw new McpAdmissionException(McpAdmissionException.Kind.UNAVAILABLE);
        }
    }

    private static PrivateKey signingKey(
            McpWorkloadProperties properties,
            JsonMapper mapper,
            byte[] privateJwk) throws GeneralSecurityException {
        JsonNode jwk = mapper.readTree(privateJwk);
        requirePrivateSigningJwk(jwk, properties.exchangeClientKeyId());
        return privateKey(jwk);
    }

    private static void requirePrivateSigningJwk(JsonNode jwk, String expectedKeyId) {
        if (!jwk.isObject()
                || !"RSA".equals(text(jwk, "kty"))
                || !expectedKeyId.equals(text(jwk, "kid"))
                || !"PS256".equals(text(jwk, "alg"))
                || !"sig".equals(text(jwk, "use"))
                || !jwk.path("key_ops").isArray()
                || jwk.path("key_ops").size() != 1
                || !"sign".equals(jwk.path("key_ops").get(0).stringValue())) {
            throw new IllegalArgumentException("invalid private signing JWK");
        }
        jwk.propertyNames().forEach(field -> {
            if (!ALLOWED_JWK_FIELDS.contains(field)) {
                throw new IllegalArgumentException("unexpected private JWK field");
            }
        });
    }

    private static PrivateKey privateKey(JsonNode jwk) throws GeneralSecurityException {
        BigInteger modulus = integer(jwk, "n");
        BigInteger publicExponent = integer(jwk, "e");
        if (modulus.bitLength() < 2048 || !BigInteger.valueOf(65537).equals(publicExponent)) {
            throw new IllegalArgumentException("unsafe RSA key parameters");
        }
        RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(
                modulus,
                publicExponent,
                integer(jwk, "d"),
                integer(jwk, "p"),
                integer(jwk, "q"),
                integer(jwk, "dp"),
                integer(jwk, "dq"),
                integer(jwk, "qi"));
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static PSSParameterSpec ps256Parameters() {
        return new PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                PSSParameterSpec.TRAILER_FIELD_BC);
    }

    private static BigInteger integer(JsonNode jwk, String field) {
        byte[] decoded = null;
        try {
            decoded = Base64.getUrlDecoder().decode(text(jwk, field));
            if (decoded.length == 0) {
                throw new IllegalArgumentException("empty JWK integer");
            }
            return new BigInteger(1, decoded);
        } finally {
            clear(decoded);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException("missing JWK field");
        }
        return value.stringValue();
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }
}
