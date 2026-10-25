package com.massimotter.weave.backend.providerbinding.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderSelectionJpaRepository
    extends JpaRepository<ProviderSelectionJpaEntity, String> {
  List<ProviderSelectionJpaEntity> findAllByOrderByCategoryAsc();
}
