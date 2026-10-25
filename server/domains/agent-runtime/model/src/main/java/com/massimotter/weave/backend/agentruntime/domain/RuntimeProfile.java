package com.massimotter.weave.backend.agentruntime.domain;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record RuntimeProfile(
        String profileVersion,
        String profileId,
        String organizationRef,
        String personRef,
        RuntimeMemberBinding memberBinding,
        String cellRef,
        WorkloadIdentity workloadIdentity,
        Instant issuedAt,
        Instant expiresAt,
        String entitlementRevision,
        String workspaceRevision,
        String workspaceManifestRef,
        String runtimeStateStoreRef,
        boolean zeroDurableCellBytes,
        ModelPolicy modelPolicy,
        MatrixPolicy matrix,
        McpPolicy mcp,
        ApprovalPolicy approvals,
        SandboxPolicy sandbox,
        AutomationPolicy automation) {

    public static final String VERSION = "weave.runtime-profile/v2";
    private static final Pattern PROFILE_ID = Pattern.compile("rp_[A-Za-z0-9_-]+");
    private static final Pattern CLIENT_ID = Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");
    private static final Pattern CREDENTIAL_REF =
            Pattern.compile("credentialref://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+");

    public RuntimeProfile {
        if (!VERSION.equals(profileVersion)) {
            throw new IllegalArgumentException("only RuntimeProfile v2 is accepted");
        }
        requirePattern(profileId, PROFILE_ID, "profileId");
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        Objects.requireNonNull(memberBinding, "memberBinding");
        requireText(cellRef, "cellRef");
        Objects.requireNonNull(workloadIdentity, "workloadIdentity");
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("RuntimeProfile expiry must follow issuance");
        }
        requireText(entitlementRevision, "entitlementRevision");
        requireText(workspaceRevision, "workspaceRevision");
        requireText(workspaceManifestRef, "workspaceManifestRef");
        if (runtimeStateStoreRef == null || !runtimeStateStoreRef.startsWith("runtime-state://")) {
            throw new IllegalArgumentException("runtimeStateStoreRef must use runtime-state://");
        }
        if (!zeroDurableCellBytes) {
            throw new IllegalArgumentException("RuntimeProfile v2 requires zero durable cell bytes");
        }
        Objects.requireNonNull(modelPolicy, "modelPolicy");
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(mcp, "mcp");
        Objects.requireNonNull(approvals, "approvals");
        Objects.requireNonNull(sandbox, "sandbox");
        Objects.requireNonNull(automation, "automation");
    }

    public record WorkloadIdentity(
            String issuer,
            String subject,
            String clientId,
            String role,
            AuthenticationMethod authenticationMethod) {
        public WorkloadIdentity {
            requireHttps(issuer, "workload issuer");
            requireText(subject, "workload subject");
            requirePattern(clientId, CLIENT_ID, "workload clientId");
            if (!"weaver-runtime".equals(role)) {
                throw new IllegalArgumentException("workload role must be weaver-runtime");
            }
            Objects.requireNonNull(authenticationMethod, "authenticationMethod");
        }
    }

    public enum AuthenticationMethod {
        PRIVATE_KEY_JWT("private_key_jwt"),
        CLIENT_SECRET_BASIC("client_secret_basic");

        private final String wireValue;

        AuthenticationMethod(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public record ModelPolicy(
            List<String> allowedProviders,
            List<String> allowedModels,
            List<String> fallback,
            Integer maximumContextTokens,
            String dataRegion) {
        public ModelPolicy {
            allowedProviders = unique(allowedProviders, "allowedProviders", false);
            allowedModels = unique(allowedModels, "allowedModels", false);
            fallback = unique(fallback, "fallback", false);
            if (maximumContextTokens != null && maximumContextTokens < 1) {
                throw new IllegalArgumentException("maximumContextTokens must be positive");
            }
            dataRegion = optionalText(dataRegion, "dataRegion");
        }
    }

    public record MatrixPolicy(
            String accountRef,
            String homeserverRef,
            String credentialRef,
            List<String> allowedRooms,
            AutoJoin autoJoin,
            boolean encryptionRequired) {
        public MatrixPolicy {
            requireText(accountRef, "matrix accountRef");
            requireText(homeserverRef, "matrix homeserverRef");
            credentialRef = optionalCredentialRef(credentialRef);
            allowedRooms = unique(allowedRooms, "allowedRooms", false);
            if (!encryptionRequired) {
                throw new IllegalArgumentException("Matrix encryption is required");
            }
        }
    }

    public enum AutoJoin {
        OFF("off"),
        ALLOWLIST("allowlist");

        private final String wireValue;

        AutoJoin(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public record McpPolicy(List<McpServer> servers, List<String> visibleToolClasses) {
        public McpPolicy {
            servers = servers == null ? List.of() : List.copyOf(servers);
            if (servers.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("MCP servers must not contain null");
            }
            visibleToolClasses = unique(visibleToolClasses, "visibleToolClasses", false);
        }
    }

    public record McpServer(
            String serverRef,
            String endpoint,
            String requestedResource,
            String extensionId,
            String grantType,
            List<String> requiredScopes,
            String credentialRef,
            List<String> allowedToolClasses) {
        public McpServer {
            requireText(serverRef, "MCP serverRef");
            requireHttps(endpoint, "MCP endpoint");
            requireHttps(requestedResource, "MCP requestedResource");
            if (!"io.modelcontextprotocol/oauth-client-credentials".equals(extensionId)) {
                throw new IllegalArgumentException("unsupported MCP authorization extension");
            }
            if (!"client_credentials".equals(grantType)) {
                throw new IllegalArgumentException("MCP grantType must be client_credentials");
            }
            requiredScopes = unique(requiredScopes, "requiredScopes", true);
            requireCredentialRef(credentialRef);
            allowedToolClasses = allowedToolClasses == null
                    ? null
                    : unique(allowedToolClasses, "allowedToolClasses", false);
        }
    }

    public record ApprovalPolicy(
            String owner,
            PluginRouting pluginRouting,
            ExecMode execMode,
            PersistentTrustPolicy persistentTrustPolicy) {
        public ApprovalPolicy {
            if (!"openclaw".equals(owner)) {
                throw new IllegalArgumentException("OpenClaw owns approval routing");
            }
            Objects.requireNonNull(pluginRouting, "pluginRouting");
            Objects.requireNonNull(execMode, "execMode");
            Objects.requireNonNull(persistentTrustPolicy, "persistentTrustPolicy");
        }
    }

    public record PluginRouting(boolean enabled, PluginRoutingMode mode, List<String> targetRefs) {
        public PluginRouting {
            Objects.requireNonNull(mode, "plugin routing mode");
            targetRefs = targetRefs == null ? null : unique(targetRefs, "targetRefs", false);
            if (mode == PluginRoutingMode.TARGETS && (targetRefs == null || targetRefs.isEmpty())) {
                throw new IllegalArgumentException("targets routing requires targetRefs");
            }
        }
    }

    public enum PluginRoutingMode {
        SAME_CHAT("same-chat"), TARGETS("targets"), LOCAL_ONLY("local-only");
        private final String wireValue;
        PluginRoutingMode(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public enum ExecMode {
        DENY("deny"), ALLOWLIST("allowlist"), ASK("ask"), AUTO("auto"), FULL("full");
        private final String wireValue;
        ExecMode(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public enum PersistentTrustPolicy {
        DISABLED("disabled"), BOUNDED("bounded");
        private final String wireValue;
        PersistentTrustPolicy(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public record SandboxPolicy(
            SandboxMode mode,
            NetworkPolicy networkPolicy,
            List<String> allowedNetworkTargets,
            FilesystemPolicy filesystemPolicy,
            List<String> approvedMountRefs) {
        public SandboxPolicy {
            Objects.requireNonNull(mode, "sandbox mode");
            Objects.requireNonNull(networkPolicy, "networkPolicy");
            Objects.requireNonNull(filesystemPolicy, "filesystemPolicy");
            allowedNetworkTargets = allowedNetworkTargets == null
                    ? null
                    : unique(allowedNetworkTargets, "allowedNetworkTargets", false);
            approvedMountRefs = approvedMountRefs == null
                    ? null
                    : unique(approvedMountRefs, "approvedMountRefs", false);
        }
    }

    public enum SandboxMode {
        REQUIRED("required"), RESTRICTED("restricted"), TRUSTED_ADMIN_ONLY("trusted-admin-only");
        private final String wireValue;
        SandboxMode(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public enum NetworkPolicy {
        DENY("deny"), ALLOWLIST("allowlist");
        private final String wireValue;
        NetworkPolicy(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public enum FilesystemPolicy {
        WORKSPACE_ONLY("workspace-only"),
        WORKSPACE_PLUS_APPROVED_MOUNTS("workspace-plus-approved-mounts");
        private final String wireValue;
        FilesystemPolicy(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public record AutomationPolicy(boolean heartbeatEnabled, SchedulePolicy schedulePolicy) {
        public AutomationPolicy {
            Objects.requireNonNull(schedulePolicy, "schedulePolicy");
        }
    }

    public enum SchedulePolicy {
        DISABLED("disabled"), USER_OWNED("user-owned"), ORGANIZATION_MANAGED("organization-managed");
        private final String wireValue;
        SchedulePolicy(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    private static List<String> unique(List<String> values, String field, boolean requireNonEmpty) {
        if (values == null || (requireNonEmpty && values.isEmpty())) {
            throw new IllegalArgumentException(field + " is required");
        }
        List<String> copy = List.copyOf(values);
        copy.forEach(value -> requireText(value, field));
        if (Set.copyOf(copy).size() != copy.size()) {
            throw new IllegalArgumentException(field + " must contain unique values");
        }
        return copy;
    }

    private static String optionalText(String value, String field) {
        if (value == null) {
            return null;
        }
        requireText(value, field);
        return value;
    }

    private static String optionalCredentialRef(String value) {
        if (value != null) {
            requireCredentialRef(value);
        }
        return value;
    }

    private static void requireCredentialRef(String value) {
        requirePattern(value, CREDENTIAL_REF, "credentialRef");
    }

    private static void requireHttps(String value, String field) {
        requireText(value, field);
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(field + " must be an HTTPS URI without user info or fragment");
        }
    }

    private static void requirePattern(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
