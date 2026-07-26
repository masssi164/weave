package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCommandEntity;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCommandId;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCommandJpaRepository;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class JpaRuntimeCommandRepository implements RuntimeCommandRepository {
  private final RuntimeCommandJpaRepository commands;

  public JpaRuntimeCommandRepository(RuntimeCommandJpaRepository commands) {
    this.commands = java.util.Objects.requireNonNull(commands);
  }

  @Override
  @Transactional
  public RuntimeCommandReceipt claim(
      String org,
      String person,
      String key,
      String command,
      String cell,
      String audit,
      Instant now) {
    RuntimeCommandId id = new RuntimeCommandId(org, person, key);
    var existing = commands.findById(id);
    if (existing.isPresent()) return same(existing.orElseThrow(), command, cell);
    try {
      return map(
          commands.saveAndFlush(
              new RuntimeCommandEntity(
                  org, person, key, command, "STARTED", cell, null, audit, null, now, now)));
    } catch (DataIntegrityViolationException conflict) {
      return same(commands.findById(id).orElseThrow(() -> conflict), command, cell);
    }
  }

  @Override
  @Transactional
  public RuntimeCommandReceipt complete(RuntimeCommandReceipt receipt, long version, Instant now) {
    RuntimeCommandEntity entity = required(receipt);
    if ("COMPLETED".equals(entity.status())) {
      if (!java.util.Objects.equals(entity.runtimeVersion(), version))
        throw new RuntimeCommandConflictException(
            "command completion conflicts with the stored receipt");
      return map(entity);
    }
    if (!entity.command().equals(receipt.command()))
      throw new RuntimeCommandConflictException(
          "command completion conflicts with the stored receipt");
    entity.complete(version, now);
    return map(commands.saveAndFlush(entity));
  }

  @Override
  @Transactional
  public RuntimeCommandReceipt fail(RuntimeCommandReceipt receipt, String code, Instant now) {
    RuntimeCommandEntity entity = required(receipt);
    if (!"COMPLETED".equals(entity.status())) entity.fail(code, now);
    return map(commands.saveAndFlush(entity));
  }

  private RuntimeCommandEntity required(RuntimeCommandReceipt r) {
    return commands
        .findById(new RuntimeCommandId(r.organizationRef(), r.personRef(), r.idempotencyKey()))
        .orElseThrow();
  }

  private static RuntimeCommandReceipt same(RuntimeCommandEntity e, String c, String cell) {
    if (!e.command().equals(c) || !e.cellRef().equals(cell))
      throw new RuntimeCommandConflictException(
          "idempotency key is already bound to another command");
    return map(e);
  }

  private static RuntimeCommandReceipt map(RuntimeCommandEntity e) {
    return new RuntimeCommandReceipt(
        e.organizationRef(),
        e.personRef(),
        e.idempotencyKey(),
        e.command(),
        RuntimeCommandReceipt.Status.valueOf(e.status()),
        e.cellRef(),
        e.runtimeVersion(),
        e.auditRef(),
        e.failureCode(),
        e.createdAt(),
        e.updatedAt());
  }
}
