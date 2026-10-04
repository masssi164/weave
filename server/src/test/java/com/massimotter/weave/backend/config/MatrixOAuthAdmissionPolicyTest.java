package com.massimotter.weave.backend.config;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatrixOAuthAdmissionPolicyTest {

    private static final String MATRIX_AUDIENCE = "https://api.weave.test/_matrix/client";
    private static final String API_AUDIENCE = "https://api.weave.test/api";
    private static final String SCOPES =
            "weave:workspace urn:matrix:client:api:* urn:matrix:client:device:DEVICE123456";

    @Test
    void acceptsOnlyTheConfiguredMatrixResourceClientAndDeviceScope() {
        MatrixOAuthAdmissionPolicy policy = policy(MATRIX_AUDIENCE, "weave-matrix-client");

        assertThat(policy.validate(token(MATRIX_AUDIENCE, "weave-matrix-client", SCOPES)).hasErrors())
                .isFalse();
        assertThat(MatrixOAuthAdmissionPolicy.requiredDeviceId(
                token(MATRIX_AUDIENCE, "weave-matrix-client", SCOPES)))
                .isEqualTo("DEVICE123456");
        assertThat(policy.validate(token(API_AUDIENCE, "weave-matrix-client", SCOPES)).hasErrors())
                .isTrue();
        assertThat(policy.validate(token(MATRIX_AUDIENCE, "weave-app", SCOPES)).hasErrors())
                .isTrue();
        assertThat(policy.validate(token(MATRIX_AUDIENCE, "weave-admin-console", SCOPES)).hasErrors())
                .isTrue();
        assertThat(policy.validate(token(MATRIX_AUDIENCE, " weave-matrix-client ", SCOPES)).hasErrors())
                .isTrue();
        assertThat(policy.validate(token(MATRIX_AUDIENCE, "weave-matrix-client", "weave:workspace")).hasErrors())
                .isTrue();
        assertThat(policy.validate(token(MATRIX_AUDIENCE, "weave-matrix-client",
                SCOPES + " urn:matrix:client:device:OTHERDEVICE12")).hasErrors()).isTrue();
        assertThatThrownBy(() -> MatrixOAuthAdmissionPolicy.requiredDeviceId(token(
                MATRIX_AUDIENCE, "weave-matrix-client", SCOPES + " urn:matrix:client:device:OTHERDEVICE12")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingOrCrossResourceConfigurationCannotAdmitAnyBearer() {
        Jwt token = token(MATRIX_AUDIENCE, "weave-matrix-client", SCOPES);
        assertThat(policy("", "weave-matrix-client").validate(token).hasErrors()).isTrue();
        assertThat(policy(MATRIX_AUDIENCE, "").validate(token).hasErrors()).isTrue();
        assertThat(policy(MATRIX_AUDIENCE, "weave-app").validate(token).hasErrors()).isTrue();
        assertThat(policy(MATRIX_AUDIENCE, "matrix-mas")
                .validate(token(MATRIX_AUDIENCE, "matrix-mas", SCOPES)).hasErrors()).isTrue();
        assertThat(policy(API_AUDIENCE, "weave-matrix-client").validate(token).hasErrors()).isTrue();
        assertThat(new MatrixOAuthAdmissionPolicy(MATRIX_AUDIENCE, "weave-matrix-client",
                "https://provider.example", API_AUDIENCE, "weave-app")
                .validate(token).hasErrors()).isTrue();
    }

    private MatrixOAuthAdmissionPolicy policy(String audience, String clients) {
        return new MatrixOAuthAdmissionPolicy(
                audience, clients, "https://api.weave.test", API_AUDIENCE, "weave-app");
    }

    private Jwt token(String audience, String clientId, String scope) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("matrix-token")
                .header("alg", "RS256")
                .subject("member-1")
                .audience(List.of(audience))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .claim("azp", clientId)
                .claim("scope", scope)
                .build();
    }
}
