package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Guards the explicit transition from provider-shaped query logic to canonical Files use cases. */
class WeaveNativeFilesQueryDelegationTest {

    @Test
    void nativeCompositionOwnsCanonicalQueryUseCaseAndDropsDuplicateQueryHelpers() {
        assertThat(Arrays.stream(WeaveNativeFilesAdapter.class.getDeclaredFields())
                .map(Field::getType))
                .contains(CanonicalFilesQueries.class);

        assertThat(Arrays.stream(WeaveNativeFilesAdapter.class.getDeclaredMethods())
                .map(Method::getName))
                .doesNotContain("listingVersion", "rootObject", "sha256");
    }
}
