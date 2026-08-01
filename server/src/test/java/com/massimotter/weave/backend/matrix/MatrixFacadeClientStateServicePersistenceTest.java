package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixIdentityProjectionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixRevokedSessionJpaRepository;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
        when(protocol.whoami("another-subject", "WEAVEDEVICEPROJECTION"))
                .thenReturn(Map.of("user_id", matrixUserId));
        assertThatThrownBy(() -> first.register(
                        jwt("another-subject", "session-conflict"),
                        "WEAVEDEVICEPROJECTION"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Matrix user is already bound to another canonical actor.");
        assertThat(first.identityForMatrixUserId(
                        matrixUserId,
                        "tenant-projection",
                        "https://auth.example/realms/weave"))
                .hasValueSatisfying(identity ->
                        assertThat(identity.actorRef().value()).isEqualTo("user:subject-projection"));
        MatrixFacadeClientStateStore.IdentityProjection stored = stateStore.identityProjection(
                        "tenant-projection",
                        "https://auth.example/realms/weave",
                        matrixUserId)
                .orElseThrow();
        stateStore.saveIdentityProjection(new MatrixFacadeClientStateStore.IdentityProjection(
                stored.tenantId(),
                stored.identityIssuer(),
                stored.matrixUserId(),
                stored.actorRef(),
                "policy:subject-projection",
                stored.updatedAt().plusSeconds(1)));
        assertThatThrownBy(() -> stateStore.saveIdentityProjection(
                        new MatrixFacadeClientStateStore.IdentityProjection(
                                stored.tenantId(),
                                stored.identityIssuer(),
                                stored.matrixUserId(),
                                "user:another-subject",
                                "policy:another-subject",
                                stored.updatedAt().plusSeconds(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Matrix user is already bound to another canonical actor.");
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
                    assertThat(identity.authorizationPrincipalRef()).isEqualTo("policy:subject-projection");
                });
        assertThat(restarted.revoked(session)).isTrue();
    }

    @Test
    void concurrentInitialRegistrationsConvergeOnOneIdentityProjection() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        com.massimotter.weave.backend.testing.JpaTestDatabase.initializeSchema(dataSource);
        MatrixIdentityProjectionJpaRepository identities =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, MatrixIdentityProjectionJpaRepository.class);
        CyclicBarrier absentReadBarrier = new CyclicBarrier(2);
        CyclicBarrier absentRevocationBarrier = new CyclicBarrier(2);
        AtomicInteger emptyReads = new AtomicInteger();
        AtomicInteger emptyRevocationReads = new AtomicInteger();
        MatrixIdentityProjectionJpaRepository racingIdentities =
                mock(MatrixIdentityProjectionJpaRepository.class, delegatesTo(identities));
        doAnswer(invocation -> {
            Optional<?> result = identities.findById(invocation.getArgument(0));
            if (result.isEmpty() && emptyReads.incrementAndGet() <= 2) {
                absentReadBarrier.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(racingIdentities).findById(any());

        MatrixRevokedSessionJpaRepository revocations =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, MatrixRevokedSessionJpaRepository.class);
        MatrixRevokedSessionJpaRepository racingRevocations =
                mock(MatrixRevokedSessionJpaRepository.class, delegatesTo(revocations));
        doAnswer(invocation -> {
            Optional<?> result = revocations.findById(invocation.getArgument(0));
            if (result.isEmpty() && emptyRevocationReads.incrementAndGet() <= 2) {
                absentRevocationBarrier.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(racingRevocations).findById(any());
        MatrixFacadeClientStateStore stateStore = new JpaMatrixFacadeClientStateStore(
                racingIdentities,
                racingRevocations,
                com.massimotter.weave.backend.testing.JpaTestDatabase.transactionManager(dataSource));
        MatrixProtocolCoreService protocol = mock(MatrixProtocolCoreService.class);
        String matrixUserId = "@user_projection:api.weave.test";
        when(protocol.whoami("subject-projection", "WEAVEDEVICEPROJECTION"))
                .thenReturn(Map.of("user_id", matrixUserId));
        ContextAuthorizationProperties authorization = authorization();
        MatrixFacadeClientStateService service = new MatrixFacadeClientStateService(
                protocol,
                stateStore,
                authorization,
                OrganizationIdentityContextResolver.configured(authorization));
        OuterTransactionBoundary outerTransaction =
                com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                        dataSource, new OuterTransactionBoundary());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<MatrixFacadeClientStateService.MatrixIdentity>> registrations = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                registrations.add(executor.submit(
                        () -> outerTransaction.register(
                                service, jwt(), "WEAVEDEVICEPROJECTION")));
            }

            MatrixFacadeClientStateService.MatrixIdentity first =
                    registrations.get(0).get(20, TimeUnit.SECONDS);
            MatrixFacadeClientStateService.MatrixIdentity second =
                    registrations.get(1).get(20, TimeUnit.SECONDS);
            assertThat(second).isEqualTo(first);
            assertThat(identities.count()).isEqualTo(1);

            List<Future<?>> revocationsInFlight = new ArrayList<>();
            Jwt revocationSession = jwt();
            for (int index = 0; index < 2; index++) {
                revocationsInFlight.add(executor.submit(() -> {
                    outerTransaction.revoke(service, revocationSession);
                    return null;
                }));
            }
            for (Future<?> revocation : revocationsInFlight) {
                revocation.get(20, TimeUnit.SECONDS);
            }
            service.revoke(revocationSession);
            assertThat(revocations.count()).isEqualTo(1);
            assertThat(service.revoked(revocationSession)).isTrue();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Jwt jwt() {
        return jwt("subject-projection", "session-projection");
    }

    private Jwt jwt(String subject, String sessionId) {
        Instant issuedAt = Instant.now().minusSeconds(60);
        return Jwt.withTokenValue("opaque-token-" + subject)
                .header("alg", "none")
                .subject(subject)
                .issuer("https://auth.example/realms/weave")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("sid", sessionId)
                .build();
    }

    private ContextAuthorizationProperties authorization() {
        return new ContextAuthorizationProperties(
                "weave_tenant_id",
                "tenant_id",
                "tenant-projection",
                "sub",
                "user:",
                List.of(),
                List.of(),
                List.of());
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
        return new JpaMatrixFacadeClientStateStore(
                identities,
                revocations,
                com.massimotter.weave.backend.testing.JpaTestDatabase.transactionManager(dataSource));
    }

    static class OuterTransactionBoundary {

        @Transactional
        public MatrixFacadeClientStateService.MatrixIdentity register(
                MatrixFacadeClientStateService service,
                Jwt jwt,
                String deviceId) {
            return service.register(jwt, deviceId);
        }

        @Transactional
        public void revoke(MatrixFacadeClientStateService service, Jwt jwt) {
            service.revoke(jwt);
        }
    }

}
