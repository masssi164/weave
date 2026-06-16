package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.AuditWriteGate;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
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
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Clock clock;

    @Autowired
    public FilesFacadeService(
            ObjectProvider<FilesStorageAdapter> filesStorageAdapterProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
        this(filesStorageAdapterProvider, contextAuthorizationPort, contextAuthorizationProperties, workspaceCapabilityService, auditEventPublisherProvider.getIfAvailable(), Clock.systemUTC());
    }

    FilesFacadeService(
            ObjectProvider<FilesStorageAdapter> filesStorageAdapterProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this.filesStorageAdapter = filesStorageAdapterProvider.getIfAvailable();
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public FileListResponse list(String path) {
        requireContextPermission(ContextPermission.VIEW, "list-files");
        return configuredAdapter("list-files").list(path);
    }

    public FileItemResponse createFolder(CreateFolderRequest request) {
        PrincipalContext context = requireContextPermission(ContextPermission.EDIT, "create-folder");
        requireAuditPublisher();
        FileItemResponse created = configuredAdapter("create-folder").createFolder(request);
        publishMutationAudit(context, AuditAction.FILE_FOLDER_CREATED, "create-folder", created.id(), created.path());
        return created;
    }

    public FileUploadResponse upload(String parentPath, MultipartFile file) {
        PrincipalContext context = requireContextPermission(ContextPermission.EDIT, "upload-file");
        requireAuditPublisher();
        FileUploadResponse uploaded = configuredAdapter("upload-file").upload(parentPath, file);
        publishMutationAudit(context, AuditAction.FILE_UPLOADED, "upload-file", uploaded.item().id(), uploaded.item().path());
        return uploaded;
    }

    public DownloadedFile download(String id) {
        requireContextPermission(ContextPermission.VIEW, "download-file");
        return configuredAdapter("download-file").download(id);
    }

    public void delete(String id) {
        PrincipalContext context = requireContextPermission(ContextPermission.EDIT, "delete-file");
        requireAuditPublisher();
        configuredAdapter("delete-file").delete(id);
        publishMutationAudit(context, AuditAction.FILE_DELETED, "delete-file", id, null);
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

    private void requireAuditPublisher() {
        if (auditEventPublisher == null) {
            throw new com.massimotter.weave.backend.audit.AuditRequiredException("audit publisher is required before files mutations are allowed");
        }
    }

    private void publishMutationAudit(PrincipalContext context, AuditAction action, String operation, String canonicalId, String canonicalPath) {
        AuditWriteGate.publishRequired(auditEventPublisher, new AuditEvent(
                context.tenantId(),
                DEFAULT_CONTEXT_ID,
                context.principalRef(),
                "files-facade",
                action,
                clock.instant(),
                "files:" + operation + ":" + stableAuditRef(canonicalId, canonicalPath),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "canonicalId", stableAuditRef(canonicalId, canonicalPath),
                        "mappingRef", "provider-mapping://files/" + stableAuditRef(canonicalId, canonicalPath),
                        "contextId", DEFAULT_CONTEXT_ID)));
    }

    private String stableAuditRef(String canonicalId, String canonicalPath) {
        String source = canonicalId != null && !canonicalId.isBlank() ? canonicalId : canonicalPath;
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        return Integer.toHexString(source.hashCode());
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
                "files-adapter-not-configured",
                "Files capability is unavailable until an admin completes workspace storage configuration.",
                Map.of("module", "files", "operation", operation));
    }
}
