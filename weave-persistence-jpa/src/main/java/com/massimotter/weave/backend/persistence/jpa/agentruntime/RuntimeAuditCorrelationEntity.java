package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weave_agent_runtime_audit_correlations")
public class RuntimeAuditCorrelationEntity {
  @Id private UUID recordId;

  @Column(nullable = false, unique = true)
  private String correlationRef;

  @Column(nullable = false, length = 71)
  private String organizationRefHash;

  @Column(nullable = false, length = 71)
  private String personRefHash;

  private String keycloakRefHash;
  private String orchestratorRefHash;
  private String openclawRefHash;
  private String matrixRefHash;
  private String mcpRefHash;
  private String domainAuditRefHash;

  @Column(nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private Instant createdAt;

  protected RuntimeAuditCorrelationEntity() {}

  public RuntimeAuditCorrelationEntity(
      UUID id,
      String ref,
      String org,
      String person,
      String keycloak,
      String orchestrator,
      String openclaw,
      String matrix,
      String mcp,
      String domain,
      Instant occurred,
      Instant created) {
    recordId = id;
    correlationRef = ref;
    organizationRefHash = org;
    personRefHash = person;
    keycloakRefHash = keycloak;
    orchestratorRefHash = orchestrator;
    openclawRefHash = openclaw;
    matrixRefHash = matrix;
    mcpRefHash = mcp;
    domainAuditRefHash = domain;
    occurredAt = occurred;
    createdAt = created;
  }

  public UUID recordId() {
    return recordId;
  }

  public String correlationRef() {
    return correlationRef;
  }

  public String organizationRefHash() {
    return organizationRefHash;
  }

  public String personRefHash() {
    return personRefHash;
  }

  public String keycloakRefHash() {
    return keycloakRefHash;
  }

  public String orchestratorRefHash() {
    return orchestratorRefHash;
  }

  public String openclawRefHash() {
    return openclawRefHash;
  }

  public String matrixRefHash() {
    return matrixRefHash;
  }

  public String mcpRefHash() {
    return mcpRefHash;
  }

  public String domainAuditRefHash() {
    return domainAuditRefHash;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
