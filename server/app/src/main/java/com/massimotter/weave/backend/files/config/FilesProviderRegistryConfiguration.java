package com.massimotter.weave.backend.files.config;

import com.massimotter.weave.backend.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.discovery.provider.RuntimeProviderStatus;
import com.massimotter.weave.backend.discovery.provider.StaticProviderPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FilesProviderRegistryConfiguration {

    @Bean
    ProviderPort filesProviderRegistrySeam(
            ObjectProvider<FilesProviderPort> filesProviderPort,
            FilesRuntimeProperties filesRuntimeProperties) {
        FilesProviderPort runtime = filesProviderPort.orderedStream()
                .filter(candidate -> filesRuntimeProperties.provider()
                        .equals(candidate.conformanceProfile().adapterKey()))
                .findFirst()
                .orElse(null);
        if (runtime == null) {
            return StaticProviderPort.pending(
                    ProviderModule.FILES,
                    "weave-native",
                    "No Files runtime adapter is bound; the canonical Files facade remains fail-closed.",
                    Set.of("list", "read", "write", "create-collection", "delete", "copy", "move"),
                    Set.of("direct-member-provider-api", "credential-exposure", "raw-provider-errors"),
                    List.of("weave-native", "nextcloud-files", "webdav", "sharepoint", "onedrive", "s3-compatible", "smb"),
                    Map.of("runtimeBindingObserved", false, "facade", "/api/files/items"));
        }
        return RuntimeProviderStatus.fromConformancePort(
                ProviderModule.FILES,
                runtime.conformanceProfile().adapterKey(),
                runtime.configured(),
                runtime.conformanceProfile(),
                "The selected Files adapter is bound behind the canonical Files port and the generated User Files API.",
                List.of("weave-native", "nextcloud-files", "webdav", "sharepoint", "onedrive", "s3-compatible", "smb"));
    }
}
