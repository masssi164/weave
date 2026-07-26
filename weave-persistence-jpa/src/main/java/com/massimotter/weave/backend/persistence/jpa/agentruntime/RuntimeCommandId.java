package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import java.io.Serializable;
import java.util.Objects;

public class RuntimeCommandId implements Serializable {
  private String organizationRef;
  private String personRef;
  private String idempotencyKey;

  protected RuntimeCommandId() {}

  public RuntimeCommandId(String organizationRef, String personRef, String idempotencyKey) {
    this.organizationRef = organizationRef;
    this.personRef = personRef;
    this.idempotencyKey = idempotencyKey;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof RuntimeCommandId id
        && Objects.equals(organizationRef, id.organizationRef)
        && Objects.equals(personRef, id.personRef)
        && Objects.equals(idempotencyKey, id.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationRef, personRef, idempotencyKey);
  }
}
