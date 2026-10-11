package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.CanonicalFilesCommands;
import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import com.massimotter.weave.backend.files.application.CanonicalFilesTreeCommands;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Guards the completed transition from provider-shaped behavior to canonical Files use cases. */
class WeaveNativeFilesQueryDelegationTest {

    @Test
    void nativeCompositionOwnsAllCanonicalUseCasesAndNoLegacyAlgorithms() {
        assertThat(Arrays.stream(WeaveNativeFilesAdapter.class.getDeclaredFields())
                .map(Field::getType))
                .contains(
                        CanonicalFilesQueries.class,
                        CanonicalFilesCommands.class,
                        CanonicalFilesTreeCommands.class);

        assertThat(Arrays.stream(WeaveNativeFilesAdapter.class.getDeclaredMethods())
                .map(Method::getName))
                .doesNotContain(
                        "listingVersion",
                        "rootObject",
                        "sha256",
                        "ensureParent",
                        "tree",
                        "substitute",
                        "tombstones",
                        "cleanupBlobs",
                        "verify",
                        "canonicalId",
                        "blobReference");
    }

    @Test
    void transitionalPrimarySelectionConfigurationNoLongerExists() {
        assertThatThrownBy(() -> Class.forName(
                "com.massimotter.weave.backend.service.files.CanonicalNativeFilesConfiguration"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
