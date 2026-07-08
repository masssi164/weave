package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileNativeProviderOptionResponse;
import com.massimotter.weave.backend.model.files.FileNativeProviderSetupResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import com.massimotter.weave.backend.service.files.DownloadedFile;
import com.massimotter.weave.backend.service.files.FilePathCodec;
import com.massimotter.weave.backend.service.files.FilesStorageAdapter;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FilesFacadeService {

    private static final String DEFAULT_CONTEXT_ID = "workspace-default";

    private final FilesStorageAdapter filesStorageAdapter;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final AuditEventPublisher auditEventPublisher;

    @Autowired
    public FilesFacadeService(
            ObjectProvider<FilesStorageAdapter> filesStorageAdapterProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
        this(
                filesStorageAdapterProvider,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                workspaceCapabilityService,
                auditEventPublisherProvider.getIfAvailable(InMemoryAuditEventPublisher::new));
    }

    public FilesFacadeService(
            ObjectProvider<FilesStorageAdapter> filesStorageAdapterProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher) {
        this.filesStorageAdapter = filesStorageAdapterProvider.getIfAvailable();
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.auditEventPublisher = auditEventPublisher;
    }

    public FilesFacadeService(
            ObjectProvider<FilesStorageAdapter> filesStorageAdapterProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this(
                filesStorageAdapterProvider,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                workspaceCapabilityService,
                new InMemoryAuditEventPublisher());
    }

    public FileListResponse list(String path) {
        requireContextPermission(ContextPermission.VIEW, "list-files");
        try {
            return configuredAdapter("list-files").list(path);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, "list-files");
        }
    }

    public FileItemResponse createFolder(CreateFolderRequest request) {
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, "create-folder");
        throw webDavWritePolicyRequired("create-folder", principal, FilePathCodec.childPath(request.parentPath(), request.name()), null);
    }

    public FileUploadResponse upload(String parentPath, MultipartFile file) {
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, "upload-file");
        throw webDavWritePolicyRequired("upload-file", principal, parentPath, null);
    }

    public DownloadedFile download(String id) {
        requireContextPermission(ContextPermission.VIEW, "download-file");
        try {
            return configuredAdapter("download-file").download(id);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, "download-file");
        }
    }

    public void delete(String id) {
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, "delete-file");
        throw webDavWritePolicyRequired("delete-file", principal, FilePathCodec.pathFromId(id), null);
    }

    public ApiErrorException rejectWebDavWrite(String method, String path) {
        String normalizedMethod = method == null ? "WRITE" : method.toUpperCase(Locale.ROOT);
        String operation = "webdav-" + normalizedMethod.toLowerCase(Locale.ROOT);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        return webDavWritePolicyRequired(operation, principal, FilePathCodec.normalizeProductPath(path), normalizedMethod);
    }

    public WebDavMutationResult putWebDavFile(
            String path,
            byte[] content,
            String contentType,
            String ifMatch,
            String ifNoneMatch) {
        String operation = "webdav-put";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "PUT", "attempted");
        try {
            FilesStorageAdapter adapter = configuredAdapter(operation);
            VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, true);
            enforcePreconditions(existing, ifMatch, ifNoneMatch, operation);
            if (existing != null && !"file".equals(existing.item().type())) {
                throw fileConflict(operation, normalizedPath, "PUT cannot replace a collection.");
            }
            byte[] writeContent = content == null ? new byte[0] : content;
            FileItemResponse stored = adapter.put(normalizedPath, writeContent, contentType);
            VersionedFileItem updated = firstNonNull(
                    existingVersionedItem(adapter, normalizedPath, operation, false),
                    versioned(stored, contentVersionToken(writeContent)));
            String etag = etag(updated);
            publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "PUT", "completed");
            return new WebDavMutationResult(updated.item(), etag, existing == null);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public WebDavMutationResult createWebDavFolder(String path, String ifMatch, String ifNoneMatch) {
        String operation = "webdav-mkcol";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "MKCOL", "attempted");
        try {
            FilesStorageAdapter adapter = configuredAdapter(operation);
            VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, true);
            enforcePreconditions(existing, ifMatch, ifNoneMatch, operation);
            if (existing != null) {
                throw fileConflict(operation, normalizedPath, "A collection or file already exists at this path.");
            }
            FileItemResponse stored = adapter.createFolder(new CreateFolderRequest(parentPath(normalizedPath), fileName(normalizedPath)));
            VersionedFileItem updated = firstNonNull(
                    existingVersionedItem(adapter, normalizedPath, operation, false),
                    versioned(stored, null));
            String etag = etag(updated);
            publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "MKCOL", "completed");
            return new WebDavMutationResult(updated.item(), etag, true);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public void deleteWebDavPath(String path, String ifMatch) {
        String operation = "webdav-delete";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "DELETE", "attempted");
        try {
            FilesStorageAdapter adapter = configuredAdapter(operation);
            VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, false);
            if (existing == null) {
                throw new ApiErrorException(
                        HttpStatus.NOT_FOUND,
                        "file-not-found",
                        "The requested file or folder was not found.",
                        Map.of("module", "files", "operation", operation));
            }
            enforcePreconditions(existing, ifMatch, null, operation);
            adapter.delete(normalizedPath);
            publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "DELETE", "completed");
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public String etagFor(String path) {
        String operation = "webdav-etag";
        requireContextPermission(ContextPermission.VIEW, operation);
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
        try {
            VersionedFileItem item = existingVersionedItem(configuredAdapter(operation), normalizedPath, operation, false);
            if (item == null) {
                throw new ApiErrorException(
                        HttpStatus.NOT_FOUND,
                        "file-not-found",
                        "The requested file or folder was not found.",
                        Map.of("module", "files", "operation", operation));
            }
            return etag(item);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public FileNativeProviderSetupResponse nativeProviderSetup(Jwt jwt) {
        return new FileNativeProviderSetupResponse(
                workspaceCapabilityService.snapshot(jwt).files(),
                true,
                false,
                false,
                "/dav/files",
                "/dav/files/{path}",
                "/dav/files/{path}",
                "/dav/files/{path}",
                List.of(
                        new FileNativeProviderOptionResponse(
                                "ios",
                                "FileProviderExtension",
                                "pigeon-or-platform-channel",
                                false,
                                "extension_contract_ready",
                                "open-weave-files-native-setup",
                                List.of(
                                        "ios-file-provider-extension",
                                        "per-device-weave-token",
                                        "list-open-proof"),
                                List.of(
                                        "The iOS extension must call Weave file facade endpoints only.",
                                        "Flutter may control setup, status, and revoke but not file IO.")),
                        new FileNativeProviderOptionResponse(
                                "android",
                                "DocumentsProvider",
                                "pigeon-or-platform-channel",
                                false,
                                "provider_contract_ready",
                                "open-weave-files-native-setup",
                                List.of(
                                        "android-documents-provider",
                                        "per-device-weave-token",
                                        "root-document-open-proof"),
                                List.of(
                                        "The Android provider must expose roots only when the Weave session is valid.",
                                        "Persistable URI permissions must reference Weave document IDs, not provider URLs."))),
                List.of(
                        "OPTIONS /dav/files",
                        "PROPFIND /dav/files",
                        "GET /dav/files/{path}",
                        "PUT /dav/files/{path}",
                        "DELETE /dav/files/{path}",
                        "MKCOL /dav/files/{path}",
                        "MCP files.search/files.read via WebDAV-backed Weave Files facade/projection",
                        "OpenAPI /api/files/readiness and native-provider-setup for discovery/status/revoke control plane",
                        "WebDAV writes use Weave ETag preconditions, support-safe conflict/storage errors, and mutation audit"),
                List.of(
                        "native-extension-implementation",
                        "per-device-token-revocation",
                        "physical-device-provider-proof"));
    }

    private PrincipalContext requireContextPermission(ContextPermission permission, String operation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required.",
                    Map.of("module", "files", "operation", operation));
        }
        Jwt jwt = jwtPrincipal(authentication, operation);
        workspaceCapabilityService.requireCapability(jwt, capabilityFor(permission), "files", operation);
        PrincipalContext principalContext = principalContext(jwt, operation);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                principalContext.tenantId(),
                DEFAULT_CONTEXT_ID,
                principalContext.principalRef(),
                permission));
        if (!decision.allowed()) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "files-forbidden",
                    "Files access is not allowed for this Context/Space.",
                    Map.of(
                            "module", "files",
                            "operation", operation,
                            "reason", decision.reason(),
                            "contextId", DEFAULT_CONTEXT_ID,
                            "permission", permission.name().toLowerCase()));
        }
        return principalContext;
    }

    private Jwt jwtPrincipal(Authentication authentication, String operation) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw invalidAuthentication(operation, "JWT principal is required");
    }

    private String capabilityFor(ContextPermission permission) {
        return permission == ContextPermission.VIEW ? "files.read" : "files.upload";
    }

    private PrincipalContext principalContext(Jwt jwt, String operation) {
        return new PrincipalContext(jwtTenantId(jwt, operation), jwtPrincipalRef(jwt, operation));
    }

    private String jwtTenantId(Jwt jwt, String operation) {
        String tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantClaim());
        if (tenantId == null) {
            tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantFallbackClaim());
        }
        if (tenantId == null) {
            throw invalidAuthentication(operation, "tenant claim is missing");
        }
        return tenantId;
    }

    private String jwtPrincipalRef(Jwt jwt, String operation) {
        String claimName = contextAuthorizationProperties.principalClaim();
        String configuredClaim = jwtClaim(jwt, claimName);
        if (configuredClaim != null) {
            return contextAuthorizationProperties.principalRef(configuredClaim);
        }
        String subject = jwt.getSubject();
        String principalRef = contextAuthorizationProperties.principalRef(subject);
        if (principalRef == null) {
            throw invalidAuthentication(operation, "principal claim is missing");
        }
        return principalRef;
    }

    private String jwtClaim(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ApiErrorException invalidAuthentication(String operation, String reason) {
        return new ApiErrorException(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Authentication is required.",
                Map.of("module", "files", "operation", operation, "reason", reason));
    }

    private record PrincipalContext(String tenantId, String principalRef) {
    }

    private FilesStorageAdapter configuredAdapter(String operation) {
        if (filesStorageAdapter == null || !filesStorageAdapter.isConfigured()) {
            throw adapterNotConfigured(operation);
        }
        return filesStorageAdapter;
    }

    private ApiErrorException adapterNotConfigured(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-storage-not-configured",
                "Files facade is available, but file storage is not configured yet.",
                Map.of("module", "files", "operation", operation));
    }

    private ApiErrorException webDavWritePolicyRequired(
            String operation,
            PrincipalContext principal,
            String productPath,
            String webDavMethod) {
        publishBlockedWriteAudit(operation, principal, productPath, webDavMethod);
        return new ApiErrorException(
                HttpStatus.NOT_IMPLEMENTED,
                "files-webdav-write-policy-required",
                "Files writes are blocked until the Weave WebDAV write policy is evidenced in #1007.",
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "webDavFacadePath", "/dav/files",
                        "writePolicyIssue", "#1007",
                        "openApiDataPlaneUsed", false,
                        "diagnosticsRedacted", true));
    }

    private void publishBlockedWriteAudit(
            String operation,
            PrincipalContext principal,
            String productPath,
            String webDavMethod) {
        auditEventPublisher.publish(new AuditEvent(
                principal.tenantId(),
                DEFAULT_CONTEXT_ID,
                principal.principalRef(),
                "files:webdav-gate",
                AuditAction.FILES_WEBDAV_WRITE_BLOCKED,
                Instant.now(),
                "files-webdav-write-blocked:" + UUID.randomUUID(),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "domain", "files",
                        "operation", operation,
                        "webDavMethod", webDavMethod == null ? "legacy-service-mutation" : webDavMethod,
                        "productPath", productPath == null ? "/" : FilePathCodec.normalizeProductPath(productPath),
                        "result", "blocked_write_policy_required",
                        "writePolicyIssue", "#1007",
                        "webDavFacadePath", "/dav/files",
                        "openApiDataPlaneUsed", false,
                        "supportSafe", true)));
    }

    private void publishWriteAudit(
            AuditAction action,
            String operation,
            PrincipalContext principal,
            String productPath,
            String webDavMethod,
            String result) {
        auditEventPublisher.publish(new AuditEvent(
                principal.tenantId(),
                DEFAULT_CONTEXT_ID,
                principal.principalRef(),
                "files:webdav",
                action,
                Instant.now(),
                "files-webdav-write:" + UUID.randomUUID(),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "domain", "files",
                        "operation", operation,
                        "webDavMethod", webDavMethod,
                        "productPath", FilePathCodec.normalizeProductPath(productPath),
                        "result", result,
                        "webDavFacadePath", "/dav/files",
                        "openApiDataPlaneUsed", false,
                        "supportSafe", true)));
    }

    private String requireMutableWebDavPath(String path, String operation) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        if ("/".equals(normalized)) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "files-forbidden",
                    "The Files root collection cannot be mutated through this operation.",
                    Map.of("module", "files", "operation", operation));
        }
        return normalized;
    }

    private FileItemResponse existingItem(
            FilesStorageAdapter adapter,
            String path,
            String operation,
            boolean missingParentAsConflict) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        if ("/".equals(normalized)) {
            return new FileItemResponse("files:root", "Files", "/", "folder", null, null, null, false);
        }
        try {
            return adapter.list(parentPath(normalized)).items().stream()
                    .filter(item -> FilePathCodec.normalizeProductPath(item.path()).equals(normalized))
                    .findFirst()
                    .orElse(null);
        } catch (ApiErrorException exception) {
            if (missingParentAsConflict && "file-not-found".equals(exception.code())) {
                throw fileConflict(operation, normalized, "The parent collection does not exist.");
            }
            throw exception;
        }
    }

    private VersionedFileItem existingVersionedItem(
            FilesStorageAdapter adapter,
            String path,
            String operation,
            boolean missingParentAsConflict) {
        FileItemResponse item = existingItem(adapter, path, operation, missingParentAsConflict);
        return item == null ? null : versioned(item, adapter.versionToken(path));
    }

    private VersionedFileItem versioned(FileItemResponse item, String versionToken) {
        return new VersionedFileItem(item, StringUtils.hasText(versionToken) ? versionToken.trim() : null);
    }

    private void enforcePreconditions(
            VersionedFileItem existing,
            String ifMatch,
            String ifNoneMatch,
            String operation) {
        if (ifMatch != null && !ifMatch.isBlank()) {
            if (existing == null || !etagMatches(etag(existing), ifMatch)) {
                throw preconditionFailed(operation, "If-Match did not match the current file state.");
            }
        }
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            if ("*".equals(ifNoneMatch.trim()) && existing != null) {
                throw preconditionFailed(operation, "If-None-Match requires the target path to be absent.");
            }
            if (existing != null && etagMatches(etag(existing), ifNoneMatch)) {
                throw preconditionFailed(operation, "If-None-Match matched the current file state.");
            }
        }
    }

    private boolean etagMatches(String currentEtag, String candidateHeader) {
        if (candidateHeader == null || candidateHeader.isBlank()) {
            return false;
        }
        return Arrays.stream(candidateHeader.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .anyMatch(value -> "*".equals(value)
                        || Objects.equals(normalizeEtagToken(currentEtag), normalizeEtagToken(value)));
    }

    private String normalizeEtagToken(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private ApiErrorException preconditionFailed(String operation, String message) {
        return new ApiErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "files-precondition-failed",
                message,
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "webDavFacadePath", "/dav/files",
                        "openApiDataPlaneUsed", false,
                        "diagnosticsRedacted", true));
    }

    private ApiErrorException fileConflict(String operation, String path, String message) {
        return new ApiErrorException(
                HttpStatus.CONFLICT,
                "file-conflict",
                message,
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "path", path,
                        "webDavFacadePath", "/dav/files",
                        "openApiDataPlaneUsed", false,
                        "diagnosticsRedacted", true));
    }

    private String etag(VersionedFileItem versionedItem) {
        return etag(versionedItem.item(), versionedItem.versionToken());
    }

    private String etag(FileItemResponse item, String versionToken) {
        String material = String.join("|",
                FilePathCodec.normalizeProductPath(item.path()),
                item.type(),
                String.valueOf(item.size()),
                timestamp(item.modifiedAt()),
                item.mimeType() == null ? "" : item.mimeType(),
                versionToken == null ? "" : versionToken);
        return "\"" + sha256(material) + "\"";
    }

    private String contentVersionToken(byte[] content) {
        return "content-sha256:" + sha256Token(content);
    }

    private String sha256(String material) {
        return sha256Token(material.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Token(byte[] material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is required for WebDAV ETags", exception);
        }
    }

    private String timestamp(OffsetDateTime value) {
        return value == null ? "" : value.toInstant().toString();
    }

    private String parentPath(String path) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        int separator = normalized.lastIndexOf('/');
        return separator <= 0 ? "/" : normalized.substring(0, separator);
    }

    private String fileName(String path) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private VersionedFileItem firstNonNull(VersionedFileItem primary, VersionedFileItem fallback) {
        return primary == null ? fallback : primary;
    }

    private record VersionedFileItem(FileItemResponse item, String versionToken) {
    }

    private ApiErrorException supportSafeStorageError(ApiErrorException exception, String operation) {
        String code = switch (exception.code()) {
            case "nextcloud-adapter-not-configured" -> "files-storage-not-configured";
            case "nextcloud-auth-failed" -> "files-storage-auth-failed";
            case "nextcloud-response-invalid" -> "files-storage-response-invalid";
            case "nextcloud-unavailable" -> "files-storage-unavailable";
            case "nextcloud-request-failed" -> "files-storage-request-failed";
            default -> exception.code();
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("module", "files");
        details.put("operation", operation);
        details.put("diagnosticsRedacted", true);
        if (operation.startsWith("webdav-")) {
            details.put("webDavFacadePath", "/dav/files");
            details.put("openApiDataPlaneUsed", false);
        }
        return new ApiErrorException(
                exception.status(),
                code,
                supportSafeStorageMessage(code),
                Map.copyOf(details));
    }

    private String supportSafeStorageMessage(String code) {
        return switch (code) {
            case "files-storage-not-configured" -> "Files facade is available, but file storage is not configured yet.";
            case "files-storage-auth-failed" -> "Files storage is unavailable because the backend actor is not authorized.";
            case "files-storage-response-invalid" -> "Files storage returned an invalid response.";
            case "files-storage-unavailable" -> "Files storage is temporarily unavailable.";
            case "files-storage-request-failed" -> "Files storage request failed before it could be completed.";
            case "files-permission-denied" -> "You do not have permission to access this file or folder.";
            case "file-not-found" -> "The requested file or folder was not found.";
            case "file-conflict" -> "The file operation conflicts with the current storage state.";
            case "files-quota-exceeded" -> "There is not enough storage available for this file operation.";
            default -> "The files request could not be completed.";
        };
    }
}
