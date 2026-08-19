package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CanonicalNativeFilesPrimarySelectionTest {

    @Test
    void canonicalCompositionIsTheSinglePrimaryFilesPort() {
        FilesAuthorityRepository authority = mock(FilesAuthorityRepository.class);
        BlobStorePort blobs = mock(BlobStorePort.class);
        WeaveNativeFilesAdapter transitional = new WeaveNativeFilesAdapter(
                authority,
                blobs,
                Clock.systemUTC(),
                100);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("weave.files.provider=weave-native").applyTo(context);
            context.registerBean(
                    "weaveNativeFilesAdapter",
                    WeaveNativeFilesAdapter.class,
                    () -> transitional,
                    definition -> definition.setPrimary(true));
            context.registerBean(
                    "filesAuthorityRepository",
                    FilesAuthorityRepository.class,
                    () -> authority);
            context.registerBean(
                    "blobStorePort",
                    BlobStorePort.class,
                    () -> blobs);
            context.register(CanonicalNativeFilesConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(FilesProviderPort.class)).hasSize(2);
            assertThat(context.getBean(FilesProviderPort.class))
                    .isInstanceOf(CanonicalNativeFilesComposition.class);
            assertThat(context.getBeanFactory()
                    .getBeanDefinition("weaveNativeFilesAdapter")
                    .isPrimary())
                    .isFalse();
            assertThat(context.getBeanFactory()
                    .getBeanDefinition("canonicalNativeFilesProvider")
                    .isPrimary())
                    .isTrue();
        }
    }
}
