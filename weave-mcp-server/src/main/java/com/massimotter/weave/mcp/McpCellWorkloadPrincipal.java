package com.massimotter.weave.mcp;

import java.time.Instant;
import java.util.Set;

record McpCellWorkloadPrincipal(
    String issuer,
    String subject,
    String clientId,
    Set<String> scopes,
    Instant issuedAt,
    Instant expiresAt,
    String tokenId) {

  McpCellWorkloadPrincipal {
    scopes = Set.copyOf(scopes);
  }
}
