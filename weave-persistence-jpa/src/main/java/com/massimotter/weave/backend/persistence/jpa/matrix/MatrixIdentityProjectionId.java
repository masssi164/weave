package com.massimotter.weave.backend.persistence.jpa.matrix;

import java.io.Serializable;
import java.util.Objects;

public final class MatrixIdentityProjectionId implements Serializable {

  private String tenantId;
  private String identityIssuer;
  private String matrixUserId;

  protected MatrixIdentityProjectionId() {}

  public MatrixIdentityProjectionId(String tenantId, String identityIssuer, String matrixUserId) {
    this.tenantId = tenantId;
    this.identityIssuer = identityIssuer;
    this.matrixUserId = matrixUserId;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof MatrixIdentityProjectionId that
            && Objects.equals(tenantId, that.tenantId)
            && Objects.equals(identityIssuer, that.identityIssuer)
            && Objects.equals(matrixUserId, that.matrixUserId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, identityIssuer, matrixUserId);
  }
}
