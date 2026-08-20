package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.agentruntime.domain.WeaverWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.port.McpWorkloadAuthorizationException;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.files.application.FilesEtags;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesLockService.FileLockedException;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.Command;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.NativePinnedMutation;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.PinnedMutation;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.PreparedMutation;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.CorruptLockOperationException;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.LockAuthorizationDeniedException;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.LockResult;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.TerminalLockOperationException;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CorruptMutationStateException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileListing;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileQuota;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.LockConflictException;
import com.massimotter.weave.backend.files.port.FilesStreamingCapabilityProfile;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Ingress;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.EntityTagCondition;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort.NativeLockMove;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort.NativeResult;
import com.massimotter.weave.backend.service.files.NativeFilesOperationAudit;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileNativeProviderOptionResponse;
import com.massimotter.weave.backend.model.files.FileNativeProviderSetupResponse;
import com.massimotter.weave.backend.model.files.FileSetupCredentialListResponse;
import com.massimotter.weave.backend.model.files.FileSetupCredentialRequest;
import com.massimotter.weave.backend.model.files.FileSetupCredentialResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.service.files.FilePathCodec;
import com.massimotter.weave.backend.service.files.WebDavFileRead;
import com.massimotter.weave.backend.service.files.WebDavPropfindListing;
import com.massimotter.weave.backend.service.files.WebDavPropfindResource;
import com.massimotter.weave.backend.service.files.WebDavLockResult;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import com.massimotter.weave.backend.service.files.WebDavPutRequest;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest;
import com.massimotter.weave.backend.service.files.WebDavSearchResult;
import com.massimotter.weave.backend.security.device.DeviceCredential;
import com.massimotter.weave.backend.security.device.DeviceCredentialException;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FilesFacadeService {

    private static final String DEFAULT_CONTEXT_ID = "workspace-default";
    private static final Logger LOGGER = LoggerFactory.getLogger(FilesFacadeService.class);

    private final FilesProviderPort filesProviderPort;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final OrganizationIdentityContextResolver identityContexts;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final AuditEventPublisher auditEventPublisher;
    private final DeviceCredentialService deviceCredentialService;
    private final FilesLockService filesLockService;
    private final FilesMutationIntentService filesMutationIntentService;
    private final NativeFilesLockRepository nativeFilesLockRepository;
    private final McpWorkloadAuthorizationService mcpWorkloadAuthorizationService;
    private final McpExchangedTokenPolicy mcpExchangedTokenPolicy;

    @Autowired
    public FilesFacadeService(
            ObjectProvider<FilesProviderPort> filesProviderPortProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            OrganizationIdentityContextResolver identityContexts,
            WorkspaceCapabilityService workspaceCapabilityService,
            DeviceCredentialService deviceCredentialService,
            AuditEventPublisher auditEventPublisher,
            FilesLockService filesLockService,
            FilesMutationIntentService filesMutationIntentService,
            NativeFilesLockRepository nativeFilesLockRepository,
            ObjectProvider<McpWorkloadAuthorizationService> mcpWorkloadAuthorizationServiceProvider,
            ObjectProvider<McpExchangedTokenPolicy> mcpExchangedTokenPolicyProvider) {
        this(
                filesProviderPortProvider,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                identityContexts,
                workspaceCapabilityService,
                deviceCredentialService,
                auditEventPublisher,
                filesLockService,
                filesMutationIntentService,
                nativeFilesLockRepository,
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
            FilesLockService filesLockService,
            FilesMutationIntentService filesMutationIntentService,
            McpWorkloadAuthorizationService mcpWorkloadAuthorizationService,
            McpExchangedTokenPolicy mcpExchangedTokenPolicy) {
        this(
                filesProviderPortProvider,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                OrganizationIdentityContextResolver.configured(contextAuthorizationProperties),
                workspaceCapabilityService,
                deviceCredentialService,
                auditEventPublisher,
                filesLockService,
                filesMutationIntentService,
                null,
                mcpWorkloadAuthorizationService,
                mcpExchangedTokenPolicy);
    }

    FilesFacadeService(
            ObjectProvider<FilesProviderPort> filesProviderPortProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            OrganizationIdentityContextResolver identityContexts,
            WorkspaceCapabilityService workspaceCapabilityService,
            DeviceCredentialService deviceCredentialService,
            AuditEventPublisher auditEventPublisher,
            FilesLockService filesLockService,
            FilesMutationIntentService filesMutationIntentService,
            McpWorkloadAuthorizationService mcpWorkloadAuthorizationService,
            McpExchangedTokenPolicy mcpExchangedTokenPolicy) {
        this(
                filesProviderPortProvider,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                identityContexts,
                workspaceCapabilityService,
                deviceCredentialService,
                auditEventPublisher,
                filesLockService,
                filesMutationIntentService,
                null,
                mcpWorkloadAuthorizationService,
                mcpExchangedTokenPolicy);
    }

    FilesFacadeService(
            ObjectProvider<FilesProviderPort> filesProviderPortProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            OrganizationIdentityContextResolver identityContexts,
            WorkspaceCapabilityService workspaceCapabilityService,
            DeviceCredentialService deviceCredentialService,
            AuditEventPublisher auditEventPublisher,
            FilesLockService filesLockService,
            FilesMutationIntentService filesMutationIntentService,
            NativeFilesLockRepository nativeFilesLockRepository,
            McpWorkloadAuthorizationService mcpWorkloadAuthorizationService,
            McpExchangedTokenPolicy mcpExchangedTokenPolicy) {
        this.filesProviderPort = filesProviderPortProvider.getIfAvailable();
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.identityContexts = Objects.requireNonNull(identityContexts, "identityContexts");
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.deviceCredentialService = deviceCredentialService;
        this.auditEventPublisher = auditEventPublisher;
        this.filesLockService = filesLockService;
        this.filesMutationIntentService = filesMutationIntentService;
        this.nativeFilesLockRepository = nativeFilesLockRepository;
        this.mcpWorkloadAuthorizationService = mcpWorkloadAuthorizationService;
        this.mcpExchangedTokenPolicy = mcpExchangedTokenPolicy;
    }

    public FilesFacadeService(
            ObjectProvider<FilesProviderPort> filesProviderPortProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            DeviceCredentialService deviceCredentialService,
            AuditEventPublisher auditEventPublisher) {
        this(filesProviderPortProvider, contextAuthorizationPort, contextAuthorizationProperties,
                workspaceCapabilityService, deviceCredentialService, auditEventPublisher, null, null,
                (McpWorkloadAuthorizationService) null, (McpExchangedTokenPolicy) null);
    }

    public FilesFacadeService(
            ObjectProvider<FilesProviderPort> filesProviderPortProvider,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            DeviceCredentialService deviceCredentialService,
            AuditEventPublisher auditEventPublisher,
            FilesLockService filesLockService) {
        this(filesProviderPortProvider, contextAuthorizationPort, contextAuthorizationProperties,
                workspaceCapabilityService, deviceCredentialService, auditEventPublisher, filesLockService, null,
                (McpWorkloadAuthorizationService) null, (McpExchangedTokenPolicy) null);
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
        this(
                filesProviderPortProvider,
                contextAuthorizationPort,
                contextAuthorizationProperties,
                workspaceCapabilityService,
                deviceCredentialService,
                auditEventPublisher,
                null,
                null,
                mcpWorkloadAuthorizationService,
                mcpExchangedTokenPolicy);
    }

    public FileListResponse list(String path) {
        PrincipalContext principal = requireContextPermission(ContextPermission.VIEW, "list-files");
        try {
            return toResponse(configuredAdapter("list-files", principal).list(new FilePath(path)).listing());
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, "list-files");
        }
    }

    public WebDavPropfindListing webDavPropfind(String path) {
        String operation = "webdav-propfind";
        PrincipalContext principal = requireContextPermission(ContextPermission.VIEW, operation);
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
        try {
            VersionedListing versionedListing = configuredAdapter(operation, principal).list(new FilePath(normalizedPath));
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
            FilesProviderPort adapter = configuredAdapter(operation, principal);
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

    public WebDavFileRead openWebDavPath(String path) {
        String operation = "webdav-get";
        PrincipalContext principal = requireContextPermission(ContextPermission.VIEW, operation);
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
        try {
            FilesProviderPort adapter = configuredAdapter(operation, principal);
            FilesStreamingContentPort streaming = requireStreamingContent(adapter, operation);
            var read = streaming.inspect(new FilePath(normalizedPath));
            return new WebDavFileRead(
                    read.item().name(),
                    read.headers().contentLength(),
                    read.headers().contentType(),
                    read.headers().strongEtag(),
                    read.headers().cacheControl(),
                    streaming.contentProfile().maximumContentBytes(),
                    () -> streaming.verify(read));
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
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
            WebDavPutRequest request,
            String ifMatch,
            String ifNoneMatch,
            String ifHeader,
            String idempotencyKey) {
        String operation = "webdav-put";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        EntityTagCondition matchCondition = parseEntityTagCondition(ifMatch, operation, "If-Match");
        EntityTagCondition noneMatchCondition = parseEntityTagCondition(ifNoneMatch, operation, "If-None-Match");
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedPath, ifHeader, operation, principal);
        FilesProviderPort scopedAdapter = configuredAdapter(operation, principal);
        FilesStreamingContentPort streaming = requireStreamingContent(scopedAdapter, operation);
        requireDurableStreamingWrite(operation);
        PutFraming framing = validatePutFraming(request, streaming, operation);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "PUT", "attempted");
        NativePlanCheck planCheck = adapter -> {
            VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, true);
            enforcePreconditions(existing, matchCondition, noneMatchCondition, operation);
            if (existing != null && existing.item().kind() != Kind.FILE) {
                throw fileConflict(operation, normalizedPath, "PUT cannot replace a collection.");
            }
            if (existing == null) {
                requireParentCollection(adapter, normalizedPath, operation);
            }
        };
        // Reject an already-stale request before consuming any request-body bytes. The same check is
        // repeated against the pinned provider binding immediately before a new Tx1 seals its
        // fences. An existing explicit-key operation replays its immutable plan/result instead of
        // evaluating today's target state; canonical-argument equivalence is checked after ingress.
        boolean knownReplay;
        try {
            knownReplay = filesMutationIntentService != null
                    && filesMutationIntentService.hasExistingOperation(principal.tenantId(), idempotencyKey);
        } catch (FilesMutationIntentService.InvalidIdempotencyKeyException invalid) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "files-idempotency-key-invalid",
                    "Idempotency-Key must contain between 16 and 128 characters.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        }
        if (!knownReplay) {
            planCheck.validate(scopedAdapter);
        }
        try (Ingress ingress = streaming.receive(
                framing.declaredLength(),
                framing.mediaType(),
                request.requestBody())) {
            var content = ingress.content();
            String arguments = String.join("\n",
                    normalizedPath,
                    content.mediaType(),
                    Long.toString(content.sizeBytes()),
                    content.sha256Digest(),
                    matchCondition.canonicalValue(),
                    noneMatchCondition.canonicalValue(),
                    lockTokenRef(ifHeader));
            return executeMutation(
                    idempotencyKey,
                    principal,
                    operation,
                    arguments,
                    List.of(
                            "file-path:" + FilesMutationIntentService.digest(normalizedPath),
                            lockTokenRef(ifHeader)),
                    new NativeFilesDurableMutationPort.Put(
                            new FilePath(normalizedPath),
                            content,
                            matchCondition,
                            noneMatchCondition),
                    ingress,
                    planCheck,
                    null,
                    result -> {
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedPath, "PUT", "completed");
                        return webDavMutationResult(result, operation);
                    },
                    adapter -> {
                        throw streamingNotSupported(operation);
                    },
                    adapter -> {
                        throw streamingNotSupported(operation);
                    },
                    result -> result.item().id() + "\n" + result.item().path() + "\n" + result.etag());
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public WebDavMutationResult createWebDavFolder(String path, String ifMatch, String ifNoneMatch) {
        return createWebDavFolder(path, ifMatch, ifNoneMatch, null);
    }

    public WebDavMutationResult createWebDavFolder(String path, String ifMatch, String ifNoneMatch, String ifHeader) {
        return createWebDavFolder(path, ifMatch, ifNoneMatch, ifHeader, null);
    }

    public WebDavMutationResult createWebDavFolder(
            String path, String ifMatch, String ifNoneMatch, String ifHeader, String idempotencyKey) {
        String operation = "webdav-mkcol";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        EntityTagCondition matchCondition = parseEntityTagCondition(ifMatch, operation, "If-Match");
        EntityTagCondition noneMatchCondition = parseEntityTagCondition(ifNoneMatch, operation, "If-None-Match");
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedPath, ifHeader, operation, principal);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "MKCOL", "attempted");
        try {
            return executeMutation(
                    idempotencyKey, principal, operation,
                    String.join("\n", normalizedPath, matchCondition.canonicalValue(),
                            noneMatchCondition.canonicalValue(), lockTokenRef(ifHeader)),
                    List.of(
                            "file-path:" + FilesMutationIntentService.digest(normalizedPath),
                            lockTokenRef(ifHeader)),
                    new NativeFilesDurableMutationPort.MakeCollection(
                            new FilePath(normalizedPath), matchCondition, noneMatchCondition),
                    adapter -> {
                        VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, true);
                        enforcePreconditions(existing, matchCondition, noneMatchCondition, operation);
                        if (existing != null) {
                            throw fileConflict(operation, normalizedPath,
                                    "A collection or file already exists at this path.");
                        }
                        requireParentCollection(adapter, normalizedPath, operation);
                    },
                    null,
                    result -> {
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedPath, "MKCOL", "completed");
                        return webDavMutationResult(result, operation);
                    },
                    adapter -> {
                        VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, true);
                        enforcePreconditions(existing, matchCondition, noneMatchCondition, operation);
                        if (existing != null) {
                            throw fileConflict(operation, normalizedPath,
                                    "A collection or file already exists at this path.");
                        }
                        requireParentCollection(adapter, normalizedPath, operation);
                        FileObject stored = adapter.createCollection(new FilePath(normalizedPath));
                        VersionedFileItem updated = firstNonNull(
                                existingVersionedItem(adapter, normalizedPath, operation, false),
                                versioned(stored, FileVersion.unknown()));
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedPath, "MKCOL", "completed");
                        return new WebDavMutationResult(toResponse(updated.item()), etag(updated), true);
                    },
                    adapter -> reconcileExisting(adapter, normalizedPath, Kind.COLLECTION, operation),
                    result -> result.item().id() + "\n" + result.item().path() + "\n" + result.etag());
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        }
    }

    public void deleteWebDavPath(String path, String ifMatch) {
        deleteWebDavPath(path, ifMatch, null, null, null);
    }

    public void deleteWebDavPath(String path, String ifMatch, String ifHeader) {
        deleteWebDavPath(path, ifMatch, null, ifHeader, null);
    }

    public void deleteWebDavPath(String path, String ifMatch, String ifHeader, String idempotencyKey) {
        deleteWebDavPath(path, ifMatch, null, ifHeader, idempotencyKey);
    }

    public void deleteWebDavPath(
            String path,
            String ifMatch,
            String ifNoneMatch,
            String ifHeader,
            String idempotencyKey) {
        String operation = "webdav-delete";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        EntityTagCondition matchCondition = parseEntityTagCondition(ifMatch, operation, "If-Match");
        EntityTagCondition noneMatchCondition = parseEntityTagCondition(ifNoneMatch, operation, "If-None-Match");
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedPath, ifHeader, operation, principal);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedPath, "DELETE", "attempted");
        try {
            executeMutation(
                    idempotencyKey, principal, operation,
                    String.join("\n", normalizedPath, matchCondition.canonicalValue(),
                            noneMatchCondition.canonicalValue(), lockTokenRef(ifHeader)),
                    List.of(
                            "file-path:" + FilesMutationIntentService.digest(normalizedPath),
                            lockTokenRef(ifHeader)),
                    new NativeFilesDurableMutationPort.Delete(
                            new FilePath(normalizedPath), FileVersion.unknown(),
                            matchCondition, noneMatchCondition),
                    adapter -> {
                        VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, false);
                        if (existing == null) {
                            throw new ApiErrorException(HttpStatus.NOT_FOUND, "file-not-found",
                                    "The requested file or folder was not found.",
                                    Map.of("module", "files", "operation", operation));
                        }
                        enforcePreconditions(existing, matchCondition, noneMatchCondition, operation);
                    },
                    null,
                    result -> {
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedPath, "DELETE", "completed");
                        return Boolean.TRUE;
                    },
                    adapter -> {
                        VersionedFileItem existing = existingVersionedItem(adapter, normalizedPath, operation, false);
                        if (existing == null) {
                            throw new ApiErrorException(HttpStatus.NOT_FOUND, "file-not-found",
                                    "The requested file or folder was not found.",
                                    Map.of("module", "files", "operation", operation));
                        }
                        enforcePreconditions(existing, matchCondition, noneMatchCondition, operation);
                        adapter.delete(new FilePath(normalizedPath), existing.version());
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedPath, "DELETE", "completed");
                        return Boolean.TRUE;
                    },
                    adapter -> reconcileDeleted(adapter, normalizedPath, operation),
                    result -> "deleted:" + normalizedPath);
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
        return copyWebDavPath(sourcePath, destinationPath, overwrite, ifMatch, null, ifHeader, null);
    }

    public WebDavMutationResult copyWebDavPath(
            String sourcePath,
            String destinationPath,
            boolean overwrite,
            String ifMatch,
            String ifHeader,
            String idempotencyKey) {
        return copyWebDavPath(
                sourcePath, destinationPath, overwrite, ifMatch, null, ifHeader, idempotencyKey);
    }

    public WebDavMutationResult copyWebDavPath(
            String sourcePath,
            String destinationPath,
            boolean overwrite,
            String ifMatch,
            String ifNoneMatch,
            String ifHeader,
            String idempotencyKey) {
        String operation = "webdav-copy";
        String normalizedSource = requireMutableWebDavPath(sourcePath, operation);
        String normalizedDestination = requireMutableWebDavPath(destinationPath, operation);
        EntityTagCondition matchCondition = parseEntityTagCondition(ifMatch, operation, "If-Match");
        EntityTagCondition noneMatchCondition = parseEntityTagCondition(ifNoneMatch, operation, "If-None-Match");
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedDestination, ifHeader, operation, principal);
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedSource, "COPY", "attempted");
        try {
            return executeMutation(
                    idempotencyKey, principal, operation,
                    String.join("\n", normalizedSource, normalizedDestination, Boolean.toString(overwrite),
                            matchCondition.canonicalValue(), noneMatchCondition.canonicalValue(),
                            lockTokenRef(ifHeader)),
                    List.of("file-path:" + FilesMutationIntentService.digest(normalizedSource),
                            "file-path:" + FilesMutationIntentService.digest(normalizedDestination),
                            lockTokenRef(ifHeader)),
                    new NativeFilesDurableMutationPort.Copy(
                            new FilePath(normalizedSource), new FilePath(normalizedDestination), overwrite,
                            matchCondition, noneMatchCondition),
                    adapter -> {
                        VersionedFileItem source = existingVersionedItem(adapter, normalizedSource, operation, false);
                        if (source == null) {
                            throw new ApiErrorException(HttpStatus.NOT_FOUND, "file-not-found",
                                    "The requested file or folder was not found.",
                                    Map.of("module", "files", "operation", operation));
                        }
                        enforcePreconditions(source, matchCondition, noneMatchCondition, operation);
                        VersionedFileItem destination = existingVersionedItem(
                                adapter, normalizedDestination, operation, true);
                        if (destination != null && !overwrite) {
                            throw preconditionFailed(operation,
                                    "Overwrite is false and the destination already exists.");
                        }
                        if (destination == null) {
                            requireParentCollection(adapter, normalizedDestination, operation);
                        }
                    },
                    null,
                    result -> {
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedDestination, "COPY", "completed");
                        return webDavMutationResult(result, operation);
                    },
                    adapter -> {
                        VersionedFileItem source = existingVersionedItem(adapter, normalizedSource, operation, false);
                        if (source == null) {
                            throw new ApiErrorException(HttpStatus.NOT_FOUND, "file-not-found",
                                    "The requested file or folder was not found.",
                                    Map.of("module", "files", "operation", operation));
                        }
                        enforcePreconditions(source, matchCondition, noneMatchCondition, operation);
                        VersionedFileItem destination = existingVersionedItem(
                                adapter, normalizedDestination, operation, true);
                        if (destination != null && !overwrite) {
                            throw preconditionFailed(operation,
                                    "Overwrite is false and the destination already exists.");
                        }
                        if (destination == null) {
                            requireParentCollection(adapter, normalizedDestination, operation);
                        }
                        FileObject copied = adapter.copy(
                                new FilePath(normalizedSource), new FilePath(normalizedDestination), overwrite);
                        VersionedFileItem updated = firstNonNull(
                                existingVersionedItem(adapter, normalizedDestination, operation, false),
                                versioned(copied, FileVersion.unknown()));
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedDestination, "COPY", "completed");
                        return new WebDavMutationResult(toResponse(updated.item()), etag(updated), destination == null);
                    },
                    adapter -> reconcileExisting(adapter, normalizedDestination, null, operation),
                    result -> result.item().id() + "\n" + result.item().path() + "\n" + result.etag());
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
        return moveWebDavPath(sourcePath, destinationPath, overwrite, ifMatch, null, ifHeader, null);
    }

    public WebDavMutationResult moveWebDavPath(
            String sourcePath,
            String destinationPath,
            boolean overwrite,
            String ifMatch,
            String ifHeader,
            String idempotencyKey) {
        return moveWebDavPath(
                sourcePath, destinationPath, overwrite, ifMatch, null, ifHeader, idempotencyKey);
    }

    public WebDavMutationResult moveWebDavPath(
            String sourcePath,
            String destinationPath,
            boolean overwrite,
            String ifMatch,
            String ifNoneMatch,
            String ifHeader,
            String idempotencyKey) {
        String operation = "webdav-move";
        String normalizedSource = requireMutableWebDavPath(sourcePath, operation);
        String normalizedDestination = requireMutableWebDavPath(destinationPath, operation);
        EntityTagCondition matchCondition = parseEntityTagCondition(ifMatch, operation, "If-Match");
        EntityTagCondition noneMatchCondition = parseEntityTagCondition(ifNoneMatch, operation, "If-None-Match");
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        enforceUnlocked(normalizedSource, ifHeader, operation, principal);
        enforceUnlocked(normalizedDestination, ifHeader, operation, principal);
        String moveToken = presentedLockToken(ifHeader);
        NativeLockMove nativeLockMove = moveToken == null
                ? null
                : new NativeLockMove(
                        new FilePath(normalizedSource),
                        new FilePath(normalizedDestination),
                        requiredLockService(operation).tokenDigest(moveToken),
                        principal.principalRef());
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED, operation, principal, normalizedSource, "MOVE", "attempted");
        try {
            return executeMutation(
                    idempotencyKey, principal, operation,
                    String.join("\n", normalizedSource, normalizedDestination, Boolean.toString(overwrite),
                            matchCondition.canonicalValue(), noneMatchCondition.canonicalValue(),
                            lockTokenRef(ifHeader)),
                    List.of("file-path:" + FilesMutationIntentService.digest(normalizedSource),
                            "file-path:" + FilesMutationIntentService.digest(normalizedDestination),
                            lockTokenRef(ifHeader)),
                    new NativeFilesDurableMutationPort.Move(
                            new FilePath(normalizedSource), new FilePath(normalizedDestination), overwrite,
                            matchCondition, noneMatchCondition),
                    adapter -> {
                        VersionedFileItem source = existingVersionedItem(adapter, normalizedSource, operation, false);
                        if (source == null) {
                            throw new ApiErrorException(HttpStatus.NOT_FOUND, "file-not-found",
                                    "The requested file or folder was not found.",
                                    Map.of("module", "files", "operation", operation));
                        }
                        enforcePreconditions(source, matchCondition, noneMatchCondition, operation);
                        VersionedFileItem destination = existingVersionedItem(
                                adapter, normalizedDestination, operation, true);
                        if (destination != null && !overwrite) {
                            throw preconditionFailed(operation,
                                    "Overwrite is false and the destination already exists.");
                        }
                        if (destination == null) {
                            requireParentCollection(adapter, normalizedDestination, operation);
                        }
                    },
                    nativeLockMove,
                    result -> {
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedDestination, "MOVE", "completed");
                        return webDavMutationResult(result, operation);
                    },
                    adapter -> {
                        VersionedFileItem source = existingVersionedItem(adapter, normalizedSource, operation, false);
                        if (source == null) {
                            throw new ApiErrorException(HttpStatus.NOT_FOUND, "file-not-found",
                                    "The requested file or folder was not found.",
                                    Map.of("module", "files", "operation", operation));
                        }
                        enforcePreconditions(source, matchCondition, noneMatchCondition, operation);
                        VersionedFileItem destination = existingVersionedItem(
                                adapter, normalizedDestination, operation, true);
                        if (destination != null && !overwrite) {
                            throw preconditionFailed(operation,
                                    "Overwrite is false and the destination already exists.");
                        }
                        if (destination == null) {
                            requireParentCollection(adapter, normalizedDestination, operation);
                        }
                        FileObject moved = adapter.move(
                                new FilePath(normalizedSource), new FilePath(normalizedDestination), overwrite);
                        VersionedFileItem updated = firstNonNull(
                                existingVersionedItem(adapter, normalizedDestination, operation, false),
                                versioned(moved, FileVersion.unknown()));
                        requiredLockService(operation).move(
                                principal.tenantId(), DEFAULT_CONTEXT_ID,
                                new FilePath(normalizedSource), new FilePath(normalizedDestination),
                                presentedLockToken(ifHeader), principal.principalRef());
                        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                                normalizedDestination, "MOVE", "completed");
                        return new WebDavMutationResult(toResponse(updated.item()), etag(updated), destination == null);
                    },
                    adapter -> reconcileMove(adapter, normalizedSource, normalizedDestination, operation),
                    result -> result.item().id() + "\n" + result.item().path() + "\n" + result.etag());
        } catch (ApiErrorException exception) {
            throw supportSafeStorageError(exception, operation);
        } catch (UnsupportedOperationException exception) {
            throw unsupportedWebDavMutation(operation, "MOVE");
        }
    }

    public WebDavLockResult lockWebDavPath(String path, String ifHeader) {
        return lockWebDavPath(path, ifHeader, null);
    }

    public WebDavLockResult lockWebDavPath(String path, String ifHeader, String idempotencyKey) {
        String operation = "webdav-lock";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        FilesProviderPort configuredRoot = configuredAdapter(operation);
        if (weaveNative(configuredRoot)) {
            return lockNativeWebDavPath(
                    normalizedPath,
                    presentedLockToken(ifHeader),
                    idempotencyKey,
                    operation,
                    principal,
                    configuredRoot);
        }
        enforceUnlocked(normalizedPath, ifHeader, operation, principal);
        try {
            return executeMutation(
                    idempotencyKey, principal, operation, normalizedPath,
                    List.of("file-path:" + FilesMutationIntentService.digest(normalizedPath)),
                    adapter -> {
                        try {
                            return lockResult(requiredLockService(operation).acquire(
                                    principal.tenantId(), DEFAULT_CONTEXT_ID, new FilePath(normalizedPath),
                                    principal.principalRef(), Duration.ofHours(1)),
                                    normalizedPath, operation, principal);
                        } catch (FileLockedException exception) {
                            throw locked(operation, normalizedPath);
                        }
                    },
                    adapter -> {
                        try {
                            return lockResult(requiredLockService(operation).refresh(
                                    principal.tenantId(), DEFAULT_CONTEXT_ID, new FilePath(normalizedPath),
                                    presentedLockToken(ifHeader), principal.principalRef()),
                                    normalizedPath, operation, principal);
                        } catch (FileLockedException exception) {
                            throw operationReconciliationRequired(operation, normalizedPath);
                        }
                    },
                    result -> normalizedPath + "\n" + FilesMutationIntentService.digest(result.token()));
        } catch (FileLockedException exception) {
            throw locked(operation, normalizedPath);
        }
    }

    public void unlockWebDavPath(String path, String lockTokenHeader) {
        unlockWebDavPath(path, lockTokenHeader, null);
    }

    public void unlockWebDavPath(String path, String lockTokenHeader, String idempotencyKey) {
        String operation = "webdav-unlock";
        String normalizedPath = requireMutableWebDavPath(path, operation);
        PrincipalContext principal = requireContextPermission(ContextPermission.EDIT, operation);
        FilesProviderPort configuredRoot = configuredAdapter(operation);
        if (weaveNative(configuredRoot)) {
            unlockNativeWebDavPath(
                    normalizedPath,
                    presentedLockToken(lockTokenHeader),
                    idempotencyKey,
                    operation,
                    principal,
                    configuredRoot);
            return;
        }
        try {
            executeMutation(
                    idempotencyKey, principal, operation,
                    normalizedPath + "\n" + FilesMutationIntentService.digest(
                            firstNonBlank(presentedLockToken(lockTokenHeader), "missing")),
                    List.of("file-path:" + FilesMutationIntentService.digest(normalizedPath)),
                    adapter -> {
                        try {
                            requiredLockService(operation).release(
                                    principal.tenantId(), DEFAULT_CONTEXT_ID, new FilePath(normalizedPath),
                                    presentedLockToken(lockTokenHeader), principal.principalRef());
                        } catch (FileLockedException exception) {
                            throw locked(operation, normalizedPath);
                        }
                        return Boolean.TRUE;
                    },
                    adapter -> {
                        if (!requiredLockService(operation).unlocked(
                                principal.tenantId(), DEFAULT_CONTEXT_ID, new FilePath(normalizedPath))) {
                            throw operationReconciliationRequired(operation, normalizedPath);
                        }
                        return Boolean.TRUE;
                    },
                    result -> "unlocked:" + normalizedPath);
        } catch (FileLockedException exception) {
            throw locked(operation, normalizedPath);
        }
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal, normalizedPath, "UNLOCK", "completed");
    }

    private WebDavLockResult lockNativeWebDavPath(
            String normalizedPath,
            String presentedToken,
            String idempotencyKey,
            String operation,
            PrincipalContext principal,
            FilesProviderPort adapter) {
        NativeFilesLockRepository locks = requiredNativeLockRepository(operation);
        String canonicalArguments = presentedToken == null
                ? normalizedPath + "\nacquire"
                : normalizedPath + "\nrefresh\n" + FilesMutationIntentService.digest(presentedToken);
        PreparedMutation prepared = prepareNativeLock(
                idempotencyKey,
                principal,
                operation,
                canonicalArguments,
                normalizedPath,
                adapter);
        String auditRef = publishNativeOperationIntentAudit(prepared.candidate());
        try {
            LockResult result = presentedToken == null
                    ? locks.acquire(
                            prepared.candidate(),
                            prepared.beginCommand(),
                            DEFAULT_CONTEXT_ID,
                            new FilePath(normalizedPath),
                            Duration.ofHours(1),
                            auditRef)
                    : locks.refresh(
                            prepared.candidate(),
                            prepared.beginCommand(),
                            DEFAULT_CONTEXT_ID,
                            new FilePath(normalizedPath),
                            presentedToken,
                            Duration.ofHours(1),
                            auditRef);
            return lockResult(result, normalizedPath, operation, principal);
        } catch (LockConflictException exception) {
            throw locked(operation, normalizedPath);
        } catch (LockAuthorizationDeniedException exception) {
            throw nativeLockAuthorizationDenied(operation);
        } catch (TerminalLockOperationException exception) {
            throw idempotencyKeyConflict(operation, prepared.candidate().operationRef());
        } catch (CorruptLockOperationException exception) {
            throw nativeLockCorrupt(operation);
        } catch (com.massimotter.weave.backend.operation.application.OperationIntentService.IdempotencyKeyConflictException exception) {
            throw idempotencyKeyConflict(operation, exception.operationRef());
        }
    }

    private void unlockNativeWebDavPath(
            String normalizedPath,
            String presentedToken,
            String idempotencyKey,
            String operation,
            PrincipalContext principal,
            FilesProviderPort adapter) {
        if (presentedToken == null) {
            throw locked(operation, normalizedPath);
        }
        PreparedMutation prepared = prepareNativeLock(
                idempotencyKey,
                principal,
                operation,
                normalizedPath + "\n" + FilesMutationIntentService.digest(presentedToken),
                normalizedPath,
                adapter);
        String auditRef = publishNativeOperationIntentAudit(prepared.candidate());
        try {
            requiredNativeLockRepository(operation).unlock(
                    prepared.candidate(),
                    prepared.beginCommand(),
                    DEFAULT_CONTEXT_ID,
                    new FilePath(normalizedPath),
                    presentedToken,
                    auditRef);
        } catch (LockConflictException exception) {
            throw locked(operation, normalizedPath);
        } catch (LockAuthorizationDeniedException exception) {
            throw nativeLockAuthorizationDenied(operation);
        } catch (TerminalLockOperationException exception) {
            throw idempotencyKeyConflict(operation, prepared.candidate().operationRef());
        } catch (CorruptLockOperationException exception) {
            throw nativeLockCorrupt(operation);
        } catch (com.massimotter.weave.backend.operation.application.OperationIntentService.IdempotencyKeyConflictException exception) {
            throw idempotencyKeyConflict(operation, exception.operationRef());
        }
        publishWriteAudit(
                AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                operation,
                principal,
                normalizedPath,
                "UNLOCK",
                "completed");
    }

    private PreparedMutation prepareNativeLock(
            String idempotencyKey,
            PrincipalContext principal,
            String operation,
            String canonicalArguments,
            String normalizedPath,
            FilesProviderPort adapter) {
        if (filesMutationIntentService == null) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-native-lock-intent-unavailable",
                    "The native Files lock intent authority is unavailable.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        }
        PreparedMutation prepared = null;
        try {
            prepared = filesMutationIntentService.prepare(new Command(
                    idempotencyKey,
                    principal.tenantId(),
                    principal.principalRef(),
                    principal.subject(),
                    operation,
                    canonicalArguments,
                    List.of("file-path:" + FilesMutationIntentService.digest(normalizedPath)),
                    principal.policyRevision(),
                    principal.entitlementRevision()));
            filesMutationIntentService.requireAdapter(
                    prepared,
                    adapter.conformanceProfile().adapterKey());
            return prepared;
        } catch (FilesMutationIntentService.ProviderBindingUnavailableException exception) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-provider-binding-unavailable",
                    "The active Files provider binding is unavailable.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        } catch (FilesMutationIntentService.InvalidIdempotencyKeyException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "files-idempotency-key-invalid",
                    "Idempotency-Key must contain between 16 and 128 characters.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        } catch (FilesMutationIntentService.PinnedAdapterMismatchException exception) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-provider-binding-mismatch",
                    "The configured Files adapter does not match the pinned provider binding.",
                    Map.of(
                            "module", "files",
                            "operation", operation,
                            "providerBindingRevision", prepared == null
                                    ? 0
                                    : prepared.binding().revision(),
                            "diagnosticsRedacted", true));
        } catch (com.massimotter.weave.backend.operation.application.OperationIntentService.IdempotencyKeyConflictException exception) {
            throw idempotencyKeyConflict(operation, exception.operationRef());
        }
    }

    private boolean weaveNative(FilesProviderPort adapter) {
        return adapter instanceof NativeFilesDurableMutationPort
                && adapter.conformanceProfile() != null
                && "weave-native".equals(adapter.conformanceProfile().adapterKey());
    }

    private NativeFilesLockRepository requiredNativeLockRepository(String operation) {
        if (nativeFilesLockRepository == null) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-native-lock-authority-unavailable",
                    "The native Files lock transaction authority is unavailable.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        }
        return nativeFilesLockRepository;
    }

    private ApiErrorException nativeLockCorrupt(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-native-lock-state-corrupt",
                "The native Files lock state is inconsistent.",
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
    }

    private ApiErrorException nativeMutationCorrupt(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-native-mutation-state-corrupt",
                "The native Files mutation state is inconsistent.",
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
    }

    private ApiErrorException nativeLockAuthorizationDenied(String operation) {
        return new ApiErrorException(
                HttpStatus.FORBIDDEN,
                "files-native-lock-authorization-denied",
                "Files authorization changed before the lock operation completed.",
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
    }

    private WebDavLockResult lockResult(
            FilesLockService.GrantedLock granted,
            String normalizedPath,
            String operation,
            PrincipalContext principal) {
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                normalizedPath, "LOCK", "completed");
        return new WebDavLockResult(normalizedPath, granted.token(),
                Math.toIntExact(Math.max(1, Duration.between(Instant.now(), granted.expiresAt()).toSeconds())));
    }

    private WebDavLockResult lockResult(
            LockResult granted,
            String normalizedPath,
            String operation,
            PrincipalContext principal) {
        publishWriteAudit(AuditAction.FILES_WEBDAV_WRITE_COMPLETED, operation, principal,
                normalizedPath, "LOCK", "completed");
        return new WebDavLockResult(normalizedPath, granted.token(),
                Math.toIntExact(Math.max(1, Duration.between(Instant.now(), granted.expiresAt()).toSeconds())));
    }

    public String etagFor(String path) {
        String operation = "webdav-etag";
        PrincipalContext principal = requireContextPermission(ContextPermission.VIEW, operation);
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
        try {
            VersionedFileItem item = existingVersionedItem(
                    configuredAdapter(operation, principal), normalizedPath, operation, false);
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
            LOGGER.warn("MCP workload authorization rejected reason={}", denied.reasonCode());
            throw new ApiErrorException(
                    denied.authorityUnavailable()
                            ? HttpStatus.SERVICE_UNAVAILABLE
                            : HttpStatus.FORBIDDEN,
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
                contextAuthorizationProperties.principalRef(workload.contextPrincipalClaim()),
                memberSubject,
                workload.personRef(),
                "runtime-profile:" + workload.runtimeProfileHash(),
                "runtime-entitlement:" + workload.entitlementRevision(),
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
        OrganizationIdentityContext identity = identityContexts.resolve(jwt);
        String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
        return new PrincipalContext(
                identity.organizationId(),
                jwtPrincipalRef(jwt, operation),
                identity.subject(),
                username,
                revisionClaim(jwt, "weave_policy_revision", "policy:unversioned"),
                revisionClaim(jwt, "weave_entitlement_revision", "entitlement:unversioned"),
                null);
    }

    private String revisionClaim(Jwt jwt, String claimName, String fallback) {
        Object value = jwt.getClaim(claimName);
        return value == null || value.toString().isBlank() ? fallback : claimName + ":" + value;
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
            String policyRevision,
            String entitlementRevision,
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

    private PutFraming validatePutFraming(
            WebDavPutRequest request,
            FilesStreamingContentPort streaming,
            String operation) {
        WebDavPutRequest required = Objects.requireNonNull(request, "request must not be null");
        Long declaredLength = parseContentLength(required.contentLengthFields(), operation);
        validateTransferEncoding(
                required.transferEncodingFields(),
                declaredLength != null,
                operation);
        String mediaType = parseContentType(required.contentTypeFields(), operation);
        validateContentEncoding(required.contentEncodingFields(), operation);
        if (declaredLength != null
                && declaredLength > streaming.contentProfile().maximumContentBytes()) {
            throw new ApiErrorException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "files-content-too-large",
                    "The Files representation exceeds the accepted content bound.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        }
        return new PutFraming(declaredLength, mediaType);
    }

    private Long parseContentLength(List<String> values, String operation) {
        if (values.size() > 1) {
            throw invalidPutRequest(operation);
        }
        if (values.isEmpty()) {
            return null;
        }
        String value = values.getFirst() == null ? "" : values.getFirst().trim();
        if (!value.matches("0|[1-9][0-9]*")) {
            throw invalidPutRequest(operation);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw invalidPutRequest(operation);
        }
    }

    private void validateTransferEncoding(
            List<String> values,
            boolean contentLengthSupplied,
            String operation) {
        if (values.size() > 1) {
            throw invalidPutRequest(operation);
        }
        if (values.isEmpty()) {
            return;
        }
        String value = values.getFirst() == null ? "" : values.getFirst().trim();
        if (contentLengthSupplied || !"chunked".equalsIgnoreCase(value)) {
            throw invalidPutRequest(operation);
        }
    }

    private String parseContentType(List<String> values, String operation) {
        if (values.size() > 1) {
            throw invalidPutRequest(operation);
        }
        if (values.isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        String value = values.getFirst() == null ? "" : values.getFirst().trim();
        if (value.isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw invalidPutRequest(operation);
        }
        try {
            MediaType.parseMediaType(value);
        } catch (IllegalArgumentException invalid) {
            throw invalidPutRequest(operation);
        }
        return value;
    }

    private void validateContentEncoding(List<String> values, String operation) {
        if (values.size() > 1) {
            throw invalidPutRequest(operation);
        }
        if (values.isEmpty()) {
            return;
        }
        String value = values.getFirst() == null ? "" : values.getFirst().trim();
        if (!"identity".equalsIgnoreCase(value)) {
            throw new ApiErrorException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "files-content-coding-unsupported",
                    "Files PUT accepts only the identity content coding.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        }
    }

    private ApiErrorException invalidPutRequest(String operation) {
        return new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "file-upload-invalid-request",
                "The Files PUT request framing is invalid.",
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
    }

    private FilesStreamingContentPort requireStreamingContent(
            FilesProviderPort adapter,
            String operation) {
        if (adapter instanceof FilesStreamingContentPort streaming) {
            if (FilesStreamingCapabilityProfile.observe(adapter, streaming).qualified()) {
                return streaming;
            }
        }
        throw streamingNotSupported(operation);
    }

    private ApiErrorException streamingNotSupported(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-streaming-not-supported",
                "The selected Files adapter has no verified bounded-content capability.",
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "webDavFacadePath", "/dav/files",
                        "openApiDataPlaneUsed", false,
                        "diagnosticsRedacted", true));
    }

    private void requireDurableStreamingWrite(String operation) {
        if (filesMutationIntentService == null
                || !(configuredAdapter(operation) instanceof NativeFilesDurableMutationPort)) {
            throw streamingNotSupported(operation);
        }
    }

    private ApiErrorException streamingCapacityUnavailable(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-streaming-capacity-unavailable",
                "Bounded Files content capacity is temporarily unavailable.",
                Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
    }

    private FilesProviderPort configuredAdapter(String operation) {
        if (filesProviderPort == null || !filesProviderPort.configured()) {
            throw adapterNotConfigured(operation);
        }
        return filesProviderPort;
    }

    private FilesProviderPort configuredAdapter(String operation, PrincipalContext principal) {
        return configuredAdapter(operation).scoped(new FilesRequestScope(
                principal.tenantId(), DEFAULT_CONTEXT_ID, 1));
    }

    private FilesProviderPort configuredAdapter(
            String operation, PrincipalContext principal, long providerBindingRevision) {
        return configuredAdapter(operation).scoped(new FilesRequestScope(
                principal.tenantId(), DEFAULT_CONTEXT_ID, providerBindingRevision));
    }

    private <T> T executeMutation(
            String idempotencyKey,
            PrincipalContext principal,
            String operation,
            String canonicalArguments,
            List<String> objectRefs,
            NativeFilesDurableMutationPort.Mutation nativeMutation,
            NativePlanCheck nativePlanCheck,
            NativeLockMove nativeLockMove,
            Function<NativeResult, T> nativeResult,
            ProviderMutation<T> apply,
            ProviderMutation<T> reconcile,
            Function<T, String> canonicalResult) {
        return executeMutation(
                idempotencyKey,
                principal,
                operation,
                canonicalArguments,
                objectRefs,
                nativeMutation,
                null,
                nativePlanCheck,
                nativeLockMove,
                nativeResult,
                apply,
                reconcile,
                canonicalResult);
    }

    private <T> T executeMutation(
            String idempotencyKey,
            PrincipalContext principal,
            String operation,
            String canonicalArguments,
            List<String> objectRefs,
            NativeFilesDurableMutationPort.Mutation nativeMutation,
            Ingress ingress,
            NativePlanCheck nativePlanCheck,
            NativeLockMove nativeLockMove,
            Function<NativeResult, T> nativeResult,
            ProviderMutation<T> apply,
            ProviderMutation<T> reconcile,
            Function<T, String> canonicalResult) {
        FilesProviderPort adapter = configuredAdapter(operation);
        if (filesMutationIntentService == null || !(adapter instanceof NativeFilesDurableMutationPort nativeAdapter)) {
            return executeMutation(
                    idempotencyKey,
                    principal,
                    operation,
                    canonicalArguments,
                    objectRefs,
                    apply,
                    reconcile,
                    canonicalResult);
        }

        PreparedMutation prepared;
        try {
            prepared = filesMutationIntentService.prepare(new Command(
                    idempotencyKey,
                    principal.tenantId(),
                    principal.principalRef(),
                    principal.subject(),
                    operation,
                    canonicalArguments,
                    objectRefs,
                    principal.policyRevision(),
                    principal.entitlementRevision()));
        } catch (FilesMutationIntentService.ProviderBindingUnavailableException exception) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-provider-binding-unavailable",
                    "The active Files provider binding is unavailable.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        } catch (FilesMutationIntentService.InvalidIdempotencyKeyException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "files-idempotency-key-invalid",
                    "Idempotency-Key must contain between 16 and 128 characters.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        } catch (com.massimotter.weave.backend.operation.application.OperationIntentService.IdempotencyKeyConflictException exception) {
            throw idempotencyKeyConflict(operation, exception.operationRef());
        }
        try {
            filesMutationIntentService.requireAdapter(
                    prepared,
                    adapter.conformanceProfile().adapterKey());
        } catch (FilesMutationIntentService.PinnedAdapterMismatchException exception) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-provider-binding-mismatch",
                    "The configured Files adapter does not match the pinned provider binding.",
                    Map.of(
                            "module", "files",
                            "operation", operation,
                            "providerBindingRevision", prepared.binding().revision(),
                            "diagnosticsRedacted", true));
        }

        FilesRequestScope scope = new FilesRequestScope(
                principal.tenantId(),
                DEFAULT_CONTEXT_ID,
                prepared.binding().revision());
        if (nativePlanCheck != null && !prepared.retry()) {
            nativePlanCheck.validate(adapter.scoped(scope));
        }
        NativePinnedMutation mutation = null;
        try {
            java.util.function.Supplier<NativePinnedMutation> planCommit = () -> prepared.retry()
                    ? filesMutationIntentService.resumeNative(prepared)
                    : filesMutationIntentService.beginNative(
                            prepared,
                            new FilesScope(scope.organizationRef(), scope.spaceRef()),
                            () -> nativeAdapter.plan(prepared.candidate(), scope, nativeMutation));
            mutation = ingress == null
                    ? planCommit.get()
                    : ingress.bindThroughPlanCommit(
                            prepared.candidate().operationRef(),
                            planCommit);
            filesMutationIntentService.requireAdapter(
                    mutation,
                    adapter.conformanceProfile().adapterKey());
            requireNativeMutationRunnable(mutation, operation);
            String auditRef = publishNativeOperationIntentAudit(mutation);
            NativeResult executed = nativeAdapter.execute(
                    mutation.intent(),
                    scope,
                    mutation.plan(),
                    nativeMutation,
                    auditRef,
                    nativeLockMove);
            if (ingress != null) {
                ingress.releaseIfTerminal();
            }
            return nativeResult.apply(executed);
        } catch (FilesMutationIntentService.PinnedAdapterMismatchException exception) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-provider-binding-mismatch",
                    "The configured Files adapter does not match the pinned provider binding.",
                    Map.of(
                            "module", "files",
                            "operation", operation,
                            "providerBindingRevision", mutation == null
                                    ? prepared.binding().revision()
                                    : mutation.binding().revision(),
                            "diagnosticsRedacted", true));
        } catch (CorruptMutationStateException exception) {
            throw nativeMutationCorrupt(operation);
        } catch (ApiErrorException exception) {
            settleFailedNativeMutation(mutation, exception);
            throw exception;
        }
    }

    private <T> T executeMutation(
            String idempotencyKey,
            PrincipalContext principal,
            String operation,
            String canonicalArguments,
            List<String> objectRefs,
            ProviderMutation<T> apply,
            ProviderMutation<T> reconcile,
            Function<T, String> canonicalResult) {
        if (filesMutationIntentService == null) {
            return apply.execute(configuredAdapter(operation, principal));
        }

        PinnedMutation mutation;
        try {
            mutation = filesMutationIntentService.begin(new Command(
                    idempotencyKey,
                    principal.tenantId(),
                    principal.principalRef(),
                    principal.subject(),
                    operation,
                    canonicalArguments,
                    objectRefs,
                    principal.policyRevision(),
                    principal.entitlementRevision()));
        } catch (FilesMutationIntentService.ProviderBindingUnavailableException exception) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-provider-binding-unavailable",
                    "The active Files provider binding is unavailable.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        } catch (FilesMutationIntentService.InvalidIdempotencyKeyException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "files-idempotency-key-invalid",
                    "Idempotency-Key must contain between 16 and 128 characters.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        } catch (com.massimotter.weave.backend.operation.application.OperationIntentService.IdempotencyKeyConflictException exception) {
            throw idempotencyKeyConflict(operation, exception.operationRef());
        }
        FilesProviderPort adapter = configuredAdapter(operation);
        try {
            try {
                filesMutationIntentService.requireAdapter(mutation, adapter.conformanceProfile().adapterKey());
            } catch (FilesMutationIntentService.PinnedAdapterMismatchException exception) {
                throw new ApiErrorException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "files-provider-binding-mismatch",
                        "The configured Files adapter does not match the pinned provider binding.",
                        Map.of("module", "files", "operation", operation,
                                "providerBindingRevision", mutation.binding().revision(),
                                "diagnosticsRedacted", true));
            }
            adapter = configuredAdapter(operation, principal, mutation.binding().revision());
            if (mutation.retry()) {
                mutation = prepareRetry(mutation, operation);
                T reconciled = reconcile.execute(adapter);
                if (mutation.intent().state() == com.massimotter.weave.backend.operation.domain.OperationIntent.State.RECONCILING) {
                    String auditRef = publishOperationIntentAudit(mutation, principal, operation, "reconciled");
                    filesMutationIntentService.succeed(mutation, canonicalResult.apply(reconciled), auditRef);
                }
                return reconciled;
            }

            mutation = filesMutationIntentService.dispatch(mutation);
            T result = apply.execute(adapter);
            String auditRef = publishOperationIntentAudit(mutation, principal, operation, "succeeded");
            filesMutationIntentService.succeed(mutation, canonicalResult.apply(result), auditRef);
            return result;
        } catch (ApiErrorException exception) {
            settleFailedMutation(mutation, exception, operation, principal);
            throw exception;
        }
    }

    private PinnedMutation prepareRetry(PinnedMutation mutation, String operation) {
        return switch (mutation.intent().state()) {
            case SUCCEEDED -> mutation;
            case DISPATCHING -> filesMutationIntentService.reconcile(filesMutationIntentService.ambiguous(
                    mutation, operation + ":retry-after-dispatch"));
            case AMBIGUOUS -> filesMutationIntentService.reconcile(mutation);
            case RECONCILING -> mutation;
            case CREATED -> filesMutationIntentService.dispatch(mutation);
            case DENIED, FAILED -> throw new ApiErrorException(
                    HttpStatus.CONFLICT,
                    "files-idempotent-operation-terminal",
                    "The idempotency key belongs to a terminal Files operation.",
                    Map.of("module", "files", "operation", operation,
                            "operationRef", mutation.intent().operationRef(), "diagnosticsRedacted", true));
        };
    }

    private void requireNativeMutationRunnable(NativePinnedMutation mutation, String operation) {
        switch (mutation.intent().state()) {
            case CREATED, AMBIGUOUS, RECONCILING, SUCCEEDED -> {
                return;
            }
            case DISPATCHING, DENIED, FAILED -> throw new ApiErrorException(
                    HttpStatus.CONFLICT,
                    "files-idempotent-operation-terminal",
                    "The idempotency key cannot be resumed through the native Files transaction.",
                    Map.of(
                            "module", "files",
                            "operation", operation,
                            "operationRef", mutation.intent().operationRef(),
                            "diagnosticsRedacted", true));
        }
    }

    private void settleFailedNativeMutation(
            NativePinnedMutation mutation,
            ApiErrorException exception) {
        if (mutation == null
                || mutation.intent().state().terminal()
                || exception.status().is5xxServerError()) {
            return;
        }
        var state = mutation.intent().state();
        if (state != com.massimotter.weave.backend.operation.domain.OperationIntent.State.CREATED
                && state != com.massimotter.weave.backend.operation.domain.OperationIntent.State.RECONCILING) {
            return;
        }
        String auditRef = publishNativeOperationIntentAudit(mutation);
        try {
            filesMutationIntentService.failNative(mutation, exception.code(), auditRef);
        } catch (RuntimeException transitionFailure) {
            LOGGER.warn("Failed to record proven native Files failure for {}", mutation.intent().operationRef());
        }
    }

    private void settleFailedMutation(
            PinnedMutation mutation,
            ApiErrorException exception,
            String operation,
            PrincipalContext principal) {
        if (mutation == null) {
            return;
        }
        var state = mutation.intent().state();
        if (state != com.massimotter.weave.backend.operation.domain.OperationIntent.State.CREATED
                && state != com.massimotter.weave.backend.operation.domain.OperationIntent.State.DISPATCHING
                && state != com.massimotter.weave.backend.operation.domain.OperationIntent.State.RECONCILING) {
            return;
        }
        String auditRef = publishOperationIntentAudit(mutation, principal, operation, "failed");
        if (state == com.massimotter.weave.backend.operation.domain.OperationIntent.State.DISPATCHING
                && ("nextcloud-unavailable".equals(exception.code())
                || "nextcloud-request-failed".equals(exception.code())
                || "files-s3-unavailable".equals(exception.code()))) {
            filesMutationIntentService.ambiguous(mutation, operation + ":provider-outcome-unknown");
            return;
        }
        filesMutationIntentService.fail(mutation, exception.code(), auditRef);
    }

    private String publishOperationIntentAudit(
            PinnedMutation mutation, PrincipalContext principal, String operation, String result) {
        return publishOperationIntentAudit(
                mutation.intent(), mutation.binding().revision(), principal, operation, result);
    }

    private String publishNativeOperationIntentAudit(NativePinnedMutation mutation) {
        return publishNativeOperationIntentAudit(mutation.intent());
    }

    private String publishNativeOperationIntentAudit(OperationIntent intent) {
        return NativeFilesOperationAudit.publish(auditEventPublisher, intent);
    }

    private String publishOperationIntentAudit(
            com.massimotter.weave.backend.operation.domain.OperationIntent intent,
            long providerBindingRevision,
            PrincipalContext principal,
            String operation,
            String result) {
        String auditRef = "files-operation-intent:" + intent.operationRef();
        auditEventPublisher.publish(new AuditEvent(
                principal.tenantId(),
                DEFAULT_CONTEXT_ID,
                principal.principalRef(),
                "files:operation-intent",
                AuditAction.FILES_OPERATION_INTENT_RECORDED,
                Instant.now(),
                auditRef,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "domain", "files",
                        "operation", operation,
                        "operationRef", intent.operationRef(),
                        "providerBindingRevision", providerBindingRevision,
                        "result", result,
                        "supportSafe", true)));
        return auditRef;
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
                contextAuthorizationProperties.principalRef(principal.subject()),
                "files:mcp",
                AuditAction.WEAVER_TOOL_INVOCATION_RECORDED,
                Instant.now(),
                "files-mcp-read:" + UUID.randomUUID(),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "domain", "files",
                        "tool", tool,
                        "workloadSubjectSha256",
                                sha256(workload.issuer() + "\u0000" + workload.subject()),
                        "workloadClientId", workload.workloadClientId(),
                        "mcpEdgeClientId", workload.mcpEdgeClientId(),
                        "cellRef", workload.cellRef(),
                        "personRef", workload.personRef(),
                        "providerBindingKey", "files.default",
                        "objectRefSha256", sha256(objectReference == null ? "/" : objectReference),
                        "result", result + ":" + matchCount)));
    }


    private WebDavMutationResult reconcileExisting(
            FilesProviderPort adapter, String normalizedPath, Kind expectedKind, String operation) {
        VersionedFileItem current = existingVersionedItem(adapter, normalizedPath, operation, false);
        if (current == null || (expectedKind != null && current.item().kind() != expectedKind)) {
            throw operationReconciliationRequired(operation, normalizedPath);
        }
        return new WebDavMutationResult(toResponse(current.item()), etag(current), false);
    }

    private Boolean reconcileDeleted(FilesProviderPort adapter, String normalizedPath, String operation) {
        if (existingVersionedItem(adapter, normalizedPath, operation, false) != null) {
            throw operationReconciliationRequired(operation, normalizedPath);
        }
        return Boolean.TRUE;
    }

    private WebDavMutationResult reconcileMove(
            FilesProviderPort adapter, String sourcePath, String destinationPath, String operation) {
        VersionedFileItem source = existingVersionedItem(adapter, sourcePath, operation, false);
        if (source != null) {
            throw operationReconciliationRequired(operation, sourcePath);
        }
        return reconcileExisting(adapter, destinationPath, null, operation);
    }

    private ApiErrorException operationReconciliationRequired(String operation, String path) {
        return new ApiErrorException(
                HttpStatus.CONFLICT,
                "files-operation-reconciliation-required",
                "The prior Files provider outcome could not be reconciled automatically.",
                Map.of("module", "files", "operation", operation,
                        "path", FilePathCodec.normalizeProductPath(path), "diagnosticsRedacted", true));
    }

    private ApiErrorException idempotencyKeyConflict(String operation, String operationRef) {
        return new ApiErrorException(
                HttpStatus.CONFLICT,
                "files-idempotency-key-conflict",
                "The idempotency key is already bound to different Files arguments.",
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "operationRef", operationRef,
                        "diagnosticsRedacted", true));
    }

    private WebDavMutationResult webDavMutationResult(NativeResult result, String operation) {
        if (result == null || result.item() == null || result.etag() == null) {
            throw new ApiErrorException(
                    HttpStatus.CONFLICT,
                    "files-native-result-invalid",
                    "The native Files mutation did not produce the expected result.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        }
        return new WebDavMutationResult(toResponse(result.item()), result.etag(), result.created());
    }

    @FunctionalInterface
    private interface NativePlanCheck {
        void validate(FilesProviderPort adapter);
    }

    @FunctionalInterface
    private interface ProviderMutation<T> {
        T execute(FilesProviderPort adapter);
    }

    private ApiErrorException adapterNotConfigured(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-storage-not-configured",
                "Files facade is available, but file storage is not configured yet.",
                Map.of("module", "files", "operation", operation));
    }

    private void enforceUnlocked(String path, String ifHeader, String operation, PrincipalContext principal) {
        String normalizedPath = FilePathCodec.normalizeProductPath(path);
        try {
            requiredLockService(operation).requireUnlocked(
                    principal.tenantId(), DEFAULT_CONTEXT_ID, new FilePath(normalizedPath),
                    presentedLockToken(ifHeader), principal.principalRef());
        } catch (FileLockedException exception) {
            throw locked(operation, normalizedPath);
        }
    }

    private FilesLockService requiredLockService(String operation) {
        if (filesLockService == null) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-lock-authority-unavailable",
                    "The durable Files lock authority is unavailable.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        }
        return filesLockService;
    }

    private String presentedLockToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        int start = headerValue.indexOf("opaquelocktoken:");
        if (start < 0) {
            return null;
        }
        int end = start;
        while (end < headerValue.length()) {
            char next = headerValue.charAt(end);
            if (Character.isWhitespace(next) || next == '>' || next == ')') {
                break;
            }
            end++;
        }
        return headerValue.substring(start, end);
    }

    private String lockTokenRef(String headerValue) {
        String token = presentedLockToken(headerValue);
        return "lock-token:" + (token == null
                ? "none"
                : FilesMutationIntentService.digest(token));
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
            EntityTagCondition ifMatch,
            EntityTagCondition ifNoneMatch,
            String operation) {
        String currentEtag = existing == null ? null : etag(existing);
        if (ifMatch.supplied() && !ifMatch.matches(currentEtag, true)) {
            throw preconditionFailed(operation, "If-Match did not match the current file state.");
        }
        if (ifNoneMatch.supplied() && ifNoneMatch.matches(currentEtag, false)) {
            throw preconditionFailed(operation, "If-None-Match matched the current file state.");
        }
    }

    private EntityTagCondition parseEntityTagCondition(
            String value,
            String operation,
            String headerName) {
        try {
            return EntityTagCondition.parseHeader(value);
        } catch (IllegalArgumentException invalid) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "files-webdav-precondition-invalid",
                    headerName + " is not a valid entity-tag condition.",
                    Map.of("module", "files", "operation", operation, "diagnosticsRedacted", true));
        }
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
        return FilesEtags.strong(item, version);
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

    private record PutFraming(Long declaredLength, String mediaType) {
    }

    private ApiErrorException supportSafeStorageError(ApiErrorException exception, String operation) {
        String code = switch (exception.code()) {
            case "nextcloud-adapter-not-configured" -> "files-storage-not-configured";
            case "nextcloud-auth-failed" -> "files-storage-auth-failed";
            case "nextcloud-response-invalid" -> "files-storage-response-invalid";
            case "nextcloud-unavailable" -> "files-storage-unavailable";
            case "nextcloud-request-failed" -> "files-storage-request-failed";
            case "files-s3-not-configured" -> "files-storage-not-configured";
            case "files-s3-unavailable" -> "files-storage-unavailable";
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
