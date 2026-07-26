package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import java.io.Serializable;
import java.util.Objects;

public class RuntimeStateDeletionId implements Serializable {
  private String organizationRef;
  private String personRef;
  private String idempotencyKey;

  protected RuntimeStateDeletionId() {}

  public RuntimeStateDeletionId(String organizationRef, String personRef, String idempotencyKey) {
    this.organizationRef = organizationRef;
    this.personRef = personRef;
    this.idempotencyKey = idempotencyKey;
  }

  @Override
  public boolean equals(Object candidate) {
    return candidate instanceof RuntimeStateDeletionId other
        && Objects.equals(organizationRef, other.organizationRef)
        && Objects.equals(personRef, other.personRef)
        && Objects.equals(idempotencyKey, other.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationRef, personRef, idempotencyKey);
  }
}
