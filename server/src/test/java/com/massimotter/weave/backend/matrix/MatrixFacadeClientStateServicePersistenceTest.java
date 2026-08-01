package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixIdentityProjectionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixRevokedSessionJpaRepository;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatrixFacadeClientStateServicePersistenceTest {

    @Test
    void keycloakDerivedIdentityProjectionSurvivesBackendRestart() {
        DriverManagerDataSource dataSource = dataSource();
        com.massimotter.weave.backend.testing.JpaTestDatabase.initializeSchema(dataSource);
        MatrixProtocolCoreService protocol = mock(MatrixProtocolCoreService.class);
        String matrixUserId = "@user_projection:api.weave.test";
        when(protocol.whoami("subject-projection", "WEAVEDEVICEPROJECTION"))
                .thenReturn(Map.of("user_id", matrixUserId));
        ContextAuthorizationProperties authorization = new ContextAuthorizationProperties(
                "weave_tenant_id",
                "tenant_id",
                "tenant-projection",
                "sub",
                "user:",
                List.of(),
                List.of(),
                List.of());
        MatrixFacadeClientStateStore stateStore = stateStore(dataSource);
        OrganizationIdentityContextResolver identityContextResolver =
                OrganizationIdentityContextResolver.configured(authorization);
        MatrixFacadeClientStateService first =
                new MatrixFacadeClientStateService(
                        protocol, stateStore, authorization, identityContextResolver);

        Jwt session = jwt();
        MatrixFacadeClientStateService.MatrixIdentity registered =
                first.register(session, "WEAVEDEVICEPROJECTION");
        MatrixFacadeClientStateService.MatrixIdentity registeredAgain =
                first.register(session, "WEAVEDEVICEPROJECTION");
        first.revoke(session);
        MatrixFacadeClientStateService restarted =
                new MatrixFacadeClientStateService(
                        protocol, stateStore(dataSource), authorization, identityContextResolver);

        assertThat(registered.userId()).isEqualTo(matrixUserId);
        assertThat(registeredAgain).isEqualTo(registered);
        assertThat(restarted.identityForMatrixUserId(
                matrixUserId,
                "tenant-projection",
                "https://auth.example/realms/weave"))
                .hasValueSatisfying(identity -> {
                    assertThat(identity.actorRef().value()).isEqualTo("user:subject-projection");
                    assertThat(identity.authorizationPrincipalRef()).isEqualTo("user:subject-projection");
                });
        assertThat(restarted.revoked(session)).isTrue();
    }

    private Jwt jwt() {
        Instant issuedAt = Instant.now().minusSeconds(60);
        return Jwt.withTokenValue("opaque-token")
                .header("alg", "none")
                .subject("subject-projection")
                .issuer("https://auth.example/realms/weave")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("sid", "session-projection")
                .build();
    }

    private DriverManagerDataSource dataSource() {
        return com.massimotter.weave.backend.testing.JpaTestDatabase
                .dataSource("matrix-facade-state");
    }

    private MatrixFacadeClientStateStore stateStore(
            DriverManagerDataSource dataSource) {
        MatrixIdentityProjectionJpaRepository identities =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource,
                        MatrixIdentityProjectionJpaRepository.class);
        MatrixRevokedSessionJpaRepository revocations =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource,
                        MatrixRevokedSessionJpaRepository.class);
        return new JpaMatrixFacadeClientStateStore(identities, revocations);
    }

}
