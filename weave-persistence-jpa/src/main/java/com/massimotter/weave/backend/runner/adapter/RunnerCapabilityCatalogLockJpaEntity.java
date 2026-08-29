package com.massimotter.weave.backend.runner.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single durable mutex used only while an organization capability catalog is first materialized.
 *
 * <p>Public bundle publication is rare compared with task claims. Serializing this initialization
 * keeps the persistence boundary JPA-only and avoids a native upsert escape hatch.
 */
@Entity
@Table(name = "weave_runner_capability_catalog_locks")
class RunnerCapabilityCatalogLockJpaEntity {

    static final String PUBLICATION_LOCK_ID = "public-capability-catalog";

    @Id
    @Column(name = "lock_id", nullable = false, length = 64, updatable = false)
    private String lockId;

    protected RunnerCapabilityCatalogLockJpaEntity() {}
}
