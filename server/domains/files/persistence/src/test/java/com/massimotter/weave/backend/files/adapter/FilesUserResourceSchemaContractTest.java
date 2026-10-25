package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

/** Keep path integrity in the owning entity, including SQL's three-valued null semantics. */
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
    void activePathCheckCannotAcceptUnknownFromANullKey() {
        assertThat(table.check()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("ck_weave_files_user_resources_active_path");
            assertThat(check.constraint()).contains("active_path_key IS NOT NULL",
                    "active_path_key = canonical_path", "lifecycle_state = 'TOMBSTONED'",
                    "active_path_key IS NULL");
        });
    }

    @Test
    void bindingRevisionMatchesTheDomainInvariant() {
        assertThat(table.check()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("ck_weave_files_user_resources_binding_revision");
            assertThat(check.constraint()).isEqualTo("binding_revision > 0");
        });
    }
}
