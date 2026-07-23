package com.massimotter.weave.backend.provider;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_provider_selections")
class ProviderSelectionJpaEntity {

    @Id
    @Column(name = "category", nullable = false, length = 80)
    private String category;

    @Column(name = "provider_key", nullable = false, length = 160)
    private String providerKey;

    @Column(name = "choice_model", nullable = false, length = 80)
    private String choiceModel;

    @Column(name = "secret_ref", length = 255)
    private String secretRef;

    @Column(name = "selected_by", nullable = false, length = 160)
    private String selectedBy;

    @Column(name = "selected_at_utc", nullable = false)
    private OffsetDateTime selectedAt;

    @Column(name = "applied", nullable = false)
    private boolean applied;

    @Column(name = "support_safe", nullable = false)
    private boolean supportSafe;

    @Column(name = "migration_dry_run_required", nullable = false)
    private boolean migrationDryRunRequired;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(
            mappedBy = "selection",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("id.noteOrder ASC")
    private final List<ProviderSelectionNoteJpaEntity> notes = new ArrayList<>();

    protected ProviderSelectionJpaEntity() {
    }

    private ProviderSelectionJpaEntity(String category) {
        this.category = category;
    }

    static ProviderSelectionJpaEntity create(ProviderSelection selection) {
        return new ProviderSelectionJpaEntity(selection.category());
    }

    void replaceWith(ProviderSelection selection) {
        providerKey = selection.providerKey();
        choiceModel = selection.choiceModel();
        secretRef = selection.secretRef();
        selectedBy = selection.selectedBy();
        selectedAt = selection.selectedAt().atOffset(ZoneOffset.UTC);
        applied = selection.applied();
        supportSafe = selection.supportSafe();
        migrationDryRunRequired = selection.migrationDryRunRequired();
        notes.clear();
        for (int index = 0; index < selection.lossyMappingNotes().size(); index++) {
            notes.add(new ProviderSelectionNoteJpaEntity(
                    this,
                    index,
                    selection.lossyMappingNotes().get(index)));
        }
    }

    ProviderSelection toDomain() {
        return new ProviderSelection(
                category,
                providerKey,
                choiceModel,
                secretRef,
                selectedBy,
                selectedAt.toInstant(),
                applied,
                supportSafe,
                migrationDryRunRequired,
                notes.stream().map(ProviderSelectionNoteJpaEntity::noteText).toList());
    }
}

@Entity
@Table(name = "weave_provider_selection_notes")
class ProviderSelectionNoteJpaEntity {

    @EmbeddedId
    private ProviderSelectionNoteId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("category")
    @JoinColumn(name = "category", nullable = false)
    private ProviderSelectionJpaEntity selection;

    @Column(name = "note_text", nullable = false, length = 1024)
    private String noteText;

    protected ProviderSelectionNoteJpaEntity() {
    }

    ProviderSelectionNoteJpaEntity(
            ProviderSelectionJpaEntity selection,
            int noteOrder,
            String noteText) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.id = new ProviderSelectionNoteId(null, noteOrder);
        this.noteText = Objects.requireNonNull(noteText, "noteText");
    }

    String noteText() {
        return noteText;
    }
}

@Embeddable
class ProviderSelectionNoteId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "category", nullable = false, length = 80)
    private String category;

    @Column(name = "note_order", nullable = false)
    private int noteOrder;

    protected ProviderSelectionNoteId() {
    }

    ProviderSelectionNoteId(String category, int noteOrder) {
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

interface ProviderSelectionJpaRepository extends JpaRepository<ProviderSelectionJpaEntity, String> {

    @EntityGraph(attributePaths = "notes")
    @Query("select selection from ProviderSelectionJpaEntity selection where selection.category = :category")
    java.util.Optional<ProviderSelectionJpaEntity> findAggregateByCategory(
            @Param("category") String category);

    @EntityGraph(attributePaths = "notes")
    @Query("select distinct selection from ProviderSelectionJpaEntity selection order by selection.category")
    List<ProviderSelectionJpaEntity> findAllAggregates();
}
