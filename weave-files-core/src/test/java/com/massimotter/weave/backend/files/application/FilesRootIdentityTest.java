package com.massimotter.weave.backend.files.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilesRootIdentityTest {

    @Test
    void identityIsDeterministicScopedAndIndependentOfProviderOrPath() {
        FilesScope firstScope = new FilesScope("org-a", "space-a");

        assertThat(FilesRootIdentity.forScope(firstScope))
                .isEqualTo(FilesRootIdentity.forScope(new FilesScope("org-a", "space-a")));
        assertThat(FilesRootIdentity.forScope(firstScope).value())
                .startsWith("files-root:")
                .hasSize("files-root:".length() + 64);
        assertThat(FilesRootIdentity.forScope(new FilesScope("org-b", "space-a")))
                .isNotEqualTo(FilesRootIdentity.forScope(firstScope));
        assertThat(FilesRootIdentity.forScope(new FilesScope("org-a", "space-b")))
                .isNotEqualTo(FilesRootIdentity.forScope(firstScope));
    }
}
