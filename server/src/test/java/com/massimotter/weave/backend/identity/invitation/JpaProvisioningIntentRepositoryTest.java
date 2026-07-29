package com.massimotter.weave.backend.identity.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.persistence.jpa.identity.ProvisioningIntentJpaRepository;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JpaProvisioningIntentRepositoryTest {

    @Test
    void repeatedSaveUpdatesTheAssignedIdentifierInsteadOfInsertingItAgain() {
        var database = JpaTestDatabase.entityFirstDataSource("identity-provisioning-intent");
        JpaProvisioningIntentRepository repository = repository(database);
        Instant createdAt = Instant.parse("2026-07-29T19:00:00Z");
        ProvisioningIntent pending = intent(UUID.randomUUID(), createdAt);

        repository.save(pending);
        ProvisioningIntent linked =
                pending.withProviderInvitation("provider-invitation-1", createdAt.plusSeconds(1));
        repository.save(linked);

        assertThat(repository.findById(pending.intentId())).contains(linked);
        assertThat(repository.findByProviderInvitationId("provider-invitation-1")).contains(linked);
    }

    @Test
    void anExistingIntentCannotBeReboundToAnotherImmutableIdentity() {
        var database = JpaTestDatabase.entityFirstDataSource("identity-provisioning-immutable");
        JpaProvisioningIntentRepository repository = repository(database);
        Instant createdAt = Instant.parse("2026-07-29T19:00:00Z");
        ProvisioningIntent pending = intent(UUID.randomUUID(), createdAt);
        repository.save(pending);
        ProvisioningIntent rebound = new ProvisioningIntent(
                pending.intentId(),
                pending.tenantId(),
                "organization:another",
                pending.invitedEmail(),
                pending.invitedEmailSha256(),
                pending.requestedRole(),
                pending.providerInvitationId(),
                pending.invitedByIssuer(),
                pending.invitedBySubject(),
                pending.auditCorrelation(),
                pending.status(),
                pending.appliedSubject(),
                pending.failureCode(),
                pending.expiresAt(),
                pending.createdAt(),
                createdAt.plusSeconds(1));

        assertThatThrownBy(() -> repository.save(rebound))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provisioning intent immutable identity changed");
    }

    private static JpaProvisioningIntentRepository repository(
            javax.sql.DataSource database) {
        return new JpaProvisioningIntentRepository(JpaTestDatabase.repository(
                database, ProvisioningIntentJpaRepository.class));
    }

    private static ProvisioningIntent intent(UUID id, Instant createdAt) {
        return new ProvisioningIntent(
                id,
                "tenant:weave",
                "organization:weave",
                "owner@weave.test",
                "a".repeat(64),
                "owner",
                null,
                "urn:weave:identity-bootstrap",
                "bootstrap-owner-invitation",
                "test:idempotency",
                ProvisioningIntentStatus.PENDING,
                null,
                null,
                createdAt.plusSeconds(86_400),
                createdAt,
                createdAt);
    }
}
