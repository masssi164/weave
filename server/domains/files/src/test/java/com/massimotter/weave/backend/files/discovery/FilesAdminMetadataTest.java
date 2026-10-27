package com.massimotter.weave.backend.files.discovery;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FilesAdminMetadataTest {
    @Test
    void installedFilesAlwaysDeclaresUnverifiedApplyRefusalAndBindingFence() {
        var policy = FilesDiscoveryMetadata.filesCategory().selectionPolicy();
        assertThat(policy.directApplyAllowed()).isFalse();
        assertThat(policy.bindingScoped()).isTrue();
        assertThat(policy.previewReadiness()).isEqualTo("blocked-unverified");
        assertThat(policy.deniedCode()).isEqualTo("files-provider-activation-unverified");
    }

    @Test
    void filesSuiteFragmentDoesNotAdvertiseOfficeCapabilitiesOrObjects() {
        var suite = FilesAdminMetadata.suite().definition();
        assertThat(suite.providerCategoryKeys()).containsExactly("files");
        assertThat(suite.readCapabilities()).containsExactly("files.read");
        assertThat(suite.canonicalObjectKinds()).contains("file", "folder").doesNotContain("document", "editor_session");
    }
}
