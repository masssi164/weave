package com.massimotter.weave.backend.persistence.jpa.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "weave_keycloak_event_receipts")
public class KeycloakEventReceiptEntity {

  @Id
  @Column(name = "event_id", length = 200, nullable = false)
  private String eventId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  protected KeycloakEventReceiptEntity() {}

  public KeycloakEventReceiptEntity(String eventId, Instant occurredAt, Instant receivedAt) {
    this.eventId = eventId;
    this.occurredAt = occurredAt;
    this.receivedAt = receivedAt;
  }
}
