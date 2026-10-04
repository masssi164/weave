package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.files.domain.FilesUserResource.State;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.CreatedObject;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import com.massimotter.weave.backend.model.files.FilesUserItemResponse;
import com.massimotter.weave.backend.model.files.FilesUserListResponse;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Base64;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Fail-closed Files User read surface for explicitly attached Weave resources. */
@Service
public class FilesUserApiService {
    public static final String ROOT_ID = "file:root";
    public static final String SPACE_REF = "workspace-default";
    public static final int MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024;
    public static final int MAX_UPLOAD_BYTES = 25 * 1024 * 1024;

    private final OrganizationIdentityContextResolver identities;
    private final ContextAuthorizationProperties contextProperties;
    private final ContextAuthorizationPort contextAuthorization;
    private final WorkspaceCapabilityService capabilities;
    private final ProviderBindingRepository bindings;
    private final FilesProviderResolver providers;
    private final FilesUserResourceRepository resources;
    private final FilesMutationIntentService intents;
    private final TransactionTemplate transactions;
    private final AuditEventPublisher auditEvents;

    public FilesUserApiService(
            OrganizationIdentityContextResolver identities,
            ContextAuthorizationProperties contextProperties,
            ContextAuthorizationPort contextAuthorization,
            WorkspaceCapabilityService capabilities,
            ProviderBindingRepository bindings,
            FilesProviderResolver providers,
            FilesUserResourceRepository resources,
            FilesMutationIntentService intents,
            TransactionTemplate transactions,
            AuditEventPublisher auditEvents) {
        this.identities = identities;
        this.contextProperties = contextProperties;
        this.contextAuthorization = contextAuthorization;
        this.capabilities = capabilities;
        this.bindings = bindings;
        this.providers = providers;
        this.resources = resources;
        this.intents = intents;
        this.transactions = transactions;
        this.auditEvents = auditEvents;
    }

    public FilesUserListResponse list(Jwt jwt, String parentFileId) {
        Member member = member(jwt, "list-files", ContextPermission.VIEW);
        String parent = parentFileId == null || parentFileId.isBlank() ? ROOT_ID : parentFileId;
        FilesUserResource parentResource = null;
        if (!ROOT_ID.equals(parent)) {
            parentResource = requireOwned(member, parent);
            if (parentResource.kind() != Kind.COLLECTION) {
                throw missing();
            }
        }
        ProviderBinding binding = activeBinding(member.organizationRef());
        FilesProviderPort provider = pinnedProvider(binding, member.organizationRef());
        List<String> parentActions = collectionActions(member, provider);
        if (parentResource != null) {
            parentActions = project(member, binding, provider, parentResource).allowedActions();
        }
        List<FilesUserItemResponse> items = resources.activeChildren(member.organizationRef(), parent).stream()
                .filter(resource -> resource.spaceRef().equals(SPACE_REF)
                        && resource.ownerPrincipalRef().equals(member.principalRef()))
                .map(resource -> project(member, binding, provider, resource))
                .toList();
        return new FilesUserListResponse(parent, parentActions, items);
    }

    public FilesUserItemResponse inspect(Jwt jwt, String fileId) {
        Member member = member(jwt, "inspect-file", ContextPermission.VIEW);
        FilesUserResource resource = requireOwned(member, fileId);
        ProviderBinding binding = activeBinding(member.organizationRef());
        return project(member, binding, pinnedProvider(binding, member.organizationRef()), resource);
    }

