package com.massimotter.weave.backend.identity.persistence;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.AdditionalAnswers.delegatesTo;

@Tag("postgres")
@EnabledIfSystemProperty(named = "weave.test.postgres", matches = "true")
class IdentityAdminOperationStorePostgresTest {
    private static final String KEY = "member-operation-key-0001";
    private static final String HASH = "a".repeat(64);

    @Test
    void scopesReplayToOrganizationAndRejectsDifferentOrIncompleteRequests() {
        var source = JpaTestDatabase.entityFirstDataSource("identity_claim_scope");
        var repository = JpaTestDatabase.repository(source, IdentityAdminOperationJpaRepository.class);
        var store = new IdentityAdminOperationStore(repository, JpaTestDatabase.transactionManager(source));
        assertThat(store.claim("org:a", KEY, "revoke-sessions", HASH)).isEmpty();
        assertConflict(() -> store.claim("org:a", KEY, "revoke-sessions", HASH));
        assertThat(store.claim("org:b", KEY, "revoke-sessions", HASH)).isEmpty();
        store.complete("org:a", KEY, "{\"outcome\":\"sessions-revoked\"}");
        assertThat(store.claim("org:a", KEY, "revoke-sessions", HASH))
                .contains("{\"outcome\":\"sessions-revoked\"}");
        assertConflict(() -> store.claim("org:a", KEY, "offboard", HASH));
        assertConflict(() -> store.claim("org:a", KEY, "revoke-sessions", "b".repeat(64)));
        assertConflict(() -> store.claim("org:b", KEY, "revoke-sessions", HASH));
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void durableClaimAndCompletionSurviveAnUnrelatedOuterRollback() {
        var source = JpaTestDatabase.entityFirstDataSource("identity_claim_rollback");
        var repository = JpaTestDatabase.repository(source, IdentityAdminOperationJpaRepository.class);
        var manager = JpaTestDatabase.transactionManager(source);
        var store = new IdentityAdminOperationStore(repository, manager);
        var outer = new TransactionTemplate(manager);
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            assertThat(store.claim("org:a", KEY, "offboard", HASH)).isEmpty();
            throw new IllegalStateException("outer rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("outer rollback");
        assertConflict(() -> store.claim("org:a", KEY, "offboard", HASH));
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            store.complete("org:a", KEY, "completed");
            throw new IllegalStateException("outer rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("outer rollback");
        assertThat(store.claim("org:a", KEY, "offboard", HASH)).contains("completed");
    }

    @Test
    void simultaneousAbsentRowClaimsRecoverAfterTheLosingInsertTransactionEnds() throws Exception {
        var source = JpaTestDatabase.entityFirstDataSource("identity_claim_race");
        var repository = JpaTestDatabase.repository(source, IdentityAdminOperationJpaRepository.class);
        var coordinated = mock(IdentityAdminOperationJpaRepository.class, delegatesTo(repository));
        var firstReads = new AtomicInteger();
        var bothAbsent = new CyclicBarrier(2);
        doAnswer(call -> {
            var result = repository.findById(call.getArgument(0));
            if (firstReads.incrementAndGet() <= 2) {
                assertThat(result).isEmpty();
                bothAbsent.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(coordinated).findById(any(IdentityAdminOperationId.class));
        var manager = JpaTestDatabase.transactionManager(source);
        // Separate instances model independent application processes, not one Java monitor.
        var first = new IdentityAdminOperationStore(coordinated, manager);
        var second = new IdentityAdminOperationStore(coordinated, manager);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var a = threads.submit(() -> claimOutcome(first));
            var b = threads.submit(() -> claimOutcome(second));
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("claimed", "conflict");
        }
        assertThat(repository.count()).isEqualTo(1);
        first.complete("org:a", KEY, "completed");
        assertThat(second.claim("org:a", KEY, "offboard", HASH)).contains("completed");
    }

    private static String claimOutcome(IdentityAdminOperationStore store) {
        try {
            Optional<String> receipt = store.claim("org:a", KEY, "offboard", HASH);
            assertThat(receipt).isEmpty();
            return "claimed";
        } catch (ApiErrorException conflict) {
            assertThat(conflict.status().value()).isEqualTo(409);
            assertThat(conflict.code()).isEqualTo("identity-operation-idempotency-conflict");
            return "conflict";
        }
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiErrorException.class, failure -> {
            assertThat(failure.status().value()).isEqualTo(409);
            assertThat(failure.code()).isEqualTo("identity-operation-idempotency-conflict");
        });
    }
}
