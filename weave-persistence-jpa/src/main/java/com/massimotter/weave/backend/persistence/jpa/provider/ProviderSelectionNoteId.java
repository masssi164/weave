package com.massimotter.weave.backend.persistence.jpa.provider;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class ProviderSelectionNoteId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String category;
  private int noteOrder;

  public ProviderSelectionNoteId() {}

  public ProviderSelectionNoteId(String category, int noteOrder) {
    this.category = category;
    this.noteOrder = noteOrder;
  }

  @Override
  public boolean equals(Object candidate) {
    return this == candidate
        || candidate instanceof ProviderSelectionNoteId other
            && noteOrder == other.noteOrder
            && Objects.equals(category, other.category);
  }

  @Override
  public int hashCode() {
    return Objects.hash(category, noteOrder);
  }
}
