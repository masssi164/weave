package com.massimotter.weave.backend.files.autoconfigure;

import com.massimotter.weave.backend.files.api.FilesController;
import com.massimotter.weave.backend.files.api.FilesUserItemsController;
import com.massimotter.weave.backend.files.api.FilesWebDavController;
import com.massimotter.weave.backend.files.application.FilesFacadeService;
import com.massimotter.weave.backend.files.application.FilesProviderResolver;
import com.massimotter.weave.backend.files.application.FilesUserApiService;
import com.massimotter.weave.backend.files.provider.FilesystemBlobStore;
import com.massimotter.weave.backend.files.provider.NextcloudFilesAdapter;
import com.massimotter.weave.backend.files.provider.WeaveNativeFilesAdapter;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses the published imports resources, without App or manually imported runtime configurations. */
@Tag("context")
class FilesRuntimeDiscoveryTest {
    @TempDir
    Path temporary;

    @Test
    void discoversActualFilesBeansAndMappingsWithoutAppOrOtherDomains() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false",
                        "weave.compatibility.public-dav.enabled=true",
                        "weave.files.provider-accounts.secret-root=" + temporary.resolve("unmounted-secrets"),
                        "weave.files.native.filesystem-root=" + temporary.resolve("blobs"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FilesRuntimeAutoConfiguration.class)
                            .hasSingleBean(FilesFacadeService.class).hasSingleBean(FilesUserApiService.class)
                            .hasSingleBean(FilesProviderResolver.class).hasSingleBean(FilesystemBlobStore.class)
                            .hasSingleBean(WeaveNativeFilesAdapter.class).hasSingleBean(NextcloudFilesAdapter.class)
                            .hasSingleBean(FilesController.class).hasSingleBean(FilesUserItemsController.class)
                            .hasSingleBean(FilesWebDavController.class);
                    assertThat(context.getBean(WorkspaceCapabilityService.class).installedDefinitions())
                            .extracting(value -> value.domainId()).containsExactly("files");
                    var paths = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class).getHandlerMethods().keySet().stream()
                            .flatMap(mapping -> mapping.getPatternValues().stream()).toList();
                    assertThat(paths).contains("/api/files/readiness", "/api/files/items", "/dav/files", "/dav/files/**");
                    assertThat(paths).noneMatch(path -> path.startsWith("/api/calendar") || path.startsWith("/_matrix"));
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.WeaveBackendApplication", context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.calendar.domain.CalendarDomain", context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.chat.provider.matrix.MatrixProtocolCoreService", context.getClassLoader())).isFalse();
                });
    }

    @Test
    void nonWebSchemaAndOperatorDiscoveryDoesNotConstructFilesProvidersOrHandlers() {
        Path untouched = temporary.resolve("must-not-be-created");
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false",
                        "weave.files.native.filesystem-root=" + untouched)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(FilesRuntimeAutoConfiguration.class)
                            .doesNotHaveBean(FilesController.class).doesNotHaveBean(FilesFacadeService.class)
                            .doesNotHaveBean(FilesystemBlobStore.class).doesNotHaveBean(NextcloudFilesAdapter.class);
                    assertThat(Files.exists(untouched)).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
