package com.massimotter.weave.backend.persistence.jpa.readiness;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import java.time.Duration;

/**
 * Executes a small, provider-neutral JPA query to prove that the configured persistence unit can
 * reach its database.
 */
public final class JpaPersistenceReadinessProbe {

  public static final Duration QUERY_TIMEOUT = Duration.ofSeconds(2);

  private final EntityManagerFactory entityManagerFactory;

  public JpaPersistenceReadinessProbe(EntityManagerFactory entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;
  }

  public boolean isReady() {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    try {
      CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
      CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
      criteriaQuery.select(criteriaBuilder.literal(1));
      TypedQuery<Integer> query = entityManager.createQuery(criteriaQuery);
      query.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT.toMillis());
      return Integer.valueOf(1).equals(query.getSingleResult());
    } finally {
      entityManager.close();
    }
  }
}
