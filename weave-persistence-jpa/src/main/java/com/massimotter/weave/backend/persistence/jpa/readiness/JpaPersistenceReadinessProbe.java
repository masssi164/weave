package com.massimotter.weave.backend.persistence.jpa.readiness;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaRepository;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Executes a small, provider-neutral JPA query to prove that the configured persistence unit can
 * reach its database.
 */
public final class JpaPersistenceReadinessProbe {

  public static final Duration QUERY_TIMEOUT = Duration.ofSeconds(2);

  private final EntityManagerFactory entityManagerFactory;
  private final SchemaAuthorityJpaRepository schemaAuthority;
  private final boolean markerRequired;
  private final String candidateCommit;
  private final BooleanSupplier additionalAuthorityEvidence;

  public JpaPersistenceReadinessProbe(EntityManagerFactory entityManagerFactory) {
    this(entityManagerFactory, null, false, "", () -> true);
  }

  public JpaPersistenceReadinessProbe(
      EntityManagerFactory entityManagerFactory,
      SchemaAuthorityJpaRepository schemaAuthority,
      boolean markerRequired,
      String candidateCommit) {
    this(entityManagerFactory, schemaAuthority, markerRequired, candidateCommit, () -> true);
  }

  public JpaPersistenceReadinessProbe(
      EntityManagerFactory entityManagerFactory,
      SchemaAuthorityJpaRepository schemaAuthority,
      boolean markerRequired,
      String candidateCommit,
      BooleanSupplier additionalAuthorityEvidence) {
    this.entityManagerFactory = entityManagerFactory;
    this.schemaAuthority = schemaAuthority;
    this.markerRequired = markerRequired;
    this.candidateCommit = candidateCommit == null ? "" : candidateCommit;
    this.additionalAuthorityEvidence = Objects.requireNonNull(additionalAuthorityEvidence);
  }

  public boolean isReady() {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    try {
      CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
      CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
      criteriaQuery.select(criteriaBuilder.literal(1));
      TypedQuery<Integer> query = entityManager.createQuery(criteriaQuery);
      query.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT.toMillis());
      if (!Integer.valueOf(1).equals(query.getSingleResult())) {
        return false;
      }
      if (!markerRequired) {
        return true;
      }
      if (schemaAuthority == null || !candidateCommit.matches("[0-9a-f]{40}")) {
        return false;
      }
      var markers = schemaAuthority.findAll();
      return markers.size() == 1
          && Objects.equals(markers.getFirst().epoch(), "weave-flyway-v1")
          && Objects.equals(
              markers.getFirst().relationalModelId(), "WEAVE-ARCH-RELATIONAL-CORE-MODEL")
          && Objects.equals(markers.getFirst().candidateCommit(), candidateCommit)
          && markers.getFirst().catalogFingerprint().matches("[0-9a-f]{64}")
          && markers.getFirst().completedAt() != null
          && additionalAuthorityEvidence.getAsBoolean();
    } finally {
      entityManager.close();
    }
  }
}
