package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.databind.JsonNode;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class RuntimeProfileJsonDecoder {
    private RuntimeProfileJsonDecoder() {
    }

    static RuntimeProfile decode(JsonNode root) {
        fields(root,
                Set.of("profileVersion", "profileId", "organizationRef", "personRef", "memberBinding",
                        "cellRef", "workloadIdentity", "issuedAt", "expiresAt", "entitlementRevision",
                        "workspaceRevision", "workspaceManifestRef", "runtimeStateStoreRef",
                        "zeroDurableCellBytes", "modelPolicy", "matrix", "mcp", "approvals", "sandbox",
                        "automation"),
                Set.of());
        return new RuntimeProfile(
                text(root, "profileVersion"),
                text(root, "profileId"),
                text(root, "organizationRef"),
                text(root, "personRef"),
                memberBinding(object(root, "memberBinding")),
                text(root, "cellRef"),
                workload(object(root, "workloadIdentity")),
                instant(root, "issuedAt"),
                instant(root, "expiresAt"),
                text(root, "entitlementRevision"),
                text(root, "workspaceRevision"),
                text(root, "workspaceManifestRef"),
                text(root, "runtimeStateStoreRef"),
                bool(root, "zeroDurableCellBytes"),
                modelPolicy(object(root, "modelPolicy")),
                matrixPolicy(object(root, "matrix")),
                mcpPolicy(object(root, "mcp")),
                approvalPolicy(object(root, "approvals")),
                sandboxPolicy(object(root, "sandbox")),
                automationPolicy(object(root, "automation")));
    }

    private static RuntimeMemberBinding memberBinding(JsonNode node) {
        fields(node, Set.of("issuer", "subject"), Set.of());
        return new RuntimeMemberBinding(text(node, "issuer"), text(node, "subject"));
    }

    private static RuntimeProfile.WorkloadIdentity workload(JsonNode node) {
        fields(node, Set.of("issuer", "subject", "clientId", "role", "authenticationMethod"), Set.of());
        return new RuntimeProfile.WorkloadIdentity(
                text(node, "issuer"), text(node, "subject"), text(node, "clientId"), text(node, "role"),
                enumeration(RuntimeProfile.AuthenticationMethod.values(), text(node, "authenticationMethod"),
                        RuntimeProfile.AuthenticationMethod::wireValue, "authenticationMethod"));
    }

    private static RuntimeProfile.ModelPolicy modelPolicy(JsonNode node) {
        fields(node, Set.of("allowedProviders", "allowedModels", "fallback"),
                Set.of("maximumContextTokens", "dataRegion"));
        Integer maximum = node.has("maximumContextTokens") ? integer(node, "maximumContextTokens") : null;
        return new RuntimeProfile.ModelPolicy(
                strings(node, "allowedProviders"), strings(node, "allowedModels"), strings(node, "fallback"),
                maximum, optionalText(node, "dataRegion"));
    }

    private static RuntimeProfile.MatrixPolicy matrixPolicy(JsonNode node) {
        fields(node, Set.of("accountRef", "homeserverRef", "allowedRooms", "encryptionRequired"),
                Set.of("credentialRef", "autoJoin"));
        RuntimeProfile.AutoJoin autoJoin = node.has("autoJoin")
                ? enumeration(RuntimeProfile.AutoJoin.values(), text(node, "autoJoin"),
                        RuntimeProfile.AutoJoin::wireValue, "autoJoin")
                : null;
        return new RuntimeProfile.MatrixPolicy(
                text(node, "accountRef"), text(node, "homeserverRef"), optionalText(node, "credentialRef"),
                strings(node, "allowedRooms"), autoJoin, bool(node, "encryptionRequired"));
    }

    private static RuntimeProfile.McpPolicy mcpPolicy(JsonNode node) {
        fields(node, Set.of("servers", "visibleToolClasses"), Set.of());
        JsonNode serverNodes = array(node, "servers");
        List<RuntimeProfile.McpServer> servers = new ArrayList<>();
        serverNodes.forEach(server -> servers.add(mcpServer(server)));
        return new RuntimeProfile.McpPolicy(servers, strings(node, "visibleToolClasses"));
    }

    private static RuntimeProfile.McpServer mcpServer(JsonNode node) {
        fields(node,
                Set.of("serverRef", "endpoint", "requestedResource", "extensionId", "grantType",
                        "requiredScopes", "credentialRef"),
                Set.of("allowedToolClasses"));
        return new RuntimeProfile.McpServer(
                text(node, "serverRef"), text(node, "endpoint"), text(node, "requestedResource"),
                text(node, "extensionId"), text(node, "grantType"), strings(node, "requiredScopes"),
                text(node, "credentialRef"),
                node.has("allowedToolClasses") ? strings(node, "allowedToolClasses") : null);
    }

    private static RuntimeProfile.ApprovalPolicy approvalPolicy(JsonNode node) {
        fields(node, Set.of("owner", "pluginRouting", "execMode", "persistentTrustPolicy"), Set.of());
        return new RuntimeProfile.ApprovalPolicy(
                text(node, "owner"), pluginRouting(object(node, "pluginRouting")),
                enumeration(RuntimeProfile.ExecMode.values(), text(node, "execMode"),
                        RuntimeProfile.ExecMode::wireValue, "execMode"),
                enumeration(RuntimeProfile.PersistentTrustPolicy.values(), text(node, "persistentTrustPolicy"),
                        RuntimeProfile.PersistentTrustPolicy::wireValue, "persistentTrustPolicy"));
    }

    private static RuntimeProfile.PluginRouting pluginRouting(JsonNode node) {
        fields(node, Set.of("enabled", "mode"), Set.of("targetRefs"));
        return new RuntimeProfile.PluginRouting(
                bool(node, "enabled"),
                enumeration(RuntimeProfile.PluginRoutingMode.values(), text(node, "mode"),
                        RuntimeProfile.PluginRoutingMode::wireValue, "plugin routing mode"),
                node.has("targetRefs") ? strings(node, "targetRefs") : null);
    }

    private static RuntimeProfile.SandboxPolicy sandboxPolicy(JsonNode node) {
        fields(node, Set.of("mode", "networkPolicy", "filesystemPolicy"),
                Set.of("allowedNetworkTargets", "approvedMountRefs"));
        return new RuntimeProfile.SandboxPolicy(
                enumeration(RuntimeProfile.SandboxMode.values(), text(node, "mode"),
                        RuntimeProfile.SandboxMode::wireValue, "sandbox mode"),
                enumeration(RuntimeProfile.NetworkPolicy.values(), text(node, "networkPolicy"),
                        RuntimeProfile.NetworkPolicy::wireValue, "networkPolicy"),
                node.has("allowedNetworkTargets") ? strings(node, "allowedNetworkTargets") : null,
                enumeration(RuntimeProfile.FilesystemPolicy.values(), text(node, "filesystemPolicy"),
                        RuntimeProfile.FilesystemPolicy::wireValue, "filesystemPolicy"),
                node.has("approvedMountRefs") ? strings(node, "approvedMountRefs") : null);
    }

    private static RuntimeProfile.AutomationPolicy automationPolicy(JsonNode node) {
        fields(node, Set.of("heartbeatEnabled", "schedulePolicy"), Set.of());
        return new RuntimeProfile.AutomationPolicy(
                bool(node, "heartbeatEnabled"),
                enumeration(RuntimeProfile.SchedulePolicy.values(), text(node, "schedulePolicy"),
                        RuntimeProfile.SchedulePolicy::wireValue, "schedulePolicy"));
    }

    private static void fields(JsonNode node, Set<String> required, Set<String> optional) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("RuntimeProfile field must be an object");
        }
        Set<String> actual = new HashSet<>();
        Iterator<String> names = node.propertyNames().iterator();
        names.forEachRemaining(actual::add);
        Set<String> allowed = new HashSet<>(required);
        allowed.addAll(optional);
        if (!actual.containsAll(required) || !allowed.containsAll(actual)) {
            throw new IllegalArgumentException("RuntimeProfile fields do not match the v2 contract");
        }
    }

    private static JsonNode object(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value;
    }

    private static JsonNode array(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static List<String> strings(JsonNode parent, String field) {
        List<String> values = new ArrayList<>();
        array(parent, field).forEach(value -> {
            if (!value.isTextual()) {
                throw new IllegalArgumentException(field + " must contain strings");
            }
            values.add(value.textValue());
        });
        return values;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode parent, String field) {
        return parent.has(field) ? text(parent, field) : null;
    }

    private static boolean bool(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.booleanValue();
    }

    private static int integer(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.intValue();
    }

    private static Instant instant(JsonNode parent, String field) {
        return Instant.parse(text(parent, field));
    }

    private static <T> T enumeration(
            T[] values, String wireValue, Function<T, String> projection, String field) {
        for (T value : values) {
            if (projection.apply(value).equals(wireValue)) {
                return value;
            }
        }
        throw new IllegalArgumentException(field + " has an unsupported value");
    }
}
