package com.massimotter.weave.backend.identity.persistence;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.identity.persistence.IdentityAdminOperationEntity;
import com.massimotter.weave.backend.identity.persistence.IdentityAdminOperationId;
import com.massimotter.weave.backend.identity.persistence.IdentityAdminOperationJpaRepository;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable request claim and response evidence for Keycloak lifecycle side effects. */
public class IdentityAdminOperationStore {
  private final IdentityAdminOperationJpaRepository repository;
  private final Clock clock;
  private final TransactionTemplate transactions;

  @Autowired
  public IdentityAdminOperationStore(IdentityAdminOperationJpaRepository repository, PlatformTransactionManager transactionManager) {
    this(repository, transactionManager, Clock.systemUTC());
  }

  IdentityAdminOperationStore(
      IdentityAdminOperationJpaRepository repository, PlatformTransactionManager transactionManager, Clock clock) {
    this.repository = repository;
    this.clock = clock;
    transactions = new TransactionTemplate(transactionManager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Optional<String> claim(
      String organizationId, String idempotencyKey, String operationKind, String requestHash) {
    requireKey(idempotencyKey);
    IdentityAdminOperationId id =
        new IdentityAdminOperationId(organizationId, idempotencyKey);
    try {
      return transactions.execute(status -> {
        var existing = repository.findById(id);
        if (existing.isPresent()) {
          return resolve(existing.get(), operationKind, requestHash);
        }
        repository.saveAndFlush(new IdentityAdminOperationEntity(
            organizationId, idempotencyKey, operationKind, requestHash, clock.instant()));
        return Optional.<String>empty();
      });
    } catch (DataIntegrityViolationException | PersistenceException concurrentClaim) {
      // The failed insert transaction must finish before reading the concurrent winner.
      return transactions.execute(status -> resolve(repository.findById(id).orElseThrow(() -> concurrentClaim),
          operationKind, requestHash));
    }
  }

  public void complete(String organizationId, String idempotencyKey, String responseJson) {
    transactions.executeWithoutResult(status -> {
      var operation = repository.findById(new IdentityAdminOperationId(organizationId, idempotencyKey))
          .orElseThrow(() -> new IllegalStateException("Identity administration operation is missing"));
      operation.complete(responseJson, clock.instant());
      repository.saveAndFlush(operation);
    });
  }

  private Optional<String> resolve(
      IdentityAdminOperationEntity existing, String operationKind, String requestHash) {
    if (!existing.operationKind().equals(operationKind)
        || !existing.requestHash().equals(requestHash)) {
      throw conflict(
          "The idempotency key is already bound to a different identity operation.");
    }
    if (!"completed".equals(existing.status()) || existing.responseJson() == null) {
      throw conflict(
          "The earlier identity operation has no safe completed response and requires operator reconciliation.");
    }
    return Optional.of(existing.responseJson());
  }

  private void requireKey(String value) {
    if (value == null || value.length() < 16 || value.length() > 128) {
      throw new ApiErrorException(
          HttpStatus.BAD_REQUEST,
          "invalid-idempotency-key",
          "Idempotency-Key must contain between 16 and 128 characters.",
          Map.of());
    }
  }

  private ApiErrorException conflict(String message) {
    return new ApiErrorException(
        HttpStatus.CONFLICT, "identity-operation-idempotency-conflict", message, Map.of());
  }
}
