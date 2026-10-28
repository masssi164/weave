package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

/** Prevent accidental schema drift when the Files JPA module is moved or optimized. */
class FilesJpaEntityContractTest {

    @Test
    void unchangedCanonicalTablesAndEmbeddedIdsBelongToFiles() throws Exception {
        assertThat(FileObjectJpaEntity.class.getAnnotation(Entity.class)).isNotNull();
        assertThat(FileObjectJpaEntity.class.getAnnotation(Table.class).name()).isEqualTo("weave_files_objects");
        assertThat(FileObjectJpaEntity.class.getDeclaredField("id").getAnnotation(EmbeddedId.class)).isNotNull();
        assertThat(CanonicalFileId.class.getAnnotation(Embeddable.class)).isNotNull();

        assertThat(FileLockJpaEntity.class.getAnnotation(Entity.class)).isNotNull();
        assertThat(FileLockJpaEntity.class.getAnnotation(Table.class).name()).isEqualTo("weave_file_locks");
        assertThat(FileLockJpaEntity.class.getDeclaredField("id").getAnnotation(EmbeddedId.class)).isNotNull();
        assertThat(FileLockId.class.getAnnotation(Embeddable.class)).isNotNull();

        assertThat(FilesUserResourceJpaEntity.class.getAnnotation(Entity.class)).isNotNull();
        assertThat(FilesUserResourceJpaEntity.class.getAnnotation(Table.class).name())
                .isEqualTo("weave_files_user_resources");
        assertThat(FilesUserResourceJpaEntity.class.getDeclaredField("id")
                .getAnnotation(EmbeddedId.class)).isNotNull();
        assertThat(FilesUserResourceId.class.getAnnotation(Embeddable.class)).isNotNull();
        assertThat(FilesUserResourceJpaEntity.class.getDeclaredField("scope")
                .getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
    }

    @Test
    void canonicalFilesRepositoriesRemainSpringDataInterfaces() {
        assertThat(FileObjectJpaRepository.class.getInterfaces()).contains(JpaRepository.class);
        assertThat(FileLockJpaRepository.class.getInterfaces()).contains(JpaRepository.class);
        assertThat(FilesUserResourceJpaRepository.class.getInterfaces()).contains(JpaRepository.class);
    }
}
