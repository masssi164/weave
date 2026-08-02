package com.massimotter.weave.backend.persistence.jpa.identity;

import java.io.Serializable;
import java.util.Objects;

public final class IdentityAdminOperationId implements Serializable {
  private String organizationId;
  private String idempotencyKey;

  public IdentityAdminOperationId() {}

  public IdentityAdminOperationId(String organizationId, String idempotencyKey) {
    this.organizationId = organizationId;
    this.idempotencyKey = idempotencyKey;
  }

  @Override
  public boolean equals(Object value) {
    return value instanceof IdentityAdminOperationId other
        && Objects.equals(organizationId, other.organizationId)
        && Objects.equals(idempotencyKey, other.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, idempotencyKey);
  }
}
