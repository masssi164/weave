package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

/** Keep the former SQL-only path invariants in the owning declarative entity mapping. */
class FilesUserResourceSchemaContractTest {
    private final Table table = FilesUserResourceJpaEntity.class.getAnnotation(Table.class);

    @Test
    void activePathsAreUniqueWithinAnOrganizationAndBinding() {
        assertThat(table.uniqueConstraints()).anySatisfy(constraint -> {
            assertThat(constraint.name()).isEqualTo("uq_weave_files_user_resources_active_path");
            assertThat(constraint.columnNames())
                    .containsExactly("organization_ref", "binding_revision", "active_path_key");
        });
    }

    @Test
    void parentLookupsKeepTheirDeclaredIndex() {
        assertThat(table.indexes()).anySatisfy(index -> {
            assertThat(index.name()).isEqualTo("ix_weave_files_user_resources_parent");
            assertThat(index.columnList()).isEqualTo("organization_ref,parent_file_id,lifecycle_state");
        });
    }

    @Test
    void lifecycleAndActivePathRemainOneDeclaredInvariant() {
        assertThat(table.check()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("ck_weave_files_user_resources_active_path");
            assertThat(check.constraint()).contains("active_path_key = canonical_path",
                    "lifecycle_state = 'TOMBSTONED'", "active_path_key IS NULL");
        });
    }
}
