package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import java.time.Instant;

public interface RuntimeCommandRepository {
  RuntimeCommandReceipt claim(
      String organizationRef,
      String personRef,
      String idempotencyKey,
      String command,
      String proposedCellRef,
      String auditRef,
      Instant now);

  RuntimeCommandReceipt complete(RuntimeCommandReceipt receipt, long runtimeVersion, Instant now);

  RuntimeCommandReceipt fail(RuntimeCommandReceipt receipt, String failureCode, Instant now);
}
