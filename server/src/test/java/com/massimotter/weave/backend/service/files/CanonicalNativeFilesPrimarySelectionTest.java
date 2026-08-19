package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CanonicalNativeFilesPrimarySelectionTest {

    @Test
    void directCanonicalCompositionIsTheOnlyNativeFilesPort() {
        FilesAuthorityRepository authority = mock(FilesAuthorityRepository.class);
        BlobStorePort blobs = mock(BlobStorePort.class);
        WeaveNativeFilesProperties properties = new WeaveNativeFilesProperties(
                Path.of("build", "canonical-native-files-primary-test"),
                1024 * 1024,
                100);

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
            context.register(CanonicalNativeFilesConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(FilesProviderPort.class))
                    .containsOnlyKeys("canonicalNativeFilesProvider");
            assertThat(context.getBean(FilesProviderPort.class))
                    .isInstanceOf(CanonicalNativeFilesComposition.class);
            assertThat(context.containsBean("weaveNativeFilesAdapter")).isFalse();
            assertThat(context.getBeanFactory()
                    .getBeanDefinition("canonicalNativeFilesProvider")
                    .isPrimary())
                    .isTrue();
        }
    }
}
