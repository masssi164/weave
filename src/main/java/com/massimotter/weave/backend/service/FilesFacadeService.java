package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import com.massimotter.weave.backend.service.files.DownloadedFile;
import com.massimotter.weave.backend.service.files.FilesStorageAdapter;
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

    private static final String DEFAULT_TENANT_ID = "tenant-default";
    private static final String DEFAULT_CONTEXT_ID = "workspace-default";

    private final FilesStorageAdapter filesStorageAdapter;
    private final ContextAuthorizationPort contextAuthorizationPort;

    public FilesFacadeService(
            ObjectProvider<FilesStorageAdapter> filesStorageAdapterProvider,
            ContextAuthorizationPort contextAuthorizationPort) {
        this.filesStorageAdapter = filesStorageAdapterProvider.getIfAvailable();
        this.contextAuthorizationPort = contextAuthorizationPort;
    }

    public FileListResponse list(String path) {
        requireContextPermission(ContextPermission.VIEW, "list-files");
        return configuredAdapter("list-files").list(path);
    }

    public FileItemResponse createFolder(CreateFolderRequest request) {
        requireContextPermission(ContextPermission.EDIT, "create-folder");
        return configuredAdapter("create-folder").createFolder(request);
    }

    public FileUploadResponse upload(String parentPath, MultipartFile file) {
        requireContextPermission(ContextPermission.EDIT, "upload-file");
        return configuredAdapter("upload-file").upload(parentPath, file);
    }

    public DownloadedFile download(String id) {
        requireContextPermission(ContextPermission.VIEW, "download-file");
        return configuredAdapter("download-file").download(id);
    }

    public void delete(String id) {
        requireContextPermission(ContextPermission.EDIT, "delete-file");
        configuredAdapter("delete-file").delete(id);
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
        PrincipalContext principalContext = principalContext(authentication);
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

    private PrincipalContext principalContext(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return new PrincipalContext(
                    claimOrDefault(jwt, "weave_tenant_id", "tenant_id", DEFAULT_TENANT_ID),
                    "user:" + jwt.getSubject());
        }
        return new PrincipalContext(DEFAULT_TENANT_ID, "user:" + authentication.getName());
    }

    private String claimOrDefault(Jwt jwt, String primaryClaim, String fallbackClaim, String defaultValue) {
        String primary = jwt.getClaimAsString(primaryClaim);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        String fallback = jwt.getClaimAsString(fallbackClaim);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return defaultValue;
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
                "nextcloud-adapter-not-configured",
                "Files facade is available, but the downstream Nextcloud adapter is not configured yet.",
                Map.of("module", "files", "operation", operation));
    }
}
