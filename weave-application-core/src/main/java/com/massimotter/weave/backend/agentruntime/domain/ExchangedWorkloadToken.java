package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.Set;

/**
 * Cryptographically authenticated and downscoped token presented to the workload use case.
 *
 * <p>The transport adapter verifies the JWT and constructs this value. Raw bearer values and Spring
 * Security types never enter the application core.
 */
public record ExchangedWorkloadToken(
    String issuer,
    String subject,
    String edgeClientId,
    Set<String> scopes,
    Instant issuedAt,
    Instant expiresAt,
    String tokenId) {

  public ExchangedWorkloadToken {
    requireText(issuer, "issuer");
    requireText(subject, "subject");
    if (!"weave-mcp-server".equals(edgeClientId)) {
      throw new IllegalArgumentException("edgeClientId must be weave-mcp-server");
    }
    scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
    if (scopes.isEmpty()) {
      throw new IllegalArgumentException("scopes must be non-empty");
    }
    if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("a bounded token lifetime is required");
    }
    requireText(tokenId, "tokenId");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 500) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
  }
}
