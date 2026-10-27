package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilesPersistenceOwnershipTest {
    @Test
    void adaptersAndTheirPackagePrivateJpaTypesHaveOnePhysicalOwner() throws IOException {
        var owner = FileObjectJpaEntity.class.getProtectionDomain().getCodeSource().getLocation();
        for (Class<?> type : List.of(JpaFilesAuthorityRepository.class,
                JpaFilesUserResourceRepository.class, FilesUserResourceJpaEntity.class,
                FileLockJpaEntity.class, FileObjectJpaRepository.class,
                FileLockJpaRepository.class, FilesUserResourceJpaRepository.class)) {
            assertThat(type.getProtectionDomain().getCodeSource().getLocation())
                    .as("%s must be compiled beside its JPA types, not in the executable Server", type.getName())
                    .isEqualTo(owner);
            String resource = type.getName().replace('.', '/') + ".class";
            assertThat(Collections.list(type.getClassLoader().getResources(resource)))
                    .as("%s must not survive as a second production copy", type.getName()).hasSize(1);
        }
    }

    @Test
    void domainPortsRemainThePublicPersistenceBoundary() {
        assertThat(FilesAuthorityRepository.class.isAssignableFrom(JpaFilesAuthorityRepository.class)).isTrue();
        assertThat(FilesUserResourceRepository.class.isAssignableFrom(JpaFilesUserResourceRepository.class)).isTrue();
    }
}