    public Download download(Jwt jwt, String fileId) {
        Member member = member(jwt, "download-file", ContextPermission.VIEW);
        FilesUserResource resource = requireOwned(member, fileId);
        if (resource.kind() != Kind.FILE) {
            throw missing();
        }
        ProviderBinding binding = activeBinding(member.organizationRef());
        FilesProviderPort provider = pinnedProvider(binding, member.organizationRef());
        VersionedFile current = requireMapped(binding, provider, resource);
        if (current.item().size() > MAX_DOWNLOAD_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "files-download-too-large", "File exceeds the download limit.");
        }
        if (!current.version().known() || !current.version().value().startsWith("\"")
                || !current.version().value().endsWith("\"")) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-strong-version-unavailable",
                    "The active Files provider has no strong version for this file.");
        }
        FileContent content;
        try {
            content = provider.readBoundedIfVersion(current.item().id(), MAX_DOWNLOAD_BYTES,
                    current.version());
        } catch (UnsupportedOperationException unsupported) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-bounded-read-unavailable",
                    "Bounded download is unavailable for the active Files provider.");
        } catch (ApiErrorException providerError) {
            throw supportSafeProviderError(providerError);
        }
        byte[] bytes = content.bytes();
        if (bytes.length > MAX_DOWNLOAD_BYTES || !content.item().id().equals(current.item().id())
                || !content.item().path().equals(current.item().path())) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-read-changed", "File changed during download.");
        }
        requireMapped(binding, provider, resource);
        String digest = digest(bytes);
        return new Download(bytes,
                content.item().mediaType() == null ? "application/octet-stream" : content.item().mediaType(),
                "\"" + digest.replace(':', '-') + "\"",
                "sha-256=:" + Base64.getEncoder().encodeToString(sha256(bytes)) + ":");
    }

    /** Atomic absent-name creation with a durable, explicitly keyed User HTTP intent. */
    public FilesUserItemResponse createFolder(
            Jwt jwt, String parentFileId, String name, String ifNoneMatch, String idempotencyKey) {
        Member member = member(jwt, "create-files-folder", ContextPermission.EDIT);
        if (!"*".equals(ifNoneMatch)) {
            throw error(HttpStatus.PRECONDITION_REQUIRED, "files-create-precondition-required",
                    "If-None-Match: * is required for creation.");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw error(HttpStatus.BAD_REQUEST, "files-idempotency-key-required",
                    "A 16 to 128 character Idempotency-Key is required.");
        }
        String parent = parentFileId == null ? ROOT_ID : parentFileId;
        FilePath parentPath;
        FilesUserResource parentResource = null;
        if (ROOT_ID.equals(parent)) {
            parentPath = new FilePath("/");
        } else {
            parentResource = requireOwned(member, parent);
            if (parentResource.kind() != Kind.COLLECTION) {
                throw missing();
            }
            parentPath = new FilePath(parentResource.path());
        }
        FilePath path;
        try {
            path = childPath(parentPath, name);
        } catch (RuntimeException invalid) {
            throw error(HttpStatus.BAD_REQUEST, "files-invalid-name", "File name is invalid.");
        }
        FilesMutationIntentService.PinnedMutation mutation;
        try {
            mutation = intents.beginUserApi(new FilesMutationIntentService.Command(
                    idempotencyKey, member.organizationRef(), member.principalRef(), member.subject(),
                    "createFilesFolder", parent + "\n" + path.value() + "\nIf-None-Match:*",
                    List.of(parent), member.policyRevision(), member.entitlementRevision()));
        } catch (OperationIntentService.IdempotencyKeyConflictException conflict) {
            throw error(HttpStatus.CONFLICT, "files-idempotency-conflict",
                    "Idempotency-Key is already bound to another Files operation.");
        } catch (FilesMutationIntentService.ProviderBindingUnavailableException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-provider-unavailable",
                    "The active Files provider is unavailable.");
        }
        ProviderBinding binding = mutation.binding();
        if (activeBinding(member.organizationRef()).revision() != binding.revision()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-pinned-binding-unavailable",
                    "The pinned Files binding is no longer active.");
        }
        FilesProviderPort provider = pinnedProvider(binding, member.organizationRef());
        if (!provider.supportsStableObjectRefs() || !provider.supportsAtomicCollectionCreate()
                || !provider.conformanceProfile().supports("create_collection")) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-stable-identity-unavailable",
                    "The active Files provider cannot create a verifiable folder.");
        }
        if (parentResource != null) {
            requireMapped(binding, provider, parentResource);
        }
        String fileId = "file:" + mutation.intent().operationRef().substring("operation:".length());
        if (mutation.retry()) {
            FilesUserResource existing = resources.find(member.organizationRef(), fileId).orElse(null);
            if (mutation.intent().state() == OperationIntent.State.SUCCEEDED && existing != null) {
                return replayRecordedResult(member, binding, provider, mutation, existing);
            }
            if (mutation.intent().state() != OperationIntent.State.CREATED) {
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-intent-reconciliation-required",
                        "The earlier Files operation requires reconciliation.");
            }
        }
        if (resources.findActivePath(member.organizationRef(), binding.revision(), path.value()).isPresent()
                || safeFind(provider, path).isPresent()) {
            intents.fail(mutation, "name-conflict", mutation.intent().operationRef());
            throw error(HttpStatus.CONFLICT, "files-name-conflict", "A file or folder already uses this name.");
        }
        mutation = intents.dispatch(mutation);
        CreatedObject created;
        String createdProviderRef;
        try {
            created = provider.createCollectionIfAbsent(path);
            if (!created.item().path().equals(path) || created.item().kind() != Kind.COLLECTION) {
                throw new IllegalStateException("provider returned a different Files object");
            }
            createdProviderRef = created.providerObjectRef();
            if (!createdProviderRef.equals(provider.providerObjectRef(path).orElse(null))) {
                throw new IllegalStateException("provider identity changed after folder creation");
            }
            if (parentResource != null) requireMapped(binding, provider, parentResource);
        } catch (ApiErrorException rejected) {
            HttpStatus status = userStatus(rejected);
            if (status == HttpStatus.CONFLICT || status == HttpStatus.LOCKED
                    || status == HttpStatus.FORBIDDEN || status == HttpStatus.INSUFFICIENT_STORAGE) {
                ApiErrorException safe = supportSafeProviderError(rejected);
                intents.fail(mutation, safe.code(), mutation.intent().operationRef());
                throw safe;
            }
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-create-outcome-unknown",
                    "Folder creation outcome requires reconciliation.");
        } catch (RuntimeException uncertain) {
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-create-outcome-unknown",
                    "Folder creation outcome requires reconciliation.");
        }
        Instant now = Instant.now();
        FilesUserResource resource = new FilesUserResource(member.organizationRef(), fileId,
                binding.revision(), SPACE_REF, parent, path.value(), Kind.COLLECTION,
                member.principalRef(), FilesUserResource.State.ACTIVE, now, now);
        FilesUserResource published;
        try {
            published = transactions.execute(status -> {
                bindings.saveMapping(new ProviderObjectMapping(member.organizationRef(), "files", binding.revision(),
                        fileId, createdProviderRef, "weave-user-http-create", now, now));
                return resources.save(resource);
            });
        } catch (RuntimeException publicationFailure) {
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-create-publication-unknown",
                    "Folder identity publication requires reconciliation.");
        }
        FilesUserItemResponse result = project(member, binding, provider, published);
        String auditRef = "files:user-http:" + mutation.intent().operationRef();
        auditEvents.publish(new AuditEvent(member.organizationRef(), SPACE_REF,
                member.principalRef(), "files:user-http", AuditAction.FILES_OPERATION_INTENT_RECORDED,
                Instant.now(), auditRef, AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("domain", "files", "operation", "createFilesFolder",
                        "fileId", fileId, "providerBindingRevision", binding.revision(),
                        "result", "completed", "supportSafe", true)));
        intents.succeed(mutation, canonicalResult(result), auditRef);
        return result;
    }

    /** Binary create is capped before adapter dispatch and conditionally published under one FileId. */
    public FilesUserItemResponse upload(Jwt jwt, String parentFileId, String name, String mediaType,
            byte[] bytes, String ifNoneMatch, String idempotencyKey) {
        Member member = member(jwt, "upload-files-item", ContextPermission.EDIT);
        if (!"*".equals(ifNoneMatch)) {
            throw error(HttpStatus.PRECONDITION_REQUIRED, "files-create-precondition-required",
                    "If-None-Match: * is required for creation.");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw error(HttpStatus.BAD_REQUEST, "files-idempotency-key-required",
                    "A 16 to 128 character Idempotency-Key is required.");
        }
        if (bytes == null || bytes.length > MAX_UPLOAD_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "files-upload-too-large", "Upload exceeds the byte limit.");
        }
        mediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType;
        mediaType = validatedMediaType(mediaType);
        String parent = parentFileId == null ? ROOT_ID : parentFileId;
        FilesUserResource parentResource = ROOT_ID.equals(parent) ? null : requireOwned(member, parent);
        if (parentResource != null && parentResource.kind() != Kind.COLLECTION) throw missing();
        FilePath parentPath = parentResource == null ? new FilePath("/") : new FilePath(parentResource.path());
        FilePath path;
        try {
            path = childPath(parentPath, name);
        } catch (RuntimeException invalid) {
            throw error(HttpStatus.BAD_REQUEST, "files-invalid-name", "File name is invalid.");
        }
        String contentDigest = digest(bytes);
        FilesMutationIntentService.PinnedMutation mutation;
        try {
            mutation = intents.beginUserApi(new FilesMutationIntentService.Command(
                    idempotencyKey, member.organizationRef(), member.principalRef(), member.subject(),
                    "uploadFilesItemContent", parent + "\n" + path.value() + "\n" + mediaType
                            + "\n" + contentDigest + "\nIf-None-Match:*",
                    List.of(parent), member.policyRevision(), member.entitlementRevision()));
        } catch (OperationIntentService.IdempotencyKeyConflictException conflict) {
            throw error(HttpStatus.CONFLICT, "files-idempotency-conflict",
                    "Idempotency-Key is already bound to another Files operation.");
        } catch (FilesMutationIntentService.ProviderBindingUnavailableException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-provider-unavailable",
                    "The active Files provider is unavailable.");
        }
        ProviderBinding binding = mutation.binding();
        if (activeBinding(member.organizationRef()).revision() != binding.revision()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-pinned-binding-unavailable",
                    "The pinned Files binding is no longer active.");
        }
        FilesProviderPort provider = pinnedProvider(binding, member.organizationRef());
        if (!provider.supportsStableObjectRefs()
                || !provider.supportsConditionalWrite() || !provider.supportsBoundedRead()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-conditional-write-unavailable",
                    "The active Files provider cannot enforce conditional upload.");
        }
        if (parentResource != null) requireMapped(binding, provider, parentResource);
        String fileId = "file:" + mutation.intent().operationRef().substring("operation:".length());
        if (mutation.retry()) {
            FilesUserResource existing = resources.find(member.organizationRef(), fileId).orElse(null);
            if (mutation.intent().state() == OperationIntent.State.SUCCEEDED && existing != null) {
                return replayRecordedResult(member, binding, provider, mutation, existing);
            }
            if (mutation.intent().state() != OperationIntent.State.CREATED) {
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-intent-reconciliation-required",
                        "The earlier Files operation requires reconciliation.");
            }
        }
        if (resources.findActivePath(member.organizationRef(), binding.revision(), path.value()).isPresent()
                || safeFind(provider, path).isPresent()) {
            intents.fail(mutation, "name-conflict", mutation.intent().operationRef());
            throw error(HttpStatus.CONFLICT, "files-name-conflict", "A file or folder already uses this name.");
        }
        mutation = intents.dispatch(mutation);
        CreatedObject created;
        String createdProviderRef;
        try {
            created = provider.writeIfAbsent(new FileWrite(path, bytes, mediaType));
            if (!created.item().path().equals(path) || created.item().kind() != Kind.FILE) {
                throw new IllegalStateException("provider returned a different Files object");
            }
            FileContent verified = provider.readBounded(created.item().id(), MAX_DOWNLOAD_BYTES);
            if (!verified.item().id().equals(created.item().id())
                    || !verified.item().path().equals(path)
                    || !contentDigest.equals(digest(verified.bytes()))) {
                throw new IllegalStateException("provider readback content differs from the requested bytes");
            }
            createdProviderRef = created.providerObjectRef();
            if (!createdProviderRef.equals(provider.providerObjectRef(path).orElse(null))) {
                throw new IllegalStateException("provider identity changed after upload");
            }
            if (parentResource != null) requireMapped(binding, provider, parentResource);
        } catch (ApiErrorException rejected) {
            HttpStatus status = userStatus(rejected);
            if (status == HttpStatus.CONFLICT || status == HttpStatus.LOCKED
                    || status == HttpStatus.PRECONDITION_FAILED || status == HttpStatus.FORBIDDEN
                    || status == HttpStatus.INSUFFICIENT_STORAGE) {
                ApiErrorException safe = supportSafeProviderError(rejected);
                intents.fail(mutation, safe.code(), mutation.intent().operationRef());
                throw safe;
            }
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-upload-outcome-unknown",
                    "Upload outcome requires reconciliation.");
        } catch (RuntimeException uncertain) {
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-upload-outcome-unknown",
                    "Upload outcome requires reconciliation.");
        }
        Instant now = Instant.now();
        FilesUserResource resource = new FilesUserResource(member.organizationRef(), fileId,
                binding.revision(), SPACE_REF, parent, path.value(), Kind.FILE,
                member.principalRef(), FilesUserResource.State.ACTIVE, now, now);
        FilesUserResource published;
        try {
            published = transactions.execute(status -> {
                bindings.saveMapping(new ProviderObjectMapping(member.organizationRef(), "files", binding.revision(),
                        fileId, createdProviderRef, "weave-user-http-create", now, now));
                return resources.save(resource);
            });
        } catch (RuntimeException publicationFailure) {
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-upload-publication-unknown",
                    "Uploaded file identity requires reconciliation.");
        }
        FilesUserItemResponse result = project(member, binding, provider, published);
        String auditRef = "files:user-http:" + mutation.intent().operationRef();
        auditEvents.publish(new AuditEvent(member.organizationRef(), SPACE_REF,
                member.principalRef(), "files:user-http", AuditAction.FILES_OPERATION_INTENT_RECORDED,
                Instant.now(), auditRef, AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("domain", "files", "operation", "uploadFilesItemContent",
                        "fileId", fileId, "providerBindingRevision", binding.revision(),
                        "contentDigest", contentDigest, "result", "completed", "supportSafe", true)));
        intents.succeed(mutation, canonicalResult(result), auditRef);
        return result;
    }

    /** Replace one owned file only after both Weave content and provider versions match. */
    public FilesUserItemResponse update(Jwt jwt, String fileId, String mediaType, byte[] bytes,
            String ifMatch, String idempotencyKey) {
        Member member = member(jwt, "update-files-item-content", ContextPermission.EDIT);
        if (ifMatch == null || ifMatch.isBlank()) {
            throw error(HttpStatus.PRECONDITION_REQUIRED, "files-update-precondition-required",
                    "A current strong If-Match validator is required.");
        }
        if (!ifMatch.startsWith("\"sha256-") || !ifMatch.endsWith("\"") || ifMatch.startsWith("W/")) {
            throw error(HttpStatus.PRECONDITION_FAILED, "files-weak-or-invalid-validator",
                    "The supplied validator is not a current strong Files content validator.");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw error(HttpStatus.BAD_REQUEST, "files-idempotency-key-required",
                    "A 16 to 128 character Idempotency-Key is required.");
        }
        if (bytes == null || bytes.length > MAX_UPLOAD_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "files-upload-too-large", "Upload exceeds the byte limit.");
        }
        FilesUserResource resource = requireOwned(member, fileId);
        if (resource.kind() != Kind.FILE) throw missing();
        ProviderBinding binding = activeBinding(member.organizationRef());
        FilesProviderPort provider = pinnedProvider(binding, member.organizationRef());
        if (!provider.supportsBoundedRead() || !provider.supportsConditionalWrite()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-conditional-write-unavailable",
                    "The active Files provider cannot enforce conditional update.");
        }
        VersionedFile observed = requireMapped(binding, provider, resource);
        String nextMediaType = mediaType == null || mediaType.isBlank()
                ? (observed.item().mediaType() == null ? "application/octet-stream" : observed.item().mediaType())
                : mediaType;
        nextMediaType = validatedMediaType(nextMediaType);
        String nextDigest = digest(bytes);
        FilesMutationIntentService.PinnedMutation mutation;
        try {
            mutation = intents.beginUserApi(new FilesMutationIntentService.Command(
                    idempotencyKey, member.organizationRef(), member.principalRef(), member.subject(),
                    "updateFilesItemContent", fileId + "\n" + ifMatch + "\n" + nextDigest
                            + "\n" + nextMediaType,
                    List.of(fileId), member.policyRevision(), member.entitlementRevision()));
        } catch (OperationIntentService.IdempotencyKeyConflictException conflict) {
            throw error(HttpStatus.CONFLICT, "files-idempotency-conflict",
                    "Idempotency-Key is already bound to another Files operation.");
        } catch (FilesMutationIntentService.ProviderBindingUnavailableException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-provider-unavailable",
                    "The active Files provider is unavailable.");
        }
        if (mutation.binding().revision() != binding.revision()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-pinned-binding-unavailable",
                    "The pinned Files binding is no longer active.");
        }
        if (mutation.retry()) {
            if (mutation.intent().state() == OperationIntent.State.SUCCEEDED) {
                return replayRecordedResult(member, binding, provider, mutation,
                        requireOwned(member, fileId));
            }
            if (mutation.intent().state() != OperationIntent.State.CREATED) {
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-intent-reconciliation-required",
                        "The earlier Files operation requires reconciliation.");
            }
        }
        if (observed.item().size() > MAX_DOWNLOAD_BYTES) {
            intents.fail(mutation, "current-file-too-large", mutation.intent().operationRef());
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "files-update-too-large",
                    "The current file exceeds the update limit.");
        }
        if (!observed.version().known() || !observed.version().value().startsWith("\"")
                || !observed.version().value().endsWith("\"")) {
            intents.fail(mutation, "strong-provider-version-unavailable", mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-strong-version-unavailable",
                    "The active Files provider has no strong version for this file.");
        }
        FileContent current;
        try {
            current = provider.readBoundedIfVersion(observed.item().id(), MAX_DOWNLOAD_BYTES,
                    observed.version());
        } catch (ApiErrorException providerError) {
            throw supportSafeProviderError(providerError);
        }
        String currentEtag = "\"" + digest(current.bytes()).replace(':', '-') + "\"";
        if (!current.item().path().equals(observed.item().path()) || !ifMatch.equals(currentEtag)) {
            intents.fail(mutation, "stale-content", mutation.intent().operationRef());
            throw error(HttpStatus.PRECONDITION_FAILED, "files-precondition-failed",
                    "The file content changed.");
        }
        mutation = intents.dispatch(mutation);
        try {
            FileObject updated = provider.writeIfVersion(
                    new FileWrite(new FilePath(resource.path()), bytes, nextMediaType), observed.version());
            if (!updated.id().equals(observed.item().id()) || !updated.path().equals(observed.item().path())) {
                throw new IllegalStateException("provider changed Files identity during content update");
            }
            FileContent verified = provider.readBounded(updated.id(), MAX_DOWNLOAD_BYTES);
            if (!nextDigest.equals(digest(verified.bytes()))) {
                throw new IllegalStateException("provider readback content differs from the requested bytes");
            }
            requireMapped(binding, provider, resource);
        } catch (ApiErrorException rejected) {
            HttpStatus status = userStatus(rejected);
            if (status == HttpStatus.PRECONDITION_FAILED || status == HttpStatus.CONFLICT
                    || status == HttpStatus.LOCKED || status == HttpStatus.FORBIDDEN
                    || status == HttpStatus.INSUFFICIENT_STORAGE) {
                ApiErrorException safe = supportSafeProviderError(rejected);
                intents.fail(mutation, safe.code(), mutation.intent().operationRef());
                throw safe;
            }
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-update-outcome-unknown",
                    "Content update outcome requires reconciliation.");
        } catch (RuntimeException uncertain) {
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-update-outcome-unknown",
                    "Content update outcome requires reconciliation.");
        }
        Instant now = Instant.now();
        FilesUserResource changed = new FilesUserResource(resource.organizationRef(), resource.fileId(),
                resource.bindingRevision(), resource.spaceRef(), resource.parentFileId(), resource.path(),
                resource.kind(), resource.ownerPrincipalRef(), resource.state(), resource.createdAt(), now);
        try {
            transactions.execute(status -> resources.save(changed));
        } catch (RuntimeException publicationFailure) {
            intents.ambiguous(mutation, mutation.intent().operationRef());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-update-publication-unknown",
                    "Updated file metadata requires reconciliation.");
        }
        FilesUserItemResponse result = project(member, binding, provider, changed);
        String auditRef = "files:user-http:" + mutation.intent().operationRef();
        auditEvents.publish(new AuditEvent(member.organizationRef(), SPACE_REF,
                member.principalRef(), "files:user-http", AuditAction.FILES_OPERATION_INTENT_RECORDED,
                Instant.now(), auditRef, AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("domain", "files", "operation", "updateFilesItemContent",
                        "fileId", fileId, "providerBindingRevision", binding.revision(),
                        "contentDigest", nextDigest, "result", "completed", "supportSafe", true)));
        intents.succeed(mutation, canonicalResult(result), auditRef);
        return result;
    }

    private FilesUserItemResponse replayRecordedResult(Member member, ProviderBinding binding,
            FilesProviderPort provider, FilesMutationIntentService.PinnedMutation mutation,
            FilesUserResource resource) {
        FilesUserItemResponse current = project(member, binding, provider, resource);
        if (!FilesMutationIntentService.digest(canonicalResult(current))
                .equals(mutation.intent().resultDigest())) {
            throw error(HttpStatus.CONFLICT, "files-idempotent-result-changed",
                    "The recorded Files result has changed since the original operation.");
        }
        return current;
    }

    private String canonicalResult(FilesUserItemResponse item) {
        return item.fileId() + "\n" + item.revision();
    }

    private FilePath childPath(FilePath parent, String name) {
        if (name == null || name.length() > 255 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("file name is invalid or exceeds its limit");
        }
        FilePath path = new FilePath(FilePathCodec.childPath(parent.value(), name));
        if (path.value().length() > 2048) {
            throw new IllegalArgumentException("file path exceeds its limit");
        }
        return path;
    }

    private FilesUserItemResponse project(
            Member member, ProviderBinding binding, FilesProviderPort provider, FilesUserResource resource) {
        VersionedFile found = requireMapped(binding, provider, resource);
        var item = found.item();
        // Provider metadata, unlike a freshly persisted Java Instant, survives the database's
        // timestamp precision unchanged. Successful idempotent retries must reproduce this value.
        String revision = digest((resource.fileId() + "\n" + found.version().value() + "\n"
                + item.kind() + "\n" + item.size() + "\n" + item.mediaType() + "\n"
                + item.modifiedAt()).getBytes(StandardCharsets.UTF_8));
        List<String> actions = new ArrayList<>();
        actions.add("inspect");
        if (item.kind() == Kind.COLLECTION) {
            actions.addAll(collectionActions(member, provider));
        } else if (item.size() <= MAX_DOWNLOAD_BYTES && provider.supportsBoundedRead()) {
            actions.add("download");
            if (member.canEdit() && provider.supportsConditionalWrite()) {
                actions.add("updateContent");
            }
        }
        return new FilesUserItemResponse(resource.fileId(), resource.parentFileId(), item.name(),
                resource.path(), item.kind() == Kind.FILE ? "file" : "folder", item.size(), item.mediaType(),
                item.modifiedAt(), revision, actions);
    }

    private List<String> collectionActions(Member member, FilesProviderPort provider) {
        List<String> actions = new ArrayList<>();
        actions.add("listChildren");
        if (member.canEdit() && provider.supportsStableObjectRefs()
                && provider.supportsAtomicCollectionCreate()
                && provider.conformanceProfile().supports("create_collection")) {
            actions.add("createFolder");
        }
        if (member.canEdit() && provider.supportsStableObjectRefs()
                && provider.supportsConditionalWrite() && provider.supportsBoundedRead()) {
            actions.add("upload");
        }
        return actions;
    }

    private VersionedFile requireMapped(ProviderBinding binding, FilesProviderPort provider,
            FilesUserResource resource) {
        if (resource.bindingRevision() != binding.revision()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-binding-identity-unavailable",
                    "Files identity is unavailable for the active binding.");
        }
        String mappedRef = bindings.mappingByCanonicalId(resource.organizationRef(), "files",
                        binding.revision(), resource.fileId())
                .orElseThrow(this::missing).providerObjectRef();
        FilePath path = new FilePath(resource.path());
        VersionedFile found = safeFind(provider, path).orElseThrow(this::missing);
        String currentRef;
        try {
            currentRef = provider.providerObjectRef(path)
                    .orElseThrow(() -> error(HttpStatus.SERVICE_UNAVAILABLE,
                            "files-provider-identity-unavailable", "Files identity could not be verified."));
        } catch (ApiErrorException providerError) {
            throw supportSafeProviderError(providerError);
        }
        if (!currentRef.equals(mappedRef) || found.item().kind() != resource.kind()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-mapping-stale",
                    "Files identity could not be verified.");
        }
        return found;
    }

    private FilesUserResource requireOwned(Member member, String fileId) {
        if (fileId == null || fileId.isBlank() || ROOT_ID.equals(fileId)) {
            throw missing();
        }
        FilesUserResource resource = resources.find(member.organizationRef(), fileId).orElseThrow(this::missing);
        if (resource.state() != State.ACTIVE || !resource.spaceRef().equals(SPACE_REF)
                || !resource.ownerPrincipalRef().equals(member.principalRef())) {
            throw missing();
        }
        return resource;
    }

    private Member member(Jwt jwt, String operation, ContextPermission permission) {
        if (jwt == null || "weave-mcp-server".equals(jwt.getClaimAsString("azp"))) {
            throw error(HttpStatus.FORBIDDEN, "files-user-context-required", "A member session is required.");
        }
        capabilities.requireCapability(jwt,
                permission == ContextPermission.VIEW ? "files.read" : "files.upload", "files", operation);
        var identity = identities.resolve(jwt);
        String principal = contextProperties.principalRef(jwt.getClaimAsString(contextProperties.principalClaim()));
        if (principal == null) {
            throw error(HttpStatus.UNAUTHORIZED, "unauthorized", "A member identity is required.");
        }
        var decision = contextAuthorization.check(new ContextAuthorizationRequest(
                identity.organizationId(), SPACE_REF, principal, permission));
        if (!decision.allowed()) {
            throw error(HttpStatus.FORBIDDEN, "files-forbidden", "Files access is not allowed for this Space.");
        }
        boolean canEdit = permission == ContextPermission.EDIT || canEdit(jwt, identity.organizationId(), principal);
        return new Member(identity.organizationId(), principal, identity.subject(), canEdit,
                revisionClaim(jwt, "weave_policy_revision", "policy:unversioned"),
                revisionClaim(jwt, "weave_entitlement_revision", "entitlement:unversioned"));
    }

    private boolean canEdit(Jwt jwt, String organizationRef, String principal) {
        try {
            capabilities.requireCapability(jwt, "files.upload", "files", "files-available-actions");
            return contextAuthorization.check(new ContextAuthorizationRequest(
                    organizationRef, SPACE_REF, principal, ContextPermission.EDIT)).allowed();
        } catch (ApiErrorException denied) {
            return false;
        }
    }

    private String revisionClaim(Jwt jwt, String name, String fallback) {
        Object claim = jwt.getClaim(name);
        return claim == null || claim.toString().isBlank() ? fallback : name + ":" + claim;
    }

    private ProviderBinding activeBinding(String organizationRef) {
        return bindings.current(organizationRef, "files")
                .filter(binding -> binding.state() == ProviderBinding.State.ACTIVE)
                .orElseThrow(() -> error(HttpStatus.SERVICE_UNAVAILABLE,
                        "files-provider-unavailable", "The active Files provider is unavailable."));
    }

    private FilesProviderPort pinnedProvider(ProviderBinding binding, String organizationRef) {
        try {
            return providers.pinned(binding, organizationRef, SPACE_REF);
        } catch (FilesProviderResolver.ProviderUnavailableException
                | FilesProviderResolver.StaleBindingException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "files-provider-unavailable",
                    "The active Files provider is unavailable.");
        } catch (ApiErrorException providerError) {
            throw supportSafeProviderError(providerError);
        }
    }

    private ApiErrorException missing() {
        return error(HttpStatus.NOT_FOUND, "file-not-found", "The file was not found.");
    }

    private ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message, Map.of("module", "files"));
    }

    private HttpStatus userStatus(ApiErrorException providerError) {
        return Integer.valueOf(423).equals(providerError.details().get("downstreamStatus"))
                ? HttpStatus.LOCKED : providerError.status();
    }

    private java.util.Optional<VersionedFile> safeFind(FilesProviderPort provider, FilePath path) {
        try {
            return provider.find(path);
        } catch (ApiErrorException providerError) {
            throw supportSafeProviderError(providerError);
        }
    }

    private ApiErrorException supportSafeProviderError(ApiErrorException providerError) {
        return switch (userStatus(providerError)) {
            case NOT_FOUND -> missing();
            case FORBIDDEN -> error(HttpStatus.FORBIDDEN, "files-provider-forbidden",
                    "The file operation is not permitted.");
            case CONFLICT -> error(HttpStatus.CONFLICT, "files-provider-conflict",
                    "The file operation conflicts with the current provider state.");
            case PRECONDITION_FAILED -> error(HttpStatus.PRECONDITION_FAILED, "files-precondition-failed",
                    "The file precondition no longer holds.");
            case LOCKED -> error(HttpStatus.LOCKED, "files-locked", "The file is locked.");
            case INSUFFICIENT_STORAGE -> error(HttpStatus.INSUFFICIENT_STORAGE,
                    "files-storage-full", "The active Files provider has insufficient storage.");
            case PAYLOAD_TOO_LARGE -> error(HttpStatus.PAYLOAD_TOO_LARGE,
                    "files-download-too-large", "File exceeds the download limit.");
            default -> error(HttpStatus.SERVICE_UNAVAILABLE, "files-provider-unavailable",
                    "The active Files provider is unavailable.");
        };
    }

    private String validatedMediaType(String value) {
        if (value == null || value.length() > 255) {
            throw error(HttpStatus.BAD_REQUEST, "files-media-type-invalid", "Media type is invalid.");
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(value);
            if (mediaType.isWildcardType() || mediaType.isWildcardSubtype()) {
                throw new IllegalArgumentException("wildcard media type");
            }
            return mediaType.toString();
        } catch (IllegalArgumentException invalid) {
            throw error(HttpStatus.BAD_REQUEST, "files-media-type-invalid", "Media type is invalid.");
        }
    }

    private static String digest(byte[] value) {
        return "sha256:" + HexFormat.of().formatHex(sha256(value));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Member(String organizationRef, String principalRef, String subject, boolean canEdit,
                          String policyRevision, String entitlementRevision) {}

    public record Download(byte[] bytes, String mediaType, String etag, String digest) {}
}
