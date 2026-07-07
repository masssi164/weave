package com.massimotter.weave.backend.service;

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
import com.massimotter.weave.backend.service.files.FilesStorageAdapter;
import java.util.List;
import java.util.Map;
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

    public FilesFacadeService(
            ObjectProvider<FilesStorageAdapter> filesStorageAdapterProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.filesStorageAdapter = filesStorageAdapterProvider.getIfAvailable();
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
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
        requireContextPermission(ContextPermission.EDIT, "create-folder");
        throw webDavWritePolicyRequired("create-folder");
    }

    public FileUploadResponse upload(String parentPath, MultipartFile file) {
        requireContextPermission(ContextPermission.EDIT, "upload-file");
        throw webDavWritePolicyRequired("upload-file");
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
        requireContextPermission(ContextPermission.EDIT, "delete-file");
        throw webDavWritePolicyRequired("delete-file");
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
                        "MCP files.search/files.read via WebDAV-backed Weave Files facade/projection",
                        "OpenAPI /api/files/readiness and native-provider-setup for discovery/status/revoke control plane",
                        "WebDAV writes blocked by #1007 until ETag, conflict, lock, quota, revocation, and audit policy exists"),
                List.of(
                        "native-extension-implementation",
                        "per-device-token-revocation",
                        "physical-device-provider-proof"));
    }

    private void requireContextPermission(ContextPermission permission, String operation) {
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

    private ApiErrorException webDavWritePolicyRequired(String operation) {
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

    private ApiErrorException supportSafeStorageError(ApiErrorException exception, String operation) {
        String code = switch (exception.code()) {
            case "nextcloud-adapter-not-configured" -> "files-storage-not-configured";
            case "nextcloud-auth-failed" -> "files-storage-auth-failed";
            case "nextcloud-response-invalid" -> "files-storage-response-invalid";
            case "nextcloud-unavailable" -> "files-storage-unavailable";
            case "nextcloud-request-failed" -> "files-storage-request-failed";
            default -> exception.code();
        };
        return new ApiErrorException(
                exception.status(),
                code,
                supportSafeStorageMessage(code),
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
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
