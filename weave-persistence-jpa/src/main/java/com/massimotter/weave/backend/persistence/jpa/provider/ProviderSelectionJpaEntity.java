package com.massimotter.weave.backend.persistence.jpa.provider;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "weave_provider_selections")
public class ProviderSelectionJpaEntity {

  @Id
  @Column(name = "category", length = 80, nullable = false)
  private String category;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

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

  @OneToMany(
      mappedBy = "selection",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("noteOrder ASC")
  private List<ProviderSelectionNoteJpaEntity> lossyMappingNotes = new ArrayList<>();

  protected ProviderSelectionJpaEntity() {}

  public ProviderSelectionJpaEntity(
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
    List<String> notes = lossyMappingNotes == null ? List.of() : List.copyOf(lossyMappingNotes);
    this.lossyMappingNotes = new ArrayList<>(notes.size());
    for (int index = 0; index < notes.size(); index++) {
      this.lossyMappingNotes.add(
          new ProviderSelectionNoteJpaEntity(this, category, index, notes.get(index)));
    }
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
    return lossyMappingNotes.stream()
        .map(ProviderSelectionNoteJpaEntity::noteText)
        .toList();
  }
}
