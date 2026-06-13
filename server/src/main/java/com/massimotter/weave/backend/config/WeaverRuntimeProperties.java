package com.massimotter.weave.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.weaver.runtime")
public record WeaverRuntimeProperties(
        boolean enabled,
        String baselineProfile,
        String image,
        String workspaceRootTemplate,
        String isolatedAgentDirectory,
        String dockerNetworkMode,
        List<String> enabledGroups,
        List<String> allowedCapabilities,
        List<String> pluginAllowlist,
        List<String> toolAllowlist,
        boolean execEnabled,
        boolean elevatedEnabled,
        boolean auditRequired,
        boolean forkRequired) {

    public WeaverRuntimeProperties {
        baselineProfile = hasText(baselineProfile) ? baselineProfile : "weaver-governed-baseline";
        image = hasText(image) ? image : "ghcr.io/masssi164/weaver-openclaw:policy-generated";
        workspaceRootTemplate = hasText(workspaceRootTemplate) ? workspaceRootTemplate : "/var/lib/weave/weaver/{userId}";
        isolatedAgentDirectory = hasText(isolatedAgentDirectory) ? isolatedAgentDirectory : ".weaver/agents";
        dockerNetworkMode = hasText(dockerNetworkMode) ? dockerNetworkMode : "none";
        enabledGroups = normalize(enabledGroups, List.of("weaver-group", "weave-weaver-runtime"));
        allowedCapabilities = normalize(allowedCapabilities, List.of("weaver.files_read", "weaver.exec_disabled"));
        pluginAllowlist = normalize(pluginAllowlist, List.of("weave-files-readonly"));
        toolAllowlist = normalize(toolAllowlist, List.of("files.read"));
        // Even when an admin enables the runtime, audit remains required by default.
        auditRequired = true;
    }

    private static List<String> normalize(List<String> values, List<String> defaults) {
        if (values == null || values.isEmpty()) {
            return defaults;
        }
        List<String> normalized = values.stream()
                .filter(WeaverRuntimeProperties::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        return normalized.isEmpty() ? defaults : normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
