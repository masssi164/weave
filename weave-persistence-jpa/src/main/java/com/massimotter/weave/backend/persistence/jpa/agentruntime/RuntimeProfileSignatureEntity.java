package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@IdClass(RuntimeProfileSignatureId.class)
@Table(name = "weave_agent_runtime_profile_signatures")
public class RuntimeProfileSignatureEntity {
  @Id private String profileHash;
  @Id private String keyId;

  @Column(nullable = false, length = Integer.MAX_VALUE)
  private String protectedHeader;

  @Column(nullable = false, length = Integer.MAX_VALUE)
  private String signature;

  @Column(nullable = false)
  private Instant createdAt;

  protected RuntimeProfileSignatureEntity() {}

  public RuntimeProfileSignatureEntity(
      String hash, String key, String header, String signature, Instant created) {
    profileHash = hash;
    keyId = key;
    protectedHeader = header;
    this.signature = signature;
    createdAt = created;
  }

  public String keyId() {
    return keyId;
  }

  public String protectedHeader() {
    return protectedHeader;
  }

  public String signature() {
    return signature;
  }
}
