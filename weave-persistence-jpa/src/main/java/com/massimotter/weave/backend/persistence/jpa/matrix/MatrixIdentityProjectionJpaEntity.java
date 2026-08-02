package com.massimotter.weave.backend.persistence.jpa.matrix;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@IdClass(MatrixIdentityProjectionId.class)
@Table(name = "weave_matrix_identity_projection")
public class MatrixIdentityProjectionJpaEntity {

  @Id
  @Column(name = "tenant_id", length = 160, nullable = false)
  private String tenantId;

  @Id
  @Column(name = "identity_issuer", length = 512, nullable = false)
  private String identityIssuer;

  @Id
  @Column(name = "matrix_user_id", length = 255, nullable = false)
  private String matrixUserId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "actor_ref", length = 255, nullable = false)
  private String actorRef;

  @Column(name = "authorization_principal_ref", length = 255, nullable = false)
  private String authorizationPrincipalRef;

  @Column(name = "updated_at_utc", nullable = false)
  private Instant updatedAt;

  protected MatrixIdentityProjectionJpaEntity() {}

  public MatrixIdentityProjectionJpaEntity(
      String tenantId,
      String identityIssuer,
      String matrixUserId,
      String actorRef,
      String authorizationPrincipalRef,
      Instant updatedAt) {
    this.tenantId = tenantId;
    this.identityIssuer = identityIssuer;
    this.matrixUserId = matrixUserId;
    this.actorRef = actorRef;
    this.authorizationPrincipalRef = authorizationPrincipalRef;
    this.updatedAt = updatedAt;
  }

  public String tenantId() {
    return tenantId;
  }

  public String identityIssuer() {
    return identityIssuer;
  }

  public String matrixUserId() {
    return matrixUserId;
  }

  public String actorRef() {
    return actorRef;
  }

  public String authorizationPrincipalRef() {
    return authorizationPrincipalRef;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public void refreshAuthorizationProjection(
      String authorizationPrincipalRef, Instant updatedAt) {
    this.authorizationPrincipalRef = authorizationPrincipalRef;
    this.updatedAt = updatedAt;
  }
}
