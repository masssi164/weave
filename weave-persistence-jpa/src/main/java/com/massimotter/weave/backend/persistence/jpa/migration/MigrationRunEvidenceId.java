package com.massimotter.weave.backend.persistence.jpa.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MigrationRunEvidenceId implements Serializable {

  @Column(name = "run_id", length = 180, nullable = false)
  private String runId;

  @Column(name = "domain_key", length = 120, nullable = false)
  private String domainKey;

  protected MigrationRunEvidenceId() {}

  public MigrationRunEvidenceId(String runId, String domainKey) {
    this.runId = runId;
    this.domainKey = domainKey;
  }

  public String runId() {
    return runId;
  }

  public String domainKey() {
    return domainKey;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof MigrationRunEvidenceId that
            && Objects.equals(runId, that.runId)
            && Objects.equals(domainKey, that.domainKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(runId, domainKey);
  }
}
