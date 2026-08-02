package com.massimotter.weave.backend.identity;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.persistence.jpa.identity.IdentityAdminOperationEntity;
import com.massimotter.weave.backend.persistence.jpa.identity.IdentityAdminOperationId;
import com.massimotter.weave.backend.persistence.jpa.identity.IdentityAdminOperationJpaRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable request claim and response evidence for Keycloak lifecycle side effects. */
@Component
public class IdentityAdminOperationStore {
  private final IdentityAdminOperationJpaRepository repository;
  private final Clock clock;

  @Autowired
  public IdentityAdminOperationStore(IdentityAdminOperationJpaRepository repository) {
    this(repository, Clock.systemUTC());
  }

  IdentityAdminOperationStore(
      IdentityAdminOperationJpaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<String> claim(
      String organizationId, String idempotencyKey, String operationKind, String requestHash) {
    requireKey(idempotencyKey);
    IdentityAdminOperationId id =
        new IdentityAdminOperationId(organizationId, idempotencyKey);
    var existing = repository.findById(id);
    if (existing.isPresent()) {
      return resolve(existing.get(), operationKind, requestHash);
    }
    try {
      repository.saveAndFlush(
          new IdentityAdminOperationEntity(
              organizationId, idempotencyKey, operationKind, requestHash, clock.instant()));
      return Optional.empty();
    } catch (DataIntegrityViolationException concurrentClaim) {
      return resolve(
          repository
              .findById(id)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Identity administration idempotency claim was lost")),
          operationKind,
          requestHash);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void complete(
      String organizationId, String idempotencyKey, String responseJson) {
    IdentityAdminOperationEntity operation =
        repository
            .findById(new IdentityAdminOperationId(organizationId, idempotencyKey))
            .orElseThrow(
                () -> new IllegalStateException("Identity administration operation is missing"));
    operation.complete(responseJson, clock.instant());
    repository.saveAndFlush(operation);
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
