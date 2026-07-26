package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuntimeCellJpaRepository extends JpaRepository<RuntimeCellEntity, UUID> {
  Optional<RuntimeCellEntity> findByOrganizationRefAndPersonRef(
      String organizationRef, String personRef);

  Optional<RuntimeCellEntity> findByCellRef(String cellRef);

  Optional<RuntimeCellEntity> findByWorkloadIssuerAndWorkloadSubject(String issuer, String subject);

  List<RuntimeCellEntity> findAllByOrderByCellRefAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select cell from RuntimeCellEntity cell where cell.cellRef = :cellRef")
  Optional<RuntimeCellEntity> findLockedByCellRef(@Param("cellRef") String cellRef);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select cell from RuntimeCellEntity cell
       where cell.organizationRef = :organizationRef and cell.personRef = :personRef
      """)
  Optional<RuntimeCellEntity> findLockedByOrganizationRefAndPersonRef(
      @Param("organizationRef") String organizationRef, @Param("personRef") String personRef);
}
