package com.massimotter.weave.backend.files.autoconfigure;

import com.massimotter.weave.backend.files.api.FilesController;
import com.massimotter.weave.backend.files.api.FilesDavSetupController;
import com.massimotter.weave.backend.files.api.FilesUserItemsController;
import com.massimotter.weave.backend.files.api.FilesWebDavController;
import com.massimotter.weave.backend.files.application.FilesFacadeService;
import com.massimotter.weave.backend.files.application.FilesProviderResolver;
import com.massimotter.weave.backend.files.application.FilesUserApiService;
import com.massimotter.weave.backend.files.capability.FilesWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.files.config.FilesProviderAccountsProperties;
import com.massimotter.weave.backend.files.config.FilesProviderHealthConfiguration;
import com.massimotter.weave.backend.files.config.FilesProviderRegistryConfiguration;
import com.massimotter.weave.backend.files.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.files.config.FilesWebDavSecurityConfiguration;
import com.massimotter.weave.backend.files.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.files.config.ProviderBindingBootstrapProperties;
import com.massimotter.weave.backend.files.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.files.config.WeaveS3FilesProperties;
import com.massimotter.weave.backend.files.provider.BootstrapNextcloudFilesAccountResolver;
import com.massimotter.weave.backend.files.provider.FilesystemBlobStore;
import com.massimotter.weave.backend.files.provider.NextcloudFilesAdapter;
import com.massimotter.weave.backend.files.provider.OpenDalS3ObjectStorageAdapter;
import com.massimotter.weave.backend.files.provider.WeaveNativeFilesAdapter;
import com.massimotter.weave.backend.files.provider.WeaveS3FilesAdapter;
import com.massimotter.weave.backend.files.spaces.FilesDefaultSpacePreparationContribution;
import com.massimotter.weave.backend.files.spaces.FilesSpaceRelationshipContribution;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.FilesProviderBindingBootstrap;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.files.autoconfigure.FilesPersistenceAutoConfiguration;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Installs the actual Files serving graph without App scanning or live provider probes. */
@AutoConfiguration(after = FilesPersistenceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({
        WeaveNativeFilesProperties.class,
        NextcloudFilesProperties.class,
        FilesRuntimeProperties.class,
        FilesProviderAccountsProperties.class,
        WeaveS3FilesProperties.class,
        ProviderBindingBootstrapProperties.class
})
@Import({
        FilesUserItemsController.class,
        FilesController.class,
        FilesWebDavController.class,
        FilesDavSetupController.class,
        WeaveS3FilesAdapter.class,
        OpenDalS3ObjectStorageAdapter.class,
        FilesUserApiService.class,
        FilesProviderResolver.class,
        com.massimotter.weave.backend.files.application.FilesProviderBindingStatus.class,
        NextcloudFilesAdapter.class,
        BootstrapNextcloudFilesAccountResolver.class,
        FilesystemBlobStore.class,
        WeaveNativeFilesAdapter.class,
        FilesFacadeService.class,
        FilesWebDavSecurityConfiguration.class,
        FilesProviderRegistryConfiguration.class,
        FilesProviderHealthConfiguration.class,
        FilesWorkspaceCapabilityConfiguration.class,
        FilesDefaultSpacePreparationContribution.class,
        FilesSpaceRelationshipContribution.class
})
public class FilesRuntimeAutoConfiguration {
    @Bean
    FilesLockService filesLockService(FilesAuthorityRepository repository) {
        return new FilesLockService(repository, Clock.systemUTC());
    }

    @Bean
    FilesMutationIntentService filesMutationIntentService(
            OperationIntentService intents, ProviderBindingRepository bindings) {
        return new FilesMutationIntentService(intents, bindings);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.provider-bindings.bootstrap.files.enabled", havingValue = "true")
    FilesProviderBindingBootstrap filesProviderBindingBootstrap(
            ProviderBindingRepository bindings, ProviderBindingBootstrapProperties properties) {
        return new FilesProviderBindingBootstrap(bindings, properties, Clock.systemUTC());
    }
}
