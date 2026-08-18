package com.massimotter.weave.backend.data.adapter.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTransferCheckpointRepository extends JpaRepository<
        JpaTransferCheckpointEntity,
        JpaTransferCheckpointEntity.JpaTransferCheckpointId> {
}
