package com.massimotter.weave.mcp;

import java.time.Instant;
import java.util.Set;

/** Token value is intentionally excluded from toString, equality, logs, and API projections. */
final class ExchangedAccessToken {
  private final String value;
  private final String subject;
  private final String authorizedParty;
  private final Set<String> audiences;
  private final Set<String> scopes;
  private final Instant issuedAt;
  private final Instant expiresAt;

  ExchangedAccessToken(
      String value,
      String subject,
      String authorizedParty,
      Set<String> audiences,
      Set<String> scopes,
      Instant issuedAt,
      Instant expiresAt) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("An exchanged access token value is required");
    }
    this.value = value;
    this.subject = subject;
    this.authorizedParty = authorizedParty;
    this.audiences = Set.copyOf(audiences);
    this.scopes = Set.copyOf(scopes);
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
  }

  String value() {
    return value;
  }

  String subject() {
    return subject;
  }

  String authorizedParty() {
    return authorizedParty;
  }

  Set<String> audiences() {
    return audiences;
  }

  Set<String> scopes() {
    return scopes;
  }

  Instant issuedAt() {
    return issuedAt;
  }

  Instant expiresAt() {
    return expiresAt;
  }

  @Override
  public String toString() {
    return "ExchangedAccessToken[redacted]";
  }
}
