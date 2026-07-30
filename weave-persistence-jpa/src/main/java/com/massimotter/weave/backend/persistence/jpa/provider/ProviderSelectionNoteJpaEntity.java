package com.massimotter.weave.backend.persistence.jpa.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@IdClass(ProviderSelectionNoteId.class)
@Table(name = "weave_provider_selection_notes")
public class ProviderSelectionNoteJpaEntity {

  @Id
  @Column(name = "category", length = 80, nullable = false, updatable = false)
  private String category;

  @Id
  @Column(name = "note_order", nullable = false, updatable = false)
  private int noteOrder;

  @Column(name = "note_text", length = 1024, nullable = false)
  private String noteText;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "category",
      nullable = false,
      insertable = false,
      updatable = false)
  private ProviderSelectionJpaEntity selection;

  protected ProviderSelectionNoteJpaEntity() {}

  ProviderSelectionNoteJpaEntity(
      ProviderSelectionJpaEntity selection,
      String category,
      int noteOrder,
      String noteText) {
    this.selection = selection;
    this.category = category;
    this.noteOrder = noteOrder;
    this.noteText = noteText;
  }

  String noteText() {
    return noteText;
  }
}
