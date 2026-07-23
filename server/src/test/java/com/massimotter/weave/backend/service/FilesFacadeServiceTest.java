package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationConfiguration;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.Command;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.PinnedMutation;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
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
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import com.massimotter.weave.backend.security.device.InMemoryDeviceCredentialRepository;
import com.massimotter.weave.backend.service.files.WebDavLockResult;
import com.massimotter.weave.backend.service.files.WebDavMutationResult;
import com.massimotter.weave.backend.service.files.WebDavPropfindResource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilesFacadeServiceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ContextAuthorizationTestConfiguration.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void failsClosedWhenAdapterIsMissingOrUnconfigured() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        FilesFacadeService missing = service(null);
        FilesFacadeService unconfigured = service(new StubAdapter(false));

        assertThatThrownBy(() -> missing.list("/"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("files-storage-not-configured");
                    assertThat(exception.getMessage()).doesNotContain("Nextcloud", "provider", "remote.php");
                    assertThat(exception.details()).containsEntry("operation", "list-files");
                });
        assertThatThrownBy(() -> unconfigured.upload("/", null))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
                    assertThat(exception.code()).isEqualTo("files-webdav-write-policy-required");
                    assertThat(exception.details()).containsEntry("operation", "upload-file");
                    assertThat(exception.details()).containsEntry("writePolicyIssue", "#1007");
                    assertThat(exception.details()).containsEntry("openApiDataPlaneUsed", false);
                });
    }

    @Test
    void delegatesToConfiguredStorageAdapter() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        FilesFacadeService service = service(new StubAdapter(true));

        FileListResponse response = service.list("/Team");

        assertThat(response.path()).isEqualTo("/Team");
        assertThat(response.items()).extracting(FileItemResponse::name).containsExactly("readme.md");
    }

    @Test
    void webDavPropfindReturnsWeaveMetadataWithoutPerChildVersionLookups() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        StubAdapter adapter = new StubAdapter(true);
        FilesFacadeService service = service(adapter);

        var response = service.webDavPropfind("/Team");

        assertThat(response.requested().item().path()).isEqualTo("/Team");
        assertThat(response.requested().etag()).startsWith("\"").endsWith("\"");
        assertThat(response.children())
                .extracting(WebDavPropfindResource::item)
                .extracting(FileItemResponse::path)
                .containsExactly("/Team/readme.md");
        assertThat(response.children())
                .extracting(WebDavPropfindResource::etag)
                .allSatisfy(etag -> assertThat(etag).startsWith("\"").endsWith("\""));
        assertThat(adapter.versionTokenCalls).isZero();
        assertThat(adapter.listWithVersionTokenCalls).isEqualTo(1);
        assertThat(response.children().get(0).etag()).isEqualTo(service.etagFor("/Team/readme.md"));
        assertThat(adapter.versionTokenCalls).isEqualTo(1);
    }

    @Test
    void mapsProviderNamedAdapterErrorsToSupportSafeStorageErrors() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        FilesFacadeService service = service(new ProviderErrorAdapter());

        assertThatThrownBy(() -> service.list("/Team"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("files-storage-unavailable");
                    assertThat(exception.getMessage()).isEqualTo("Files storage is temporarily unavailable.");
                    assertThat(exception.getMessage()).doesNotContain("Nextcloud", "remote.php", "Bearer");
                    assertThat(exception.details())
                            .containsEntry("module", "files")
                            .containsEntry("operation", "list-files")
                            .containsEntry("diagnosticsRedacted", true)
                            .doesNotContainKeys("downstreamStatus", "providerUrl");
                });
    }

    @Test
    void listFailsClosedWhenContextAuthorizationDeniesAccess() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        assertThatThrownBy(() -> service(
                        new StubAdapter(true),
                        request -> ContextAuthorizationDecision.deny("no matching context membership"))
                        .list("/Team"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.code()).isEqualTo("files-forbidden");
                    assertThat(exception.details()).containsEntry("module", "files");
                    assertThat(exception.details()).containsEntry("operation", "list-files");
                    assertThat(exception.details()).containsEntry("contextId", "workspace-default");
                    assertThat(exception.details()).containsEntry("permission", "view");
                    assertThat(exception.details()).containsEntry("reason", "no matching context membership");
                });
    }

    @Test
    void mutatingOperationsRequireEditPermissionForWorkspaceContext() {
        AtomicReference<ContextAuthorizationRequest> captured = new AtomicReference<>();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        assertThatThrownBy(() -> service(
                        new StubAdapter(true),
                        request -> {
                            captured.set(request);
                            return ContextAuthorizationDecision.deny("edit denied");
                        })
                        .upload("/Team", null))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.details()).containsEntry("permission", "edit");
                });
        assertThat(captured.get().tenantId()).isEqualTo("tenant-default");
        assertThat(captured.get().contextId()).isEqualTo("workspace-default");
        assertThat(captured.get().principalRef()).isEqualTo("user:user-123");
        assertThat(captured.get().permission()).isEqualTo(ContextPermission.EDIT);
    }

    @Test
    void mutatingOperationsFailClosedBeforeStorageAdapterAccessUntilWebdavWritePolicyExists() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        FilesFacadeService service = service(new StubAdapter(true), audit);

        assertThatThrownBy(() -> service.createFolder(new CreateFolderRequest("/Team", "Design")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
                    assertThat(exception.code()).isEqualTo("files-webdav-write-policy-required");
                    assertThat(exception.details())
                            .containsEntry("operation", "create-folder")
                            .containsEntry("webDavFacadePath", "/dav/files")
                            .containsEntry("writePolicyIssue", "#1007")
                            .containsEntry("openApiDataPlaneUsed", false)
                            .containsEntry("diagnosticsRedacted", true);
                    assertThat(exception.getMessage()).doesNotContain("Nextcloud", "remote.php", "Bearer");
                });
        assertThatThrownBy(() -> service.upload("/Team", null))
                .isInstanceOfSatisfying(ApiErrorException.class, exception ->
                        assertThat(exception.details()).containsEntry("operation", "upload-file"));
        assertThatThrownBy(() -> service.delete("/Team/old.md"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception ->
                        assertThat(exception.details()).containsEntry("operation", "delete-file"));
        assertThat(audit.events())
                .hasSize(3)
                .allSatisfy(event -> {
                    assertThat(event.action()).isEqualTo(AuditAction.FILES_WEBDAV_WRITE_BLOCKED);
                    assertThat(event.tenantId()).isEqualTo("tenant-default");
                    assertThat(event.contextId()).isEqualTo("workspace-default");
                    assertThat(event.actorRef()).isEqualTo("user:user-123");
                    assertThat(event.payload())
                            .containsEntry("domain", "files")
                            .containsEntry("result", "blocked_write_policy_required")
                            .containsEntry("writePolicyIssue", "#1007")
                            .containsEntry("openApiDataPlaneUsed", false)
                            .containsEntry("supportSafe", true);
                });
    }

    @Test
    void webDavWriteRejectionsRequireEditPolicyAndPublishSupportSafeAudit() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        FilesFacadeService service = service(new StubAdapter(true), audit);

        ApiErrorException exception = service.rejectWebDavWrite("PUT", "/Team/readme.md");
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(exception.code()).isEqualTo("files-webdav-write-policy-required");
        assertThat(exception.details())
                .containsEntry("operation", "webdav-put")
                .containsEntry("webDavFacadePath", "/dav/files")
                .containsEntry("writePolicyIssue", "#1007")
                .containsEntry("openApiDataPlaneUsed", false);

        assertThat(audit.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.FILES_WEBDAV_WRITE_BLOCKED);
            assertThat(event.payload())
                    .containsEntry("operation", "webdav-put")
                    .containsEntry("webDavMethod", "PUT")
                    .containsEntry("productPath", "/Team/readme.md")
                    .containsEntry("webDavFacadePath", "/dav/files")
                    .doesNotContainKeys("providerUrl", "downstreamPayload", "bearerToken");
        });
    }

    @Test
    void webDavPutCreateFolderAndDeleteUseFacadePolicyAndPublishMutationAudit() {
        // FILES_WEBDAV_PUT_CREATE_AUDIT
        // FILES_WEBDAV_DELETE_AUDIT
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        StubAdapter adapter = new StubAdapter(true);
        FilesFacadeService service = service(adapter, audit);

        WebDavMutationResult put = service.putWebDavFile(
                "/Team/new.md",
                "new".getBytes(),
                "text/markdown",
                null,
                "*");
        WebDavMutationResult folder = service.createWebDavFolder("/Team/Design", null, "*");
        String readmeEtag = service.etagFor("/Team/readme.md");
        service.deleteWebDavPath("/Team/readme.md", readmeEtag);

        assertThat(put.created()).isTrue();
        assertThat(put.item().path()).isEqualTo("/Team/new.md");
        assertThat(put.etag()).startsWith("\"").endsWith("\"");
        assertThat(folder.created()).isTrue();
        assertThat(folder.item().path()).isEqualTo("/Team/Design");
        assertThat(adapter.deletedPath).isEqualTo("/Team/readme.md");
        assertThat(audit.events())
                .extracting(event -> event.action())
                .containsExactly(
                        AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED,
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                        AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED,
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                        AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED,
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED);
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload())
                .containsEntry("webDavFacadePath", "/dav/files")
                .containsEntry("openApiDataPlaneUsed", false)
                .containsEntry("supportSafe", true)
                .doesNotContainKeys("providerUrl", "rawProviderPayload", "bearerToken"));
    }

    @Test
    void webDavMkcolRejectsDuplicatesAndMissingParentsBeforeStorageMutation() {
        // FILES_WEBDAV_MKCOL_CONFLICTS
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        StubAdapter adapter = new StubAdapter(true);
        FilesFacadeService service = service(adapter, new InMemoryAuditEventPublisher());

        assertThatThrownBy(() -> service.createWebDavFolder("/Team", null, null))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("file-conflict");
                    assertThat(exception.details()).containsEntry("operation", "webdav-mkcol");
                });
        assertThatThrownBy(() -> service.createWebDavFolder("/Missing/Child", null, "*"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("file-conflict");
                    assertThat(exception.details())
                            .containsEntry("operation", "webdav-mkcol")
                            .containsEntry("diagnosticsRedacted", true);
                });

        assertThat(adapter.createdFolderPath).isNull();
    }

    @Test
    void webDavWritePreconditionsFailBeforeStorageMutationButAfterAttemptAudit() {
        // FILES_WEBDAV_STALE_ETAG_PRECONDITION
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        StubAdapter adapter = new StubAdapter(true);
        FilesFacadeService service = service(adapter, audit);

        assertThatThrownBy(() -> service.putWebDavFile(
                        "/Team/readme.md",
                        "new".getBytes(),
                        "text/markdown",
                        null,
                        "*"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                    assertThat(exception.code()).isEqualTo("files-precondition-failed");
                    assertThat(exception.details())
                            .containsEntry("operation", "webdav-put")
                            .containsEntry("openApiDataPlaneUsed", false)
                            .containsEntry("diagnosticsRedacted", true);
                });

        assertThat(adapter.putPath).isNull();
        assertThat(audit.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED);
            assertThat(event.payload())
                    .containsEntry("operation", "webdav-put")
                    .containsEntry("webDavMethod", "PUT")
                    .containsEntry("productPath", "/Team/readme.md");
        });
    }

    @Test
    void webDavCopyAndMoveEnforceDestinationsAndPublishAudit() {
        // FILES_WEBDAV_COPY_MOVE_CANONICAL
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        StubAdapter adapter = new StubAdapter(true);
        FilesFacadeService service = service(adapter, audit);

        WebDavMutationResult copied = service.copyWebDavPath(
                "/Team/readme.md", "/Team/readme-copy.md", false, null, null);
        WebDavMutationResult moved = service.moveWebDavPath(
                "/Team/readme.md", "/Team/readme-moved.md", false, null, null);

        assertThat(copied.created()).isTrue();
        assertThat(copied.item().path()).isEqualTo("/Team/readme-copy.md");
        assertThat(moved.created()).isTrue();
        assertThat(moved.item().path()).isEqualTo("/Team/readme-moved.md");
        assertThat(adapter.copiedPath).isEqualTo("/Team/readme-copy.md");
        assertThat(adapter.movedPath).isEqualTo("/Team/readme-moved.md");
        assertThat(audit.events())
                .extracting(event -> event.action())
                .containsExactly(
                        AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED,
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                        AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED,
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED);
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload())
                .containsEntry("webDavFacadePath", "/dav/files")
                .doesNotContainKeys("providerUrl", "rawProviderPayload", "bearerToken"));
    }

    @Test
    void webDavLockBlocksConflictingWritesUntilMatchingUnlock() {
        // FILES_WEBDAV_LOCK_CONFLICT
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        StubAdapter adapter = new StubAdapter(true);
        FilesFacadeService service = service(adapter, new InMemoryAuditEventPublisher());

        WebDavLockResult lock = service.lockWebDavPath("/Team/readme.md", null);

        assertThatThrownBy(() -> service.putWebDavFile(
                        "/Team/readme.md",
                        "blocked".getBytes(StandardCharsets.UTF_8),
                        "text/markdown",
                        null,
                        null,
                        null))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.LOCKED);
                    assertThat(exception.code()).isEqualTo("files-locked");
                    assertThat(exception.details()).containsEntry("webDavFacadePath", "/dav/files");
                });
        assertThatThrownBy(() -> service.unlockWebDavPath(
                        "/Team/readme.md", "<opaquelocktoken:wrong>"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.LOCKED));

        service.unlockWebDavPath("/Team/readme.md", "<" + lock.token() + ">");
        WebDavMutationResult updated = service.putWebDavFile(
                "/Team/readme.md",
                "unlocked".getBytes(StandardCharsets.UTF_8),
                "text/markdown",
                null,
                null,
                null);
        assertThat(updated.created()).isFalse();
        assertThat(adapter.putPath).isEqualTo("/Team/readme.md");
    }

    @Test
    void webDavPutResponseEtagChangesForSameSizeOverwriteWhenMetadataDoesNotChange() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        StubAdapter adapter = new StubAdapter(true);
        FilesFacadeService service = service(adapter, new InMemoryAuditEventPublisher());

        String initialEtag = service.etagFor("/Team/readme.md");
        WebDavMutationResult first = service.putWebDavFile(
                "/Team/readme.md",
                "bbbbbbbbbbbb".getBytes(StandardCharsets.UTF_8),
                "text/markdown",
                initialEtag,
                null);
        WebDavMutationResult second = service.putWebDavFile(
                "/Team/readme.md",
                "cccccccccccc".getBytes(StandardCharsets.UTF_8),
                "text/markdown",
                first.etag(),
                null);

        assertThat(first.created()).isFalse();
        assertThat(second.created()).isFalse();
        assertThat(first.item().size()).isEqualTo(12L);
        assertThat(second.item().size()).isEqualTo(12L);
        assertThat(first.item().modifiedAt()).isEqualTo(OffsetDateTime.parse("2026-04-26T08:00:00Z"));
        assertThat(second.item().modifiedAt()).isEqualTo(OffsetDateTime.parse("2026-04-26T08:00:00Z"));
        assertThat(first.etag()).isNotEqualTo(initialEtag);
        assertThat(second.etag()).isNotEqualTo(first.etag());
    }

    @Test
    void webDavPutPinsProviderAndPersistsIntentAroundAdapterMutation() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        FilesMutationIntentService intents = mock(FilesMutationIntentService.class);
        OperationIntent createdIntent = mock(OperationIntent.class);
        OperationIntent dispatchingIntent = mock(OperationIntent.class);
        ProviderBinding binding = new ProviderBinding(
                "tenant-default", "files", 4, "test-memory", "secretref:files:stub",
                ProviderBinding.State.ACTIVE, Instant.parse("2026-07-22T02:00:00Z"));
        PinnedMutation created = new PinnedMutation(createdIntent, binding, false);
        PinnedMutation dispatching = new PinnedMutation(dispatchingIntent, binding, false);
        when(createdIntent.state()).thenReturn(OperationIntent.State.CREATED);
        when(dispatchingIntent.state()).thenReturn(OperationIntent.State.DISPATCHING);
        when(dispatchingIntent.operationRef()).thenReturn("operation:files-put-test");
        when(intents.begin(any(Command.class))).thenReturn(created);
        when(intents.dispatch(created)).thenReturn(dispatching);
        when(intents.succeed(any(PinnedMutation.class), any(String.class), any(String.class)))
                .thenReturn(dispatching);
        StubAdapter adapter = new StubAdapter(true);
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        FilesFacadeService service = service(adapter, audit, intents);

        service.putWebDavFile(
                "/Team/readme.md",
                "intent".getBytes(StandardCharsets.UTF_8),
                "text/markdown",
                null,
                null,
                null,
                "files-put-idempotency-0001");

        var command = org.mockito.ArgumentCaptor.forClass(Command.class);
        verify(intents).begin(command.capture());
        assertThat(command.getValue().idempotencyKey()).isEqualTo("files-put-idempotency-0001");
        assertThat(command.getValue().organizationRef()).isEqualTo("tenant-default");
        assertThat(command.getValue().operation()).isEqualTo("webdav-put");
        verify(intents).requireAdapter(created, "test-memory");
        verify(intents).dispatch(created);
        verify(intents).succeed(any(PinnedMutation.class), any(String.class), any(String.class));
        assertThat(adapter.putPath).isEqualTo("/Team/readme.md");
        assertThat(audit.events())
                .extracting(event -> event.action())
                .containsExactly(
                        AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED,
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                        AuditAction.FILES_OPERATION_INTENT_RECORDED);
    }

    @Test
    void guestFileAccessRequiresEffectivePolicyGrantBeforeContextAuthorization() {
        java.util.concurrent.atomic.AtomicBoolean contextChecked = new java.util.concurrent.atomic.AtomicBoolean(false);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwtWithRolesAndGroups(List.of("guest"), List.of()), null));

        assertThatThrownBy(() -> service(
                        new StubAdapter(true),
                        request -> {
                            contextChecked.set(true);
                            return ContextAuthorizationDecision.allow("context would allow");
                        })
                        .upload("/Team", null))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.code()).isEqualTo("capability-policy-blocked");
                    assertThat(exception.details()).containsEntry("requiredCapability", "files.upload");
                    assertThat(exception.details()).containsEntry("diagnosticsRedacted", true);
                });
        assertThat(contextChecked).isFalse();
    }

    @Test
    void filesWebdavDeviceCredentialLifecycleIssuesListsRevokesAndDeniesAfterRevoke() {
        // FILES_WEBDAV_DEVICE_CREDENTIAL_LIFECYCLE
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        FilesFacadeService service = service(new StubAdapter(true), audit);

        var credential = service.createSetupCredential(
                new com.massimotter.weave.backend.model.files.FileSetupCredentialRequest(
                        "Mac Finder",
                        "webdav"));

        assertThat(credential.credentialId()).startsWith("files_device_");
        assertThat(credential.state()).isEqualTo("active");
        assertThat(credential.principalRef()).isEqualTo("user:user-123");
        assertThat(credential.webDavBasePath()).isEqualTo("/dav/files");
        assertThat(credential.secretMaterialReturned()).isTrue();
        assertThat(credential.username()).isEqualTo(credential.credentialId());
        assertThat(credential.secret()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(credential.revocationActions())
                .containsExactly("DELETE /api/files/client-setup/credentials/" + credential.credentialId());
        assertThat(service.setupCredentials().credentials())
                .extracting(com.massimotter.weave.backend.model.files.FileSetupCredentialResponse::credentialId)
                .containsExactly(credential.credentialId());
        assertThat(service.setupCredentials().credentials().get(0).secret()).isNull();
        assertThat(service.requireActiveSetupCredential(credential.credentialId()).state())
                .isEqualTo("active");

        var revoked = service.revokeSetupCredential(credential.credentialId());

        assertThat(revoked.state()).isEqualTo("revoked");
        assertThat(revoked.revokedAt()).isNotNull();
        assertThat(revoked.secretMaterialReturned()).isFalse();
        assertThatThrownBy(() -> service.requireActiveSetupCredential(credential.credentialId()))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("files-setup-credential-revoked");
                    assertThat(exception.details())
                            .containsEntry("webDavFacadePath", "/dav/files")
                            .containsEntry("diagnosticsRedacted", true);
                    assertThat(exception.getMessage()).doesNotContain("Nextcloud", "Bearer", "app_password");
                });
        assertThat(audit.events())
                .extracting(event -> event.action())
                .containsExactly(
                        AuditAction.FILES_DEVICE_CREDENTIAL_ISSUED,
                        AuditAction.FILES_DEVICE_CREDENTIAL_REVOKED);
        assertThat(audit.events()).allSatisfy(event -> assertThat(event.payload())
                .containsEntry("domain", "files")
                .containsEntry("webDavFacadePath", "/dav/files")
                .containsEntry("secretMaterialReturned", "[redacted]")
                .containsEntry("supportSafe", true)
                .doesNotContainKeys("providerUrl", "rawProviderPayload", "bearerToken", "secretValue"));
    }

    @Test
    void configurationPropertiesBindSeededMembershipsIntoAuthorizationPort() {
        contextRunner
                .withPropertyValues(
                        "weave.context.authorization.memberships[0].tenant-id=tenant-default",
                        "weave.context.authorization.memberships[0].context-id=workspace-default",
                        "weave.context.authorization.memberships[0].principal-ref=user:test",
                        "weave.context.authorization.memberships[0].role=MEMBER",
                        "weave.context.authorization.memberships[0].source=test-seed")
                .run(context -> {
                    ContextAuthorizationPort port = context.getBean(ContextAuthorizationPort.class);

                    assertThat(port.check(new ContextAuthorizationRequest(
                                    "tenant-default",
                                    "workspace-default",
                                    "user:test",
                                    ContextPermission.VIEW)).allowed())
                            .isTrue();
                    assertThat(port.check(new ContextAuthorizationRequest(
                                    "tenant-default",
                                    "workspace-default",
                                    "user:other",
                                    ContextPermission.VIEW)).allowed())
                            .isFalse();
                });
    }

    @Test
    void jwtWithoutTenantClaimIsRejectedBeforeSeededAuthorizationCanGrantAccess() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject("user-123")
                        .issuer("https://auth.example.invalid/realms/acme")
                        .claim("resource_access", java.util.Map.of("weave-app", java.util.Map.of("roles", java.util.List.of("member"))))
                        .build(),
                null));

        assertThatThrownBy(() -> service(new StubAdapter(true)).list("/Team"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.details()).containsEntry("reason", "tenant claim is missing");
                });
    }

    @Test
    void configurablePrincipalClaimSupportsDeterministicLocalContextSeeds() {
        AtomicReference<ContextAuthorizationRequest> captured = new AtomicReference<>();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject("opaque-keycloak-subject")
                        .issuer("https://auth.example.invalid/realms/acme")
                        .claim("preferred_username", "test")
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("resource_access", java.util.Map.of("weave-app", java.util.Map.of("roles", java.util.List.of("member"))))
                        .build(),
                null));

        FilesFacadeService service = service(
                new StubAdapter(true),
                request -> {
                    captured.set(request);
                    return ContextAuthorizationDecision.allow("seeded local membership");
                },
                new ContextAuthorizationProperties(
                        "weave_tenant_id",
                        "tenant_id",
                        "tenant-default",
                        "preferred_username",
                        "user:",
                        List.of(),
                        List.of(),
                        List.of()));

        assertThat(service.list("/").items()).hasSize(1);
        assertThat(captured.get().principalRef()).isEqualTo("user:test");
    }

    private FilesFacadeService service(FilesProviderPort adapter) {
        return service(adapter, request -> ContextAuthorizationDecision.allow("test allow"));
    }

    private FilesFacadeService service(FilesProviderPort adapter, InMemoryAuditEventPublisher auditEventPublisher) {
        return service(adapter, request -> ContextAuthorizationDecision.allow("test allow"), auditEventPublisher);
    }

    private FilesFacadeService service(
            FilesProviderPort adapter,
            InMemoryAuditEventPublisher auditEventPublisher,
            FilesMutationIntentService intents) {
        return new FilesFacadeService(
                provider(adapter),
                request -> ContextAuthorizationDecision.allow("test allow"),
                defaultContextAuthorizationProperties(),
                workspaceCapabilityService(),
                new DeviceCredentialService(new InMemoryDeviceCredentialRepository()),
                auditEventPublisher,
                new FilesLockService(new TestFilesAuthorityRepository(), Clock.systemUTC()),
                intents);
    }

    private FilesFacadeService service(FilesProviderPort adapter, ContextAuthorizationPort contextAuthorizationPort) {
        return service(adapter, contextAuthorizationPort, new InMemoryAuditEventPublisher());
    }

    private FilesFacadeService service(
            FilesProviderPort adapter,
            ContextAuthorizationPort contextAuthorizationPort,
            InMemoryAuditEventPublisher auditEventPublisher) {
        return service(adapter, contextAuthorizationPort, defaultContextAuthorizationProperties(), auditEventPublisher);
    }

    private FilesFacadeService service(
            FilesProviderPort adapter,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties) {
        return service(adapter, contextAuthorizationPort, contextAuthorizationProperties, new InMemoryAuditEventPublisher());
    }

    private FilesFacadeService service(
            FilesProviderPort adapter,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            InMemoryAuditEventPublisher auditEventPublisher) {
        return new FilesFacadeService(
                provider(adapter),
                contextAuthorizationPort,
                contextAuthorizationProperties,
                workspaceCapabilityService(),
                new DeviceCredentialService(new InMemoryDeviceCredentialRepository()),
                auditEventPublisher,
                new FilesLockService(new TestFilesAuthorityRepository(), Clock.systemUTC()));
    }

    private ContextAuthorizationProperties defaultContextAuthorizationProperties() {
        return new ContextAuthorizationProperties(null, null, null, null, null, List.of(), List.of(), List.of());
    }

    private static final class TestFilesAuthorityRepository implements FilesAuthorityRepository {
        private final Map<String, FileLockRecord> locks = new ConcurrentHashMap<>();

        @Override
        public CanonicalFileRecord save(CanonicalFileRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CanonicalFileRecord> findByPath(String organizationRef, String spaceRef, FilePath path) {
            return Optional.empty();
        }

        @Override
        public Optional<CanonicalFileRecord> findById(String organizationRef, String spaceRef, FileId id) {
            return Optional.empty();
        }

        @Override
        public CanonicalFileRecord move(String organizationRef, String spaceRef, FileId id,
                FilePath expectedPath, FilePath destination, Instant movedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized FileLockRecord acquireLock(FileLockRecord requested, Instant now) {
            String key = key(requested.organizationRef(), requested.spaceRef(), requested.path());
            FileLockRecord active = locks.get(key);
            if (active != null && active.expiresAt().isAfter(now)) {
                throw new LockConflictException(requested.path());
            }
            long fence = active == null ? 1 : active.fence() + 1;
            FileLockRecord stored = new FileLockRecord(requested.organizationRef(), requested.spaceRef(),
                    requested.path(), requested.tokenDigest(), requested.ownerRef(), fence,
                    requested.expiresAt(), requested.createdAt());
            locks.put(key, stored);
            return stored;
        }

        @Override
        public Optional<FileLockRecord> activeLock(
                String organizationRef, String spaceRef, FilePath path, Instant now) {
            FileLockRecord lock = locks.get(key(organizationRef, spaceRef, path));
            return lock != null && lock.expiresAt().isAfter(now) ? Optional.of(lock) : Optional.empty();
        }

        @Override
        public List<FileLockRecord> activeLocks(String organizationRef, String spaceRef, Instant now) {
            return locks.values().stream()
                    .filter(lock -> lock.organizationRef().equals(organizationRef))
                    .filter(lock -> lock.spaceRef().equals(spaceRef))
                    .filter(lock -> lock.expiresAt().isAfter(now))
                    .toList();
        }

        @Override
        public synchronized void releaseLock(String organizationRef, String spaceRef, FilePath path,
                String tokenDigest, String ownerRef, Instant now) {
            String key = key(organizationRef, spaceRef, path);
            FileLockRecord active = locks.get(key);
            if (active == null || !active.tokenDigest().equals(tokenDigest) || !active.ownerRef().equals(ownerRef)) {
                throw new LockConflictException(path);
            }
            locks.remove(key);
        }

        @Override
        public synchronized void moveLock(String organizationRef, String spaceRef, FilePath source,
                FilePath destination, String tokenDigest, String ownerRef, Instant now) {
            String sourceKey = key(organizationRef, spaceRef, source);
            FileLockRecord active = locks.get(sourceKey);
            if (active == null || !active.tokenDigest().equals(tokenDigest) || !active.ownerRef().equals(ownerRef)) {
                throw new LockConflictException(source);
            }
            locks.remove(sourceKey);
            locks.put(key(organizationRef, spaceRef, destination), new FileLockRecord(
                    organizationRef, spaceRef, destination, active.tokenDigest(), active.ownerRef(),
                    active.fence(), active.expiresAt(), active.createdAt()));
        }

        private String key(String organizationRef, String spaceRef, FilePath path) {
            return organizationRef + "\u0000" + spaceRef + "\u0000" + path.value();
        }
    }

    private WorkspaceCapabilityService workspaceCapabilityService() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        return new WorkspaceCapabilityService(
                properties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));
    }

    private Jwt jwt() {
        return jwtWithRolesAndGroups(List.of("member"), List.of("weave-file-uploaders"));
    }

    private Jwt jwtWithRolesAndGroups(List<String> roles, List<String> groups) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "tenant-default")
                .claim("resource_access", java.util.Map.of("weave-app", java.util.Map.of("roles", roles)))
                .claim("groups", groups)
                .build();
    }

    @Configuration
    @EnableConfigurationProperties(ContextAuthorizationProperties.class)
    @Import(ContextAuthorizationConfiguration.class)
    static class ContextAuthorizationTestConfiguration {
    }

    private ObjectProvider<FilesProviderPort> provider(FilesProviderPort adapter) {
        return new ObjectProvider<>() {
            @Override
            public FilesProviderPort getObject(Object... args) {
                return adapter;
            }

            @Override
            public FilesProviderPort getIfAvailable() {
                return adapter;
            }

            @Override
            public FilesProviderPort getIfUnique() {
                return adapter;
            }

            @Override
            public FilesProviderPort getObject() {
                return adapter;
            }

            @Override
            public Iterator<FilesProviderPort> iterator() {
                return adapter == null ? List.<FilesProviderPort>of().iterator() : List.of(adapter).iterator();
            }
        };
    }

    private static class StubAdapter implements FilesProviderPort {

        private final boolean configured;
        private final Map<String, byte[]> contentByPath = new HashMap<>(Map.of(
                "/Team/readme.md", "aaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8)));
        private final Set<String> collections = new java.util.HashSet<>(Set.of("/", "/Team"));
        private String putPath;
        private String createdFolderPath;
        private String deletedPath;
        private String copiedPath;
        private String movedPath;
        private int listWithVersionTokenCalls;
        private int versionTokenCalls;

        private StubAdapter(boolean configured) {
            this.configured = configured;
        }

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public ProviderReadiness readiness() {
            return configured
                    ? ProviderReadiness.ready("files-storage-ready")
                    : ProviderReadiness.degraded("files-storage-not-configured");
        }

        @Override
        public ProviderConformanceProfile conformanceProfile() {
            return new ProviderConformanceProfile(
                    "files",
                    "test-memory",
                    Set.of("list", "read", "write", "create-collection", "delete"),
                    Map.of(),
                    true,
                    true,
                    true);
        }

        @Override
        public VersionedListing list(FilePath path) {
            listWithVersionTokenCalls++;
            String normalized = path.value();
            List<FileObject> children = contentByPath.entrySet().stream()
                    .filter(entry -> parent(entry.getKey()).equals(normalized))
                    .map(entry -> file(entry.getKey(), entry.getValue()))
                    .toList();
            if (children.isEmpty() && "/".equals(normalized)) {
                children = List.of(file("/readme.md", "aaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8)));
            }
            Map<FilePath, FileVersion> childVersions = new HashMap<>();
            for (FileObject item : children) {
                byte[] content = contentByPath.get(item.path().value());
                if (content != null) {
                    childVersions.put(item.path(), version(content));
                }
            }
            return new VersionedListing(
                    new FileListing(path, children, FileQuota.unknown()),
                    FileVersion.unknown(),
                    childVersions);
        }

        @Override
        public Optional<VersionedFile> find(FilePath path) {
            versionTokenCalls++;
            byte[] content = contentByPath.get(path.value());
            if (content != null) {
                return Optional.of(new VersionedFile(file(path.value(), content), version(content)));
            }
            if (collections.contains(path.value())) {
                return Optional.of(new VersionedFile(collection(path.value()), FileVersion.unknown()));
            }
            return Optional.empty();
        }

        @Override
        public FileContent read(FileId id) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public FileObject write(FileWrite write) {
            putPath = write.path().value();
            contentByPath.put(putPath, write.bytes());
            return file(putPath, write.bytes(), write.mediaType(), Instant.parse("2026-04-26T08:05:00Z"));
        }

        @Override
        public FileObject createCollection(FilePath path) {
            createdFolderPath = path.value();
            collections.add(createdFolderPath);
            return collection(createdFolderPath);
        }

        @Override
        public FileObject copy(FilePath source, FilePath destination, boolean overwrite) {
            byte[] content = contentByPath.get(source.value());
            if (content == null) {
                throw new IllegalArgumentException("source file is missing");
            }
            copiedPath = destination.value();
            contentByPath.put(copiedPath, content.clone());
            return file(copiedPath, contentByPath.get(copiedPath));
        }

        @Override
        public FileObject move(FilePath source, FilePath destination, boolean overwrite) {
            byte[] content = contentByPath.remove(source.value());
            if (content == null) {
                throw new IllegalArgumentException("source file is missing");
            }
            movedPath = destination.value();
            contentByPath.put(movedPath, content);
            return file(movedPath, content);
        }

        @Override
        public void delete(FilePath path, FileVersion expectedVersion) {
            deletedPath = path.value();
            contentByPath.remove(deletedPath);
            collections.remove(deletedPath);
        }

        private FileObject file(String path, byte[] content) {
            return file(path, content, "text/markdown", Instant.parse("2026-04-26T08:00:00Z"));
        }

        private FileObject file(String path, byte[] content, String mediaType, Instant modifiedAt) {
            return new FileObject(
                    new FileId("files:" + path),
                    new FilePath(path),
                    Kind.FILE,
                    content.length,
                    mediaType,
                    modifiedAt,
                    false);
        }

        private FileObject collection(String path) {
            return new FileObject(
                    new FileId("files:" + path),
                    new FilePath(path),
                    Kind.COLLECTION,
                    0,
                    null,
                    Instant.parse("2026-04-26T08:05:00Z"),
                    false);
        }

        private FileVersion version(byte[] content) {
            return new FileVersion(new String(content, StandardCharsets.UTF_8));
        }

        private String parent(String path) {
            int separator = path.lastIndexOf('/');
            return separator <= 0 ? "/" : path.substring(0, separator);
        }
    }

    private static final class ProviderErrorAdapter extends StubAdapter {

        private ProviderErrorAdapter() {
            super(true);
        }

        @Override
        public VersionedListing list(FilePath path) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "nextcloud-unavailable",
                    "Nextcloud WebDAV failed at https://files.example.invalid/remote.php/dav",
                    java.util.Map.of(
                            "module", "files",
                            "operation", "list-files",
                            "downstreamStatus", 503,
                            "providerUrl", "https://files.example.invalid/remote.php/dav"));
        }
    }
}
