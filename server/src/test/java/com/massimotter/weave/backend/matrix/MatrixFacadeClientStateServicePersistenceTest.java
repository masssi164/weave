package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatrixFacadeClientStateServicePersistenceTest {

    @Test
    void keycloakDerivedIdentityProjectionSurvivesBackendRestart() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        MatrixProtocolCoreService protocol = mock(MatrixProtocolCoreService.class);
        String matrixUserId = "@user_projection:api.weave.test";
        when(protocol.whoami("subject-projection", "WEAVEDEVICEPROJECTION"))
                .thenReturn(Map.of("user_id", matrixUserId));
        ContextAuthorizationProperties authorization = new ContextAuthorizationProperties(
                "weave_tenant_id",
                "tenant_id",
                "tenant-default",
                "sub",
                "user:",
                List.of(),
                List.of(),
                List.of());
        MatrixFacadeClientStateStore stateStore = stateStore(dataSource);
        MatrixFacadeClientStateService first =
                new MatrixFacadeClientStateService(protocol, stateStore, authorization);

        MatrixFacadeClientStateService.MatrixIdentity registered =
                first.register(jwt(), "WEAVEDEVICEPROJECTION");
        MatrixFacadeClientStateService restarted =
                new MatrixFacadeClientStateService(protocol, stateStore(dataSource), authorization);

        assertThat(registered.userId()).isEqualTo(matrixUserId);
        assertThat(restarted.identityForMatrixUserId(
                matrixUserId,
                "tenant-projection",
                "https://auth.example/realms/weave"))
                .hasValueSatisfying(identity -> {
                    assertThat(identity.actorRef().value()).isEqualTo("user:subject-projection");
                    assertThat(identity.authorizationPrincipalRef()).isEqualTo("user:subject-projection");
                });
    }

    private Jwt jwt() {
        Instant issuedAt = Instant.parse("2026-07-15T10:00:00Z");
        return Jwt.withTokenValue("opaque-token")
                .header("alg", "none")
                .subject("subject-projection")
                .issuer("https://auth.example/realms/weave")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("weave_tenant_id", "tenant-projection")
                .claim("sid", "session-projection")
                .build();
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
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
