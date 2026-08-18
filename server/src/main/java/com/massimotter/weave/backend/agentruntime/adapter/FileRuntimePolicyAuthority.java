package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProvisioningPlan;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict read-only adapter for an operator-generated, secret-free runtime policy document. */
public final class FileRuntimePolicyAuthority implements RuntimePolicyAuthority {
    public static final String SCHEMA = "weave.runtime-policy/v1";
    private static final int MAX_POLICY_BYTES = 1_048_576;
    private static final Set<String> PLACEHOLDERS = Set.of(
            "{organizationRef}", "{personRef}", "{cellRef}", "{workloadClientId}");

    private final Path policyFile;
    private final ObjectMapper mapper;
    private final Duration maximumProfileTtl;

    public FileRuntimePolicyAuthority(Path policyFile, ObjectMapper objectMapper, Duration maximumProfileTtl) {
        if (policyFile == null || objectMapper == null || maximumProfileTtl == null
                || maximumProfileTtl.isZero() || maximumProfileTtl.isNegative()) {
            throw new IllegalArgumentException("runtime policy file, mapper, and maximum profile TTL are required");
        }
        this.policyFile = policyFile.toAbsolutePath().normalize();
        this.mapper = objectMapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        this.maximumProfileTtl = maximumProfileTtl;
    }

    @Override
    public RuntimeProvisioningPlan provisioningPlan(RuntimePersonDirectory.ResolvedRuntimePerson person) {
        Objects.requireNonNull(person, "person");
        PolicyDocument policy = read();
        TemplateContext context = new TemplateContext(
                person.organizationRef(), person.personRef(), null, null);
        return new RuntimeProvisioningPlan(
                policy.workspace().revision(),
                expand(policy.workspace().manifestRefTemplate(), context),
                expand(policy.workspace().runtimeStateStoreRefTemplate(), context),
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT);
    }

    @Override
    public RuntimeProfile runtimeProfile(
            RuntimeCell cell,
            String profileId,
            Instant issuedAt,
            Instant expiresAt) {
        Objects.requireNonNull(cell, "cell");
        PolicyDocument policy = read();
        if (issuedAt == null || expiresAt == null || !expiresAt.equals(issuedAt.plus(profileTtl(policy)))) {
            throw new RuntimePolicyException("RuntimeProfile issuance must use the exact configured lifetime");
        }
        TemplateContext context = new TemplateContext(
                cell.organizationRef(), cell.personRef(), cell.cellRef(), cell.workloadBinding().clientId());
        RuntimeProfile.MatrixPolicy matrix = new RuntimeProfile.MatrixPolicy(
                expand(policy.matrix().accountRefTemplate(), context),
                expand(policy.matrix().homeserverRefTemplate(), context),
                optionalExpand(policy.matrix().credentialRefTemplate(), context),
                policy.matrix().allowedRooms(),
                autoJoin(policy.matrix().autoJoin()),
                true);
        List<RuntimeProfile.McpServer> mcpServers = policy.mcp().servers().stream()
                .map(server -> new RuntimeProfile.McpServer(
                        server.serverRef(),
                        server.endpoint(),
                        server.requestedResource(),
                        "io.modelcontextprotocol/oauth-client-credentials",
                        "client_credentials",
                        server.requiredScopes(),
                        expand(server.credentialRefTemplate(), context),
                        server.allowedToolClasses()))
                .toList();
        return new RuntimeProfile(
                RuntimeProfile.VERSION,
                profileId,
                cell.organizationRef(),
                cell.personRef(),
                cell.memberBinding(),
                cell.cellRef(),
                new RuntimeProfile.WorkloadIdentity(
                        cell.workloadBinding().issuer(),
                        cell.workloadBinding().subject(),
                        cell.workloadBinding().clientId(),
                        "weaver-runtime",
                        authenticationMethod(cell.workloadBinding().authenticationMethod())),
                issuedAt,
                expiresAt,
                cell.entitlementRevision(),
                cell.workspaceRevision(),
                cell.workspaceManifestRef(),
                cell.runtimeStateStoreRef(),
                true,
                new RuntimeProfile.ModelPolicy(
                        policy.modelPolicy().allowedProviders(),
                        policy.modelPolicy().allowedModels(),
                        policy.modelPolicy().fallback(),
                        policy.modelPolicy().maximumContextTokens(),
                        policy.modelPolicy().dataRegion()),
                matrix,
                new RuntimeProfile.McpPolicy(mcpServers, policy.mcp().visibleToolClasses()),
                new RuntimeProfile.ApprovalPolicy(
                        "openclaw",
                        new RuntimeProfile.PluginRouting(
                                policy.approvals().pluginRouting().enabled(),
                                pluginRoutingMode(policy.approvals().pluginRouting().mode()),
                                policy.approvals().pluginRouting().targetRefs()),
                        execMode(policy.approvals().execMode()),
                        trustPolicy(policy.approvals().persistentTrustPolicy())),
                new RuntimeProfile.SandboxPolicy(
                        sandboxMode(policy.sandbox().mode()),
                        networkPolicy(policy.sandbox().networkPolicy()),
                        policy.sandbox().allowedNetworkTargets(),
                        filesystemPolicy(policy.sandbox().filesystemPolicy()),
                        policy.sandbox().approvedMountRefs()),
                new RuntimeProfile.AutomationPolicy(
                        policy.automation().heartbeatEnabled(),
                        schedulePolicy(policy.automation().schedulePolicy())));
    }

