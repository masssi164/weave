package com.massimotter.weave.backend.persistence.jpa.provider;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderSelectionJpaRepository
    extends JpaRepository<ProviderSelectionEntity, String> {
  List<ProviderSelectionEntity> findAllByOrderByCategoryAsc();
}
