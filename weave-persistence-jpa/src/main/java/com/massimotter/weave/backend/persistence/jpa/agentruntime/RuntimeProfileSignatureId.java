package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import java.io.Serializable;
import java.util.Objects;

public class RuntimeProfileSignatureId implements Serializable {
  private String profileHash;
  private String keyId;

  protected RuntimeProfileSignatureId() {}

  public RuntimeProfileSignatureId(String profileHash, String keyId) {
    this.profileHash = profileHash;
    this.keyId = keyId;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RuntimeProfileSignatureId i
        && Objects.equals(profileHash, i.profileHash)
        && Objects.equals(keyId, i.keyId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(profileHash, keyId);
  }
}