    @Override
    public Duration profileTtl() {
        return profileTtl(read());
    }

    private PolicyDocument read() {
        if (Files.isSymbolicLink(policyFile)) {
            throw new RuntimePolicyException("The runtime policy file must not be a symbolic link");
        }
        try {
            if (!Files.isRegularFile(policyFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new RuntimePolicyException("The configured runtime policy file is unavailable");
            }
            long size = Files.size(policyFile);
            if (size < 1 || size > MAX_POLICY_BYTES) {
                throw new RuntimePolicyException("The runtime policy file violates its size bound");
            }
            byte[] bytes = Files.readAllBytes(policyFile);
            try {
                if (bytes.length < 1 || bytes.length > MAX_POLICY_BYTES) {
                    throw new RuntimePolicyException("The runtime policy file violates its size bound");
                }
                PolicyDocument policy = mapper.readValue(bytes, PolicyDocument.class);
                validate(policy);
                return policy;
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (RuntimePolicyException failure) {
            throw failure;
        } catch (JacksonException failure) {
            throw new RuntimePolicyException("The runtime policy file is invalid or unreadable", failure);
        } catch (IOException failure) {
            throw new RuntimePolicyException("The runtime policy file is invalid or unreadable", failure);
        }
    }

    private void validate(PolicyDocument policy) {
        if (policy == null || !SCHEMA.equals(policy.schemaVersion()) || policy.workspace() == null
                || policy.modelPolicy() == null || policy.matrix() == null || policy.mcp() == null
                || policy.approvals() == null || policy.sandbox() == null || policy.automation() == null) {
            throw new RuntimePolicyException("The runtime policy document violates its schema contract");
        }
        profileTtl(policy);
        requireText(policy.workspace().revision(), "workspace revision");
        validateTemplate(policy.workspace().manifestRefTemplate(), "workspace manifest reference template");
        validateTemplate(policy.workspace().runtimeStateStoreRefTemplate(), "runtime state reference template");
        unique(policy.modelPolicy().allowedProviders(), "allowed model providers", true);
        unique(policy.modelPolicy().allowedModels(), "allowed models", true);
        unique(policy.modelPolicy().fallback(), "model fallback", false);
        if (policy.modelPolicy().maximumContextTokens() != null
                && policy.modelPolicy().maximumContextTokens() < 1) {
            throw new RuntimePolicyException("maximum context tokens must be positive");
        }
        validateTemplate(policy.matrix().accountRefTemplate(), "Matrix account reference template");
        validateTemplate(policy.matrix().homeserverRefTemplate(), "Matrix homeserver reference template");
        if (policy.matrix().credentialRefTemplate() != null) {
            validateTemplate(policy.matrix().credentialRefTemplate(), "Matrix credential reference template");
        }
        unique(policy.matrix().allowedRooms(), "Matrix allowed rooms", false);
        autoJoin(policy.matrix().autoJoin());
        if (policy.mcp().servers() == null || policy.mcp().servers().isEmpty()) {
            throw new RuntimePolicyException("At least one workload-only MCP server policy is required");
        }
        Set<String> serverRefs = new LinkedHashSet<>();
        for (McpServerDocument server : policy.mcp().servers()) {
            if (server == null || !serverRefs.add(server.serverRef())) {
                throw new RuntimePolicyException("MCP server references must be present and unique");
            }
            requireText(server.serverRef(), "MCP server reference");
            validateHttps(server.endpoint(), "MCP endpoint");
            validateHttps(server.requestedResource(), "MCP requested resource");
            unique(server.requiredScopes(), "MCP required scopes", true);
            validateTemplate(server.credentialRefTemplate(), "MCP credential reference template");
            unique(server.allowedToolClasses(), "MCP allowed tool classes", true);
        }
        unique(policy.mcp().visibleToolClasses(), "visible MCP tool classes", true);
        if (policy.approvals().pluginRouting() == null) {
            throw new RuntimePolicyException("OpenClaw plugin routing policy is required");
        }
        pluginRoutingMode(policy.approvals().pluginRouting().mode());
        execMode(policy.approvals().execMode());
        trustPolicy(policy.approvals().persistentTrustPolicy());
        sandboxMode(policy.sandbox().mode());
        networkPolicy(policy.sandbox().networkPolicy());
        filesystemPolicy(policy.sandbox().filesystemPolicy());
        unique(policy.sandbox().allowedNetworkTargets(), "sandbox network targets", false);
        unique(policy.sandbox().approvedMountRefs(), "sandbox mount references", false);
        schedulePolicy(policy.automation().schedulePolicy());
    }

    private Duration profileTtl(PolicyDocument policy) {
        if (policy.profileTtlSeconds() < 30) {
            throw new RuntimePolicyException("RuntimeProfile TTL must be at least 30 seconds");
        }
        Duration ttl = Duration.ofSeconds(policy.profileTtlSeconds());
        if (ttl.compareTo(maximumProfileTtl) > 0) {
            throw new RuntimePolicyException("RuntimeProfile TTL exceeds the signing trust-window contract");
        }
        return ttl;
    }

    private static String expand(String template, TemplateContext context) {
        validateTemplate(template, "runtime policy template");
        String expanded = template;
        expanded = replace(expanded, "{organizationRef}", context.organizationRef());
        expanded = replace(expanded, "{personRef}", context.personRef());
        expanded = replace(expanded, "{cellRef}", context.cellRef());
        expanded = replace(expanded, "{workloadClientId}", context.workloadClientId());
        if (expanded.contains("{") || expanded.contains("}")) {
            throw new RuntimePolicyException("The runtime policy template contains an unresolved placeholder");
        }
        return expanded;
    }

    private static String optionalExpand(String template, TemplateContext context) {
        return template == null ? null : expand(template, context);
    }

    private static String replace(String source, String placeholder, String value) {
        if (!source.contains(placeholder)) {
            return source;
        }
        if (value == null || value.isBlank()) {
            throw new RuntimePolicyException("The runtime policy template requires unavailable context");
        }
        return source.replace(placeholder, value);
    }

    private static void validateTemplate(String template, String field) {
        requireText(template, field);
        String remaining = template;
        for (String placeholder : PLACEHOLDERS) {
            remaining = remaining.replace(placeholder, "");
        }
        if (remaining.contains("{") || remaining.contains("}")) {
            throw new RuntimePolicyException(field + " contains an unsupported placeholder");
        }
    }

    private static void validateHttps(String value, String field) {
        java.net.URI uri;
        try {
            uri = java.net.URI.create(value);
        } catch (IllegalArgumentException invalid) {
            throw new RuntimePolicyException(field + " must be an HTTPS URI", invalid);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new RuntimePolicyException(field + " must be an HTTPS URI");
        }
    }

    private static List<String> unique(List<String> values, String field, boolean nonEmpty) {
        if (values == null || (nonEmpty && values.isEmpty())) {
            throw new RuntimePolicyException(field + " must not be empty");
        }
        List<String> copy = List.copyOf(values);
        copy.forEach(value -> requireText(value, field));
        if (new LinkedHashSet<>(copy).size() != copy.size()) {
            throw new RuntimePolicyException(field + " must contain unique values");
        }
        return copy;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new RuntimePolicyException(field + " is required");
        }
    }

    private static RuntimeProfile.AuthenticationMethod authenticationMethod(
            RuntimeWorkloadBinding.AuthenticationMethod method) {
        return switch (method) {
            case PRIVATE_KEY_JWT -> RuntimeProfile.AuthenticationMethod.PRIVATE_KEY_JWT;
            case CLIENT_SECRET_BASIC -> RuntimeProfile.AuthenticationMethod.CLIENT_SECRET_BASIC;
        };
    }

    private static RuntimeProfile.AutoJoin autoJoin(String value) {
        return enumValue(value, Map.of(
                "off", RuntimeProfile.AutoJoin.OFF,
                "allowlist", RuntimeProfile.AutoJoin.ALLOWLIST), "Matrix auto-join policy");
    }

    private static RuntimeProfile.PluginRoutingMode pluginRoutingMode(String value) {
        return enumValue(value, Map.of(
                "same-chat", RuntimeProfile.PluginRoutingMode.SAME_CHAT,
                "targets", RuntimeProfile.PluginRoutingMode.TARGETS,
                "local-only", RuntimeProfile.PluginRoutingMode.LOCAL_ONLY), "plugin routing mode");
    }

    private static RuntimeProfile.ExecMode execMode(String value) {
        return enumValue(value, Map.of(
                "deny", RuntimeProfile.ExecMode.DENY,
                "allowlist", RuntimeProfile.ExecMode.ALLOWLIST,
                "ask", RuntimeProfile.ExecMode.ASK,
                "auto", RuntimeProfile.ExecMode.AUTO,
                "full", RuntimeProfile.ExecMode.FULL), "exec mode");
    }

    private static RuntimeProfile.PersistentTrustPolicy trustPolicy(String value) {
        return enumValue(value, Map.of(
                "disabled", RuntimeProfile.PersistentTrustPolicy.DISABLED,
                "bounded", RuntimeProfile.PersistentTrustPolicy.BOUNDED), "persistent trust policy");
    }

    private static RuntimeProfile.SandboxMode sandboxMode(String value) {
        return enumValue(value, Map.of(
                "required", RuntimeProfile.SandboxMode.REQUIRED,
                "restricted", RuntimeProfile.SandboxMode.RESTRICTED,
                "trusted-admin-only", RuntimeProfile.SandboxMode.TRUSTED_ADMIN_ONLY), "sandbox mode");
    }

    private static RuntimeProfile.NetworkPolicy networkPolicy(String value) {
        return enumValue(value, Map.of(
                "deny", RuntimeProfile.NetworkPolicy.DENY,
                "allowlist", RuntimeProfile.NetworkPolicy.ALLOWLIST), "sandbox network policy");
    }

    private static RuntimeProfile.FilesystemPolicy filesystemPolicy(String value) {
        return enumValue(value, Map.of(
                "workspace-only", RuntimeProfile.FilesystemPolicy.WORKSPACE_ONLY,
                "workspace-plus-approved-mounts", RuntimeProfile.FilesystemPolicy.WORKSPACE_PLUS_APPROVED_MOUNTS),
                "sandbox filesystem policy");
    }

    private static RuntimeProfile.SchedulePolicy schedulePolicy(String value) {
        return enumValue(value, Map.of(
                "disabled", RuntimeProfile.SchedulePolicy.DISABLED,
                "user-owned", RuntimeProfile.SchedulePolicy.USER_OWNED,
                "organization-managed", RuntimeProfile.SchedulePolicy.ORGANIZATION_MANAGED), "schedule policy");
    }

    private static <T> T enumValue(String value, Map<String, T> accepted, String field) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        T result = accepted.get(normalized);
        if (result == null) {
            throw new RuntimePolicyException(field + " is invalid");
        }
        return result;
    }

    private record TemplateContext(
            String organizationRef,
            String personRef,
            String cellRef,
            String workloadClientId) {
    }

    private record PolicyDocument(
            String schemaVersion,
            long profileTtlSeconds,
            WorkspaceDocument workspace,
            ModelPolicyDocument modelPolicy,
            MatrixDocument matrix,
            McpDocument mcp,
            ApprovalsDocument approvals,
            SandboxDocument sandbox,
            AutomationDocument automation) {
    }

    private record WorkspaceDocument(
            String revision,
            String manifestRefTemplate,
            String runtimeStateStoreRefTemplate) {
    }

    private record ModelPolicyDocument(
            List<String> allowedProviders,
            List<String> allowedModels,
            List<String> fallback,
            Integer maximumContextTokens,
            String dataRegion) {
    }

    private record MatrixDocument(
            String accountRefTemplate,
            String homeserverRefTemplate,
            String credentialRefTemplate,
            List<String> allowedRooms,
            String autoJoin) {
    }

    private record McpDocument(List<McpServerDocument> servers, List<String> visibleToolClasses) {
    }

    private record McpServerDocument(
            String serverRef,
            String endpoint,
            String requestedResource,
            List<String> requiredScopes,
            String credentialRefTemplate,
            List<String> allowedToolClasses) {
    }

    private record ApprovalsDocument(
            PluginRoutingDocument pluginRouting,
            String execMode,
            String persistentTrustPolicy) {
    }

    private record PluginRoutingDocument(boolean enabled, String mode, List<String> targetRefs) {
    }

    private record SandboxDocument(
            String mode,
            String networkPolicy,
            List<String> allowedNetworkTargets,
            String filesystemPolicy,
            List<String> approvedMountRefs) {
    }

    private record AutomationDocument(boolean heartbeatEnabled, String schedulePolicy) {
    }
}
