package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.agentruntime.domain.WeaverWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.port.McpWorkloadAuthorizationException;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileListing;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileQuota;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileNativeProviderOptionResponse;
import com.massimotter.weave.backend.model.files.FileNativeProviderSetupResponse;
import com.massimotter.weave.backend.model.files.FileSetupCredentialListResponse;
import com.massimotter.weave.backend.model.files.FileSetupCredentialRequest;
import com.massimotter.weave.backend.model.files.FileSetupCredentialResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import com.massimotter.weave.backend.service.files.DownloadedFile;
import com.massimotter.weave.backend.service.files.FilePathCodec;
import com.massimotter.weave.backend.service.files.WebDavPropfindListing;
import com.massimotter.weave.backend.service.files.WebDavPropfindResource;
import com.massimotter.weave.backend.service.files.WebDavLockResult;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest;
import com.massimotter.weave.backend.service.files.WebDavSearchResult;
import com.massimotter.weave.backend.security.device.DeviceCredential;
import com.massimotter.weave.backend.security.device.DeviceCredentialException;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    private final FilesProviderPort filesProviderPort;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final AuditEventPublisher auditEventPublisher;
    private final DeviceCredentialService deviceCredentialService;
    private final McpWorkloadAuthorizationService mcpWorkloadAuthorizationService;
    private final McpExchangedTokenPolicy mcpExchangedTokenPolicy;
    private final Map<String, WebDavLockState> webDavLocks = new ConcurrentHashMap<>();

    @Autowired
    public FilesFacadeService(
            ObjectProvider<FilesProviderPort> filesProviderPortProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            DeviceCredentialService deviceCredentialService,
            AuditEventPublisher auditEventPublisher,
            ObjectProvider<McpWorkloadAuthorizationService> mcpWorkloadAuthorizationServiceProvider,
            ObjectProvider<McpExchangedTokenPolicy> mcpExchangedTokenPolicyProvider) {
        this(
                filesProviderPortProvider,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                workspaceCapabilityService,
                deviceCredentialService,
                auditEventPublisher,
                mcpWorkloadAuthorizationServiceProvider.getIfAvailable(),
                mcpExchangedTokenPolicyProvider.getIfAvailable());
    }

    FilesFacadeService(
            ObjectProvider<FilesProviderPort> filesProviderPortProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            DeviceCredentialService deviceCredentialService,
            AuditEventPublisher auditEventPublisher,
            McpWorkloadAuthorizationService mcpWorkloadAuthorizationService,
            McpExchangedTokenPolicy mcpExchangedTokenPolicy) {
        this.filesProviderPort = filesProviderPortProvider.getIfAvailable();
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.deviceCredentialService = deviceCredentialService;
        this.auditEventPublisher = auditEventPublisher;
        this.mcpWorkloadAuthorizationService = mcpWorkloadAuthorizationService;
        this.mcpExchangedTokenPolicy = mcpExchangedTokenPolicy;
    }

    public FileListResponse list(String path) {
        requireContextPermission(ContextPermission.VIEW, "list-files");
        try {
            return toResponse(configuredAdapter("list-files").list(new FilePath(path)).listing());
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, "list-files");
        }
    }

    public WebDavPropfindListing webDavPropfind(String path) {
        String operation = "webdav-propfind";
        requireContextPermission(ContextPermission.VIEW, operation);
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
        try {
            VersionedListing versionedListing = configuredAdapter(operation).list(new FilePath(normalizedPath));
            FileListing listing = versionedListing.listing();
            FileObject requested = new FileObject(
                    new FileId("files:" + normalizedPath),
                    new FilePath(normalizedPath),
                    Kind.COLLECTION,
                    0,
                    null,
                    null,
                    false);
            return new WebDavPropfindListing(
                    webDavResource(requested, versionedListing.requestedVersion()),
                    listing.children().stream()
                            .map(item -> webDavResource(item, versionedListing.childVersions().get(item.path())))
                            .toList(),
                    toResponse(listing.quota()));
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    /**
     * Executes the deliberately bounded Weave profile of RFC 5323 basicsearch.
     *
     * <p>The traversal remains on the canonical Files port. Provider URLs and identifiers never
     * enter the northbound result.
     */
    public WebDavSearchResult webDavSearch(WebDavSearchRequest request) {
        String operation = "webdav-search";
        PrincipalContext principal = requireContextPermission(ContextPermission.VIEW, operation);
        String scopePath = FilePathCodec.normalizeProductPath(request.scopePath());
        String needle = request.query().toLowerCase(Locale.ROOT);
        ArrayDeque<SearchNode> pending = new ArrayDeque<>();
        pending.add(new SearchNode(scopePath, 0));
        List<WebDavPropfindResource> matches = new java.util.ArrayList<>();
        int scanned = 0;
        try {
            FilesProviderPort adapter = configuredAdapter(operation);
            while (!pending.isEmpty() && matches.size() < request.limit()) {
                SearchNode node = pending.removeFirst();
                VersionedListing listing = adapter.list(new FilePath(node.path()));
                for (FileObject item : listing.listing().children()) {
                    if (++scanned > 1_000) {
                        WebDavSearchResult bounded = new WebDavSearchResult(matches);
                        publishWorkloadReadAudit(
                                principal, "files.search", scopePath, "bounded", bounded.resources().size());
                        return bounded;
                    }
                    String searchable = request.matchField() == WebDavSearchRequest.MatchField.CANONICAL_ID
                            ? item.id().value().toLowerCase(Locale.ROOT)
                            : (item.name() + "\n" + item.path().value()).toLowerCase(Locale.ROOT);
                    boolean matchesQuery = request.matchField() == WebDavSearchRequest.MatchField.CANONICAL_ID
                            ? searchable.equals(needle)
                            : searchable.contains(needle);
                    if (matchesQuery) {
                        matches.add(webDavResource(item, listing.childVersions().get(item.path())));
                        if (matches.size() >= request.limit()) {
                            break;
                        }
                    }
                    if (item.kind() == Kind.COLLECTION && node.depth() < 8) {
                        pending.addLast(new SearchNode(item.path().value(), node.depth() + 1));
                    }
                }
            }
            WebDavSearchResult result = new WebDavSearchResult(matches);
            publishWorkloadReadAudit(
                    principal, "files.search", scopePath, "completed", result.resources().size());
            return result;
        } catch (ApiErrorException exception) {
            publishWorkloadReadAudit(principal, "files.search", scopePath, "failed", 0);
            throw supportSafeStorageError(exception, operation);
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
        PrincipalContext principal =
                requireContextPermission(ContextPermission.VIEW, "download-file");
        try {
            FileContent content = configuredAdapter("download-file").read(new FileId(id));
            publishWorkloadReadAudit(principal, "files.resource.read", id, "completed", 1);
            return new DownloadedFile(content.item().name(), content.item().mediaType(), content.bytes());
        } catch (ApiErrorException exception) {
            publishWorkloadReadAudit(principal, "files.resource.read", id, "failed", 0);
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
        return putWebDavFile(path, content, contentType, ifMatch, ifNoneMatch, null);
    }

    public WebDavMutationResult putWebDavFile(
            String path,
            byte[] content,
            String contentType,
            String ifMatch,
            String ifNoneMatch,
            String ifHeader) {
        String operation = "webdav-put";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedPath, ifHeader, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "PUT", "attempted");
        try {
            FilesProviderPort adapter = configuredAdapter(operation);
            VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, true);
            enforcePreconditions(existing, ifMatch, ifNoneMatch, operation);
            if (existing != null && existing.item().kind() != Kind.FILE) {
                throw fileConflict(operation, normalizedPath, "PUT cannot replace a collection.");
            }
            if (existing == null) {
                requireParentCollection(adapter, normalizedPath, operation);
            }
            byte[] writeContent = content == null ? new byte[0] : content;
            FileObject stored = adapter.write(new FileWrite(new FilePath(normalizedPath), writeContent, contentType));
            VersionedFileItem updated = firstNonNull(
                    existingVersionedItem(adapter, normalizedPath, operation, false),
                    versioned(stored, new FileVersion(contentVersionToken(writeContent))));
            String etag = etag(updated);
            publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "PUT", "completed");
            return new WebDavMutationResult(toResponse(updated.item()), etag, existing == null);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public WebDavMutationResult createWebDavFolder(String path, String ifMatch, String ifNoneMatch) {
        return createWebDavFolder(path, ifMatch, ifNoneMatch, null);
    }

    public WebDavMutationResult createWebDavFolder(String path, String ifMatch, String ifNoneMatch, String ifHeader) {
        String operation = "webdav-mkcol";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedPath, ifHeader, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "MKCOL", "attempted");
        try {
            FilesProviderPort adapter = configuredAdapter(operation);
            VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, true);
            enforcePreconditions(existing, ifMatch, ifNoneMatch, operation);
            if (existing != null) {
                throw fileConflict(operation, normalizedPath, "A collection or file already exists at this path.");
            }
            requireParentCollection(adapter, normalizedPath, operation);
            FileObject stored = adapter.createCollection(new FilePath(normalizedPath));
            VersionedFileItem updated = firstNonNull(
                    existingVersionedItem(adapter, normalizedPath, operation, false),
                    versioned(stored, FileVersion.unknown()));
            String etag = etag(updated);
            publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "MKCOL", "completed");
            return new WebDavMutationResult(toResponse(updated.item()), etag, true);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public void deleteWebDavPath(String path, String ifMatch) {
        deleteWebDavPath(path, ifMatch, null);
    }

    public void deleteWebDavPath(String path, String ifMatch, String ifHeader) {
        String operation = "webdav-delete";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedPath, ifHeader, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "DELETE", "attempted");
        try {
            FilesProviderPort adapter = configuredAdapter(operation);
            VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, false);
            if (existing == null) {
                throw new ApiErrorException(
                        HttpStatus.NOT_FOUND,
                        "file-not-found",
                        "The requested file or folder was not found.",
                        Map.of("module", "files", "operation", operation));
            }
            enforcePreconditions(existing, ifMatch, null, operation);
            adapter.delete(new FilePath(normalizedPath), existing.version());
            publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "DELETE", "completed");
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public WebDavMutationResult copyWebDavPath(
            String sourcePath,
            String destinationPath,
            boolean overwrite,
            String ifMatch,
            String ifHeader) {
        String operation = "webdav-copy";
        String normalizedSource = requireMutableWebDavPath(sourcePath, operation);
        String normalizedDestination = requireMutableWebDavPath(destinationPath, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedDestination, ifHeader, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedSource, "COPY", "attempted");
        try {
            FilesProviderPort adapter = configuredAdapter(operation);
            VersionedFileItem source = existingVersionedItem(adapter, normalizedSource, operation, false);
            if (source == null) {
                throw new ApiErrorException(
                        HttpStatus.NOT_FOUND,
                        "file-not-found",
                        "The requested file or folder was not found.",
                        Map.of("module", "files", "operation", operation));
            }
            enforcePreconditions(source, ifMatch, null, operation);
            VersionedFileItem destination = existingVersionedItem(adapter, normalizedDestination, operation, true);
            if (destination != null && !overwrite) {
                throw preconditionFailed(operation, "Overwrite is false and the destination already exists.");
            }
            if (destination == null) {
                requireParentCollection(adapter, normalizedDestination, operation);
            }
            FileObject copied = adapter.copy(new FilePath(normalizedSource), new FilePath(normalizedDestination), overwrite);
            VersionedFileItem updated = firstNonNull(
                    existingVersionedItem(adapter, normalizedDestination, operation, false),
                    versioned(copied, FileVersion.unknown()));
            publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedDestination, "COPY", "completed");
            return new WebDavMutationResult(toResponse(updated.item()), etag(updated), destination == null);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        } catch (UnsupportedOperationException exception) {
            throw unsupportedWebDavMutation(operation, "COPY");
        }
    }

    public WebDavMutationResult moveWebDavPath(
            String sourcePath,
            String destinationPath,
            boolean overwrite,
            String ifMatch,
            String ifHeader) {
        String operation = "webdav-move";
        String normalizedSource = requireMutableWebDavPath(sourcePath, operation);
        String normalizedDestination = requireMutableWebDavPath(destinationPath, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedSource, ifHeader, operation);
        enforceUnlocked(normalizedDestination, ifHeader, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedSource, "MOVE", "attempted");
        try {
            FilesProviderPort adapter = configuredAdapter(operation);
            VersionedFileItem source = existingVersionedItem(adapter, normalizedSource, operation, false);
            if (source == null) {
                throw new ApiErrorException(
                        HttpStatus.NOT_FOUND,
                        "file-not-found",
                        "The requested file or folder was not found.",
                        Map.of("module", "files", "operation", operation));
            }
            enforcePreconditions(source, ifMatch, null, operation);
            VersionedFileItem destination = existingVersionedItem(adapter, normalizedDestination, operation, true);
            if (destination != null && !overwrite) {
                throw preconditionFailed(operation, "Overwrite is false and the destination already exists.");
            }
            if (destination == null) {
                requireParentCollection(adapter, normalizedDestination, operation);
            }
            FileObject moved = adapter.move(new FilePath(normalizedSource), new FilePath(normalizedDestination), overwrite);
            VersionedFileItem updated = firstNonNull(
                    existingVersionedItem(adapter, normalizedDestination, operation, false),
                    versioned(moved, FileVersion.unknown()));
            webDavLocks.remove(normalizedSource);
            publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedDestination, "MOVE", "completed");
            return new WebDavMutationResult(toResponse(updated.item()), etag(updated), destination == null);
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        } catch (UnsupportedOperationException exception) {
            throw unsupportedWebDavMutation(operation, "MOVE");
        }
    }

    public WebDavLockResult lockWebDavPath(String path, String ifHeader) {
        String operation = "webdav-lock";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedPath, ifHeader, operation);
        String token = "opaquelocktoken:" + UUID.randomUUID();
        webDavLocks.put(normalizedPath, new WebDavLockState(normalizedPath, token, principal.principalRef(), Instant.now().plusSeconds(3600)));
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "LOCK", "completed");
        return new WebDavLockResult(normalizedPath, token, 3600);
    }

    public void unlockWebDavPath(String path, String lockTokenHeader) {
        String operation = "webdav-unlock";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        WebDavLockState state = webDavLocks.get(normalizedPath);
        if (state == null || !lockTokenMatches(state, lockTokenHeader)) {
            throw locked(operation, normalizedPath);
        }
        webDavLocks.remove(normalizedPath);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "UNLOCK", "completed");
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
                "/api/files/client-setup/credentials",
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
                        "OpenAPI /api/files/readiness, native-provider-setup, and client-setup/credentials for discovery/status/revoke control plane",
                        "WebDAV writes use Weave ETag preconditions, support-safe conflict/storage errors, and mutation audit"),
                List.of(
                        "native-extension-implementation",
                        "device-credential-authenticator",
                        "physical-device-provider-proof"));
    }

    public FileSetupCredentialListResponse setupCredentials() {
        PrincipalContext principal = requireContextPermission(ContextPermission.VIEW, "list-file-setup-credentials");
        return new FileSetupCredentialListResponse(deviceCredentialService.list("files", principal.principalRef()).stream()
                .map(credential -> fileCredentialResponse(credential, null))
                .toList());
    }

    public FileSetupCredentialResponse createSetupCredential(FileSetupCredentialRequest request) {
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, "create-file-setup-credential");
        var issued = deviceCredentialService.issue(
                "files",
                principal.tenantId(),
                principal.principalRef(),
                principal.subject(),
                principal.username(),
                request.clientType(),
                request.label(),
                Set.of("files.read", "files.upload"));
        publishCredentialAudit(
                AuditAction.FILES_DEVICE_CREDENTIAL_ISSUED,
                principal,
                issued.credential().credentialId(),
                "issued");
        return fileCredentialResponse(issued.credential(), issued.secret());
    }

    public FileSetupCredentialResponse requireActiveSetupCredential(String credentialId) {
        PrincipalContext principal = requireContextPermission(ContextPermission.VIEW, "verify-file-setup-credential");
        DeviceCredential credential;
        try {
            credential = deviceCredentialService.requireOwned("files", credentialId, principal.principalRef());
        } catch (DeviceCredentialException exception) {
            throw setupCredentialNotFound("verify-file-setup-credential");
        }
        if (!credential.activeAt(Instant.now())) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "files-setup-credential-revoked",
                    "Files setup credential is revoked or inactive.",
                    Map.of(
                            "module", "files",
                            "operation", "verify-file-setup-credential",
                            "webDavFacadePath", "/dav/files",
                            "diagnosticsRedacted", true));
        }
        return fileCredentialResponse(credential, null);
    }

    public FileSetupCredentialResponse revokeSetupCredential(String credentialId) {
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, "revoke-file-setup-credential");
        DeviceCredential revoked;
        try {
            revoked = deviceCredentialService.revoke("files", credentialId, principal.principalRef());
        } catch (DeviceCredentialException exception) {
            throw setupCredentialNotFound("revoke-file-setup-credential");
        }
        publishCredentialAudit(AuditAction.FILES_DEVICE_CREDENTIAL_REVOKED, principal, credentialId, "revoked");
        return fileCredentialResponse(revoked, null);
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
        if ("weave-mcp-server".equals(jwt.getClaimAsString("azp"))) {
            return requireWorkloadContextPermission(jwt, permission, operation);
        }
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

    private PrincipalContext requireWorkloadContextPermission(
            Jwt jwt,
            ContextPermission permission,
            String operation) {
        if (permission != ContextPermission.VIEW
                || mcpWorkloadAuthorizationService == null
                || mcpExchangedTokenPolicy == null) {
            throw invalidAuthentication(operation, "workload authorization is unavailable");
        }
        WeaverWorkloadPrincipal workload;
        try {
            workload = mcpWorkloadAuthorizationService.authorize(
                    mcpExchangedTokenPolicy.resolve(jwt));
        } catch (McpWorkloadAuthorizationException denied) {
            throw new ApiErrorException(
                    denied.authorityUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.FORBIDDEN,
                    denied.authorityUnavailable()
                            ? "mcp-workload-authority-unavailable"
                            : "mcp-workload-files-forbidden",
                    denied.authorityUnavailable()
                            ? "The MCP workload authority is temporarily unavailable."
                            : "The MCP workload has no current Files authorization.",
                    Map.of("module", "files", "operation", operation));
        }
        if (!workload.scopes().contains("files.read")
                || !workload.visibleToolClasses().contains("files.read")) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "mcp-workload-files-forbidden",
                    "The MCP workload has no current Files authorization.",
                    Map.of("module", "files", "operation", operation));
        }
        String memberSubject = workload.memberBinding().subject();
        PrincipalContext principal = new PrincipalContext(
                workload.organizationRef(),
                contextAuthorizationProperties.principalRef(memberSubject),
                memberSubject,
                workload.personRef(),
                new WorkloadAuditContext(
                        workload.issuer(),
                        workload.workloadSubject(),
                        workload.workloadClientId(),
                        workload.mcpEdgeClientId(),
                        workload.cellRef(),
                        workload.personRef()));
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                principal.tenantId(),
                DEFAULT_CONTEXT_ID,
                principal.principalRef(),
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
        return principal;
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
        String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
        return new PrincipalContext(
                jwtTenantId(jwt, operation),
                jwtPrincipalRef(jwt, operation),
                jwt.getSubject(),
                username,
                null);
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

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private record PrincipalContext(
            String tenantId,
            String principalRef,
            String subject,
            String username,
            WorkloadAuditContext workload) {
    }

    private record WorkloadAuditContext(
            String issuer,
            String subject,
            String workloadClientId,
            String mcpEdgeClientId,
            String cellRef,
            String personRef) {
    }

    private record SearchNode(String path, int depth) {
    }

    private record WebDavLockState(String path, String token, String principalRef, Instant expiresAt) {
    }

    private FilesProviderPort configuredAdapter(String operation) {
        if (filesProviderPort == null || !filesProviderPort.configured()) {
            throw adapterNotConfigured(operation);
        }
        return filesProviderPort;
    }

    private ApiErrorException adapterNotConfigured(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-storage-not-configured",
                "Files facade is available, but file storage is not configured yet.",
                Map.of("module", "files", "operation", operation));
    }

    private void enforceUnlocked(String path, String ifHeader, String operation) {
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
        Instant now = Instant.now();
        webDavLocks.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        WebDavLockState matchingLock = webDavLocks.values().stream()
                .filter(lock -> lockApplies(lock.path(), normalizedPath))
                .findFirst()
                .orElse(null);
        if (matchingLock == null || lockTokenMatches(matchingLock, ifHeader)) {
            return;
        }
        throw locked(operation, normalizedPath);
    }

    private boolean lockApplies(String lockedPath, String requestPath) {
        String locked = FilePathCodec.normalizeProductPath(lockedPath);
        String request = FilePathCodec.normalizeProductPath(requestPath);
        return request.equals(locked)
                || request.startsWith(locked.endsWith("/") ? locked : locked + "/")
                || locked.startsWith(request.endsWith("/") ? request : request + "/");
    }

    private boolean lockTokenMatches(WebDavLockState state, String headerValue) {
        if (state == null || headerValue == null || headerValue.isBlank()) {
            return false;
        }
        String normalizedHeader = headerValue.replace("<", "").replace(">", "");
        return normalizedHeader.contains(state.token());
    }

    private ApiErrorException locked(String operation, String path) {
        return new ApiErrorException(
                HttpStatus.LOCKED,
                "files-locked",
                "The file operation conflicts with an active WebDAV lock.",
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "path", FilePathCodec.normalizeProductPath(path),
                        "webDavFacadePath", "/dav/files",
                        "openApiDataPlaneUsed", false,
                        "diagnosticsRedacted", true));
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

    private ApiErrorException setupCredentialNotFound(String operation) {
        return new ApiErrorException(
                HttpStatus.NOT_FOUND,
                "files-setup-credential-not-found",
                "Files setup credential was not found.",
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
    }

    private FileSetupCredentialResponse fileCredentialResponse(DeviceCredential credential, String secret) {
        boolean active = credential.activeAt(Instant.now());
        String state = credential.revokedAt() != null ? "revoked" : active ? "active" : "expired";
        return new FileSetupCredentialResponse(
                credential.credentialId(),
                state,
                credential.principalRef(),
                credential.clientType(),
                credential.label(),
                OffsetDateTime.ofInstant(credential.issuedAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(credential.expiresAt(), ZoneOffset.UTC),
                credential.revokedAt() == null
                        ? null
                        : OffsetDateTime.ofInstant(credential.revokedAt(), ZoneOffset.UTC),
                secret != null,
                credential.credentialId(),
                secret,
                "/dav/files",
                active ? List.of("DELETE /api/files/client-setup/credentials/" + credential.credentialId()) : List.of());
    }

    private void publishCredentialAudit(
            AuditAction action,
            PrincipalContext principal,
            String credentialId,
            String result) {
        auditEventPublisher.publish(new AuditEvent(
                principal.tenantId(),
                DEFAULT_CONTEXT_ID,
                principal.principalRef(),
                "files:device-credential",
                action,
                Instant.now(),
                "files-device-credential:" + UUID.randomUUID(),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "domain", "files",
                        "credentialId", credentialId,
                        "result", result,
                        "webDavFacadePath", "/dav/files",
                        "secretMaterialReturned", false,
                        "supportSafe", true)));
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

    private ApiErrorException unsupportedWebDavMutation(String operation, String method) {
        return new ApiErrorException(
                HttpStatus.NOT_IMPLEMENTED,
                "webdav-method-not-implemented",
                "Weave Files WebDAV does not implement " + method + " for the configured storage adapter.",
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "webDavFacadePath", "/dav/files",
                        "openApiDataPlaneUsed", false,
                        "diagnosticsRedacted", true));
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

    private void publishWorkloadReadAudit(
            PrincipalContext principal,
            String tool,
            String objectReference,
            String result,
            int matchCount) {
        WorkloadAuditContext workload = principal.workload();
        if (workload == null) {
            return;
        }
        auditEventPublisher.publish(new AuditEvent(
                principal.tenantId(),
                DEFAULT_CONTEXT_ID,
                principal.principalRef(),
                "files:mcp",
                AuditAction.WEAVER_TOOL_INVOCATION_RECORDED,
                Instant.now(),
                "files-mcp-read:" + UUID.randomUUID(),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "domain", "files",
                        "tool", tool,
                        "workloadSubjectSha256", sha256(workload.issuer() + "\u0000" + workload.subject()),
                        "workloadClientId", workload.workloadClientId(),
                        "mcpEdgeClientId", workload.mcpEdgeClientId(),
                        "cellRef", workload.cellRef(),
                        "personRef", workload.personRef(),
                        "providerBindingKey", "files.default",
                        "objectRefSha256", sha256(objectReference == null ? "/" : objectReference),
                        "result", result + ":" + matchCount)));
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

    private FileObject existingItem(
            FilesProviderPort adapter,
            String path,
            String operation,
            boolean missingParentAsConflict) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        if ("/".equals(normalized)) {
            return new FileObject(new FileId("files:root"), new FilePath("/"), Kind.COLLECTION, 0, null, null, false);
        }
        try {
            return adapter.find(new FilePath(normalized)).map(VersionedFile::item).orElse(null);
        } catch (ApiErrorException exception) {
            if (missingParentAsConflict && "file-not-found".equals(exception.code())) {
                throw fileConflict(operation, normalized, "The parent collection does not exist.");
            }
            throw exception;
        }
    }

    private VersionedFileItem existingVersionedItem(
            FilesProviderPort adapter,
            String path,
            String operation,
            boolean missingParentAsConflict) {
        try {
            return adapter.find(new FilePath(path))
                    .map(item -> versioned(item.item(), item.version()))
                    .orElse(null);
        } catch (ApiErrorException exception) {
            if (missingParentAsConflict && "file-not-found".equals(exception.code())) {
                throw fileConflict(operation, path, "The parent collection does not exist.");
            }
            throw exception;
        }
    }

    private void requireParentCollection(
            FilesProviderPort adapter,
            String path,
            String operation) {
        String parent = parentPath(path);
        VersionedFileItem parentItem = existingVersionedItem(adapter, parent, operation, false);
        if (parentItem == null || parentItem.item().kind() != Kind.COLLECTION) {
            throw fileConflict(operation, path, "The parent collection does not exist.");
        }
    }

    private VersionedFileItem versioned(FileObject item, FileVersion version) {
        return new VersionedFileItem(item, version == null ? FileVersion.unknown() : version);
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
        return etag(versionedItem.item(), versionedItem.version());
    }

    private WebDavPropfindResource webDavResource(FileObject item, FileVersion version) {
        return new WebDavPropfindResource(toResponse(item), etag(item, version));
    }

    private String etag(FileObject item, FileVersion version) {
        String material = String.join("|",
                item.path().value(),
                item.kind().name(),
                String.valueOf(item.size()),
                timestamp(item.modifiedAt()),
                item.mediaType() == null ? "" : item.mediaType(),
                version == null || !version.known() ? "" : version.value());
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

    private String timestamp(Instant value) {
        return value == null ? "" : value.toString();
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

    private FileListResponse toResponse(FileListing listing) {
        return new FileListResponse(
                listing.requestedPath().value(),
                listing.children().stream().map(this::toResponse).toList(),
                toResponse(listing.quota()));
    }

    private FileItemResponse toResponse(FileObject item) {
        return new FileItemResponse(
                item.id().value(),
                item.name(),
                item.path().value(),
                item.kind() == Kind.COLLECTION ? "folder" : "file",
                item.mediaType(),
                item.kind() == Kind.COLLECTION ? null : item.size(),
                item.modifiedAt() == null ? null : OffsetDateTime.ofInstant(item.modifiedAt(), ZoneOffset.UTC),
                item.kind() == Kind.FILE);
    }

    private com.massimotter.weave.backend.model.files.FileQuotaResponse toResponse(FileQuota quota) {
        if (quota == null || (quota.usedBytes() == null && quota.availableBytes() == null)) {
            return null;
        }
        Long total = quota.usedBytes() != null && quota.availableBytes() != null
                ? quota.usedBytes() + quota.availableBytes()
                : null;
        return new com.massimotter.weave.backend.model.files.FileQuotaResponse(quota.usedBytes(), total);
    }

    private VersionedFileItem firstNonNull(VersionedFileItem primary, VersionedFileItem fallback) {
        return primary == null ? fallback : primary;
    }

    private record VersionedFileItem(FileObject item, FileVersion version) {
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
            case "files-locked" -> "The file operation conflicts with an active WebDAV lock.";
            case "files-quota-exceeded" -> "There is not enough storage available for this file operation.";
            default -> "The files request could not be completed.";
        };
    }
}
