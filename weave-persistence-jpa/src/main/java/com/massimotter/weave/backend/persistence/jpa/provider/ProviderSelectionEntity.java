package com.massimotter.weave.backend.persistence.jpa.provider;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "weave_provider_selections")
public class ProviderSelectionEntity {

  @Id
  @Column(name = "category", length = 80, nullable = false)
  private String category;

  @Column(name = "provider_key", length = 160, nullable = false)
  private String providerKey;

  @Column(name = "choice_model", length = 80, nullable = false)
  private String choiceModel;

  @Column(name = "secret_ref", length = 255)
  private String secretRef;

  @Column(name = "selected_by", length = 160, nullable = false)
  private String selectedBy;

  @Column(name = "selected_at_utc", nullable = false)
  private Instant selectedAt;

  @Column(name = "applied", nullable = false)
  private boolean applied;

  @Column(name = "support_safe", nullable = false)
  private boolean supportSafe;

  @Column(name = "migration_dry_run_required", nullable = false)
  private boolean migrationDryRunRequired;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "weave_provider_selection_notes",
      joinColumns = @JoinColumn(name = "category", nullable = false))
  @OrderColumn(name = "note_order")
  @Column(name = "note_text", length = 1024, nullable = false)
  private List<String> lossyMappingNotes = new ArrayList<>();

  protected ProviderSelectionEntity() {}

  public ProviderSelectionEntity(
      String category,
      String providerKey,
      String choiceModel,
      String secretRef,
      String selectedBy,
      Instant selectedAt,
      boolean applied,
      boolean supportSafe,
      boolean migrationDryRunRequired,
      List<String> lossyMappingNotes) {
    this.category = category;
    this.providerKey = providerKey;
    this.choiceModel = choiceModel;
    this.secretRef = secretRef;
    this.selectedBy = selectedBy;
    this.selectedAt = selectedAt;
    this.applied = applied;
    this.supportSafe = supportSafe;
    this.migrationDryRunRequired = migrationDryRunRequired;
    this.lossyMappingNotes =
        new ArrayList<>(lossyMappingNotes == null ? List.of() : lossyMappingNotes);
  }

  public String category() {
    return category;
  }

  public String providerKey() {
    return providerKey;
  }

  public String choiceModel() {
    return choiceModel;
  }

  public String secretRef() {
    return secretRef;
  }

  public String selectedBy() {
    return selectedBy;
  }

  public Instant selectedAt() {
    return selectedAt;
  }

  public boolean applied() {
    return applied;
  }

  public boolean supportSafe() {
    return supportSafe;
  }

  public boolean migrationDryRunRequired() {
    return migrationDryRunRequired;
  }

  public List<String> lossyMappingNotes() {
    return List.copyOf(lossyMappingNotes);
  }
}
