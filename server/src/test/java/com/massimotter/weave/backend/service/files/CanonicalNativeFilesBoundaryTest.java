package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.CanonicalFilesCommands;
import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import com.massimotter.weave.backend.files.application.CanonicalFilesTreeCommands;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Guards the completed transition from provider-shaped native behavior to canonical use cases. */
class CanonicalNativeFilesBoundaryTest {

    @Test
    void directCompositionOwnsEveryCanonicalFilesUseCase() {
        assertThat(Arrays.stream(CanonicalNativeFilesComposition.class.getDeclaredFields())
                .map(Field::getType))
                .contains(
                        CanonicalFilesQueries.class,
                        CanonicalFilesCommands.class,
                        CanonicalFilesTreeCommands.class)
                .noneMatch(type -> type.getSimpleName().equals("WeaveNativeFilesAdapter"));
    }

    @Test
    void transitionAdapterClassNoLongerExistsAtRuntime() {
        assertThatThrownBy(() -> Class.forName(
                "com.massimotter.weave.backend.service.files.WeaveNativeFilesAdapter"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
