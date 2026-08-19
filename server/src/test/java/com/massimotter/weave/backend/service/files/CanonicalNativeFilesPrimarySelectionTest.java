package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Proves the native runtime exposes one direct canonical Files provider bean. */
class CanonicalNativeFilesPrimarySelectionTest {

    @Test
    void directCanonicalProviderIsTheOnlyFilesPortBean() {
        FilesAuthorityRepository authority = mock(FilesAuthorityRepository.class);
        BlobStorePort blobs = mock(BlobStorePort.class);
        WeaveNativeFilesProperties properties = mock(WeaveNativeFilesProperties.class);
        when(properties.reconciliationLimit()).thenReturn(100);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("weave.files.provider=weave-native").applyTo(context);
            context.registerBean(
                    "filesAuthorityRepository",
                    FilesAuthorityRepository.class,
                    () -> authority);
            context.registerBean(
                    "blobStorePort",
                    BlobStorePort.class,
                    () -> blobs);
            context.registerBean(
                    "weaveNativeFilesProperties",
                    WeaveNativeFilesProperties.class,
                    () -> properties);
            context.register(WeaveNativeFilesAdapter.class);
            context.refresh();

            assertThat(context.getBeansOfType(FilesProviderPort.class))
                    .containsOnlyKeys("weaveNativeFilesAdapter");
            assertThat(context.getBean(FilesProviderPort.class))
                    .isInstanceOf(WeaveNativeFilesAdapter.class);
            assertThat(context.getBeanFactory()
                    .getBeanDefinition("weaveNativeFilesAdapter")
                    .isPrimary())
                    .isTrue();
        }
    }
}
