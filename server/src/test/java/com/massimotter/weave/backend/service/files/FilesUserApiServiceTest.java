package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilesUserApiServiceTest {
    private final ContextAuthorizationProperties contextProperties = new ContextAuthorizationProperties(
            null, null, null, null, null, null, null, null);
    private final ContextAuthorizationPort authorization = mock(ContextAuthorizationPort.class);
    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
    private final FilesProviderResolver resolver = mock(FilesProviderResolver.class);
    private final FilesProviderPort provider = mock(FilesProviderPort.class);
    private final FilesUserResourceRepository resources = mock(FilesUserResourceRepository.class);
    private final FilesMutationIntentService intents = mock(FilesMutationIntentService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final AuditEventPublisher auditEvents = mock(AuditEventPublisher.class);
    private final FilesUserApiService service = new FilesUserApiService(
            OrganizationIdentityContextResolver.configured(contextProperties), contextProperties,
            authorization, capabilities, bindings, resolver, resources, intents, transactions, auditEvents);
    private final ProviderBinding binding = new ProviderBinding("org-a", "files", 4,
            "nextcloud-webdav", "config-a", ProviderBinding.State.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"));

    @BeforeEach
    void allowSpace() {
        when(authorization.check(any())).thenReturn(ContextAuthorizationDecision.allow("member"));
        when(bindings.current("org-a", "files")).thenReturn(Optional.of(binding));
        when(resolver.pinned(binding, "org-a", "workspace-default")).thenReturn(provider);
    }

    @Test
    void serviceAccountVisibleButUnmappedProviderObjectIsNeverListedOrAddressable() {
        when(resources.activeChildren("org-a", FilesUserApiService.ROOT_ID)).thenReturn(List.of());
        var listing = service.list(jwt("org-a", "alice"), null);
        assertThat(listing.parentFileId()).isEqualTo(FilesUserApiService.ROOT_ID);
        assertThat(listing.allowedActions()).containsExactly("listChildren");
        assertThat(listing.items()).isEmpty();
        verify(provider, never()).list(any());
        verify(provider, never()).find(any());

        when(resources.find("org-a", "files:/secret.txt")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.inspect(jwt("org-a", "alice"), "files:/secret.txt"))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status().value()).isEqualTo(404));
        verify(provider, never()).read(any());
    }

    @Test
    void rootListingAdvertisesOnlySupportedCurrentMemberActions() {
        when(resources.activeChildren("org-a", FilesUserApiService.ROOT_ID)).thenReturn(List.of());
        when(provider.supportsStableObjectRefs()).thenReturn(true);
        when(provider.supportsAtomicCollectionCreate()).thenReturn(true);
        when(provider.supportsConditionalWrite()).thenReturn(true);
        when(provider.supportsBoundedRead()).thenReturn(true);
        when(provider.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files", "nextcloud-webdav", java.util.Set.of("create_collection"),
                java.util.Map.of(), true, true, true));

        assertThat(service.list(jwt("org-a", "alice"), null).allowedActions())
                .containsExactly("listChildren", "createFolder", "upload")
                .doesNotContain("share", "move", "copy", "delete");
    }

    @Test
    void ownerDownloadUsesPrivateMappingAndPreservesExactBinary() {
        FilesUserResource owned = file("alice", 4);
        when(resources.find("org-a", owned.fileId())).thenReturn(Optional.of(owned));
        when(bindings.mappingByCanonicalId("org-a", "files", 4, owned.fileId()))
                .thenReturn(Optional.of(mapping(owned)));
        FileObject object = new FileObject(new FileId("files:/x.bin"), new FilePath("/x.bin"), Kind.FILE,
                4, "application/octet-stream", Instant.parse("2026-01-01T00:00:00Z"), false);
        when(provider.find(new FilePath("/x.bin"))).thenReturn(Optional.of(
                new VersionedFile(object, new FileVersion("\"provider-v1\""))));
        when(provider.providerObjectRef(new FilePath("/x.bin")))
                .thenReturn(Optional.of("nextcloud-fileid:42"));
        byte[] bytes = {0, 1, (byte) 255, 42};
        when(provider.readBoundedIfVersion(new FileId("files:/x.bin"),
                FilesUserApiService.MAX_DOWNLOAD_BYTES, new FileVersion("\"provider-v1\"")))
                .thenReturn(new FileContent(object, bytes));

        var result = service.download(jwt("org-a", "alice"), owned.fileId());
        assertThat(result.bytes()).containsExactly(bytes);
        assertThat(result.digest()).isEqualTo("sha-256=:LIsYu9unKp0PjpkQekKzXKNkVnwLDvDo08FKPLgglhY=:");
        assertThat(result.etag()).isEqualTo(
                "\"sha256-2c8b18bbdba72a9d0f8e99107a42b35ca364567c0b0ef0e8d3c14a3cb8209616\"");
    }

    @Test
    void pathReuseCannotInheritPreviousWeaveFileId() {
        FilesUserResource owned = file("alice", 4);
        when(resources.find("org-a", owned.fileId())).thenReturn(Optional.of(owned));
        when(bindings.mappingByCanonicalId("org-a", "files", 4, owned.fileId()))
                .thenReturn(Optional.of(mapping(owned)));
        FileObject replacementAtSamePath = new FileObject(new FileId("files:/x.bin"),
                new FilePath("/x.bin"), Kind.FILE, 1, "text/plain", Instant.now(), false);
        when(provider.find(new FilePath("/x.bin"))).thenReturn(Optional.of(
                new VersionedFile(replacementAtSamePath, new FileVersion("\"replacement\""))));
        when(provider.providerObjectRef(new FilePath("/x.bin")))
                .thenReturn(Optional.of("nextcloud-fileid:99"));

        assertThatThrownBy(() -> service.download(jwt("org-a", "alice"), owned.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("files-mapping-stale"));
        verify(provider, never()).readBoundedIfVersion(any(), eq(FilesUserApiService.MAX_DOWNLOAD_BYTES), any());
    }

    @Test
    void observedPathReplacementDuringDownloadFailsClosed() {
        FilesUserResource owned = file("alice", 4);
        when(resources.find("org-a", owned.fileId())).thenReturn(Optional.of(owned));
        when(bindings.mappingByCanonicalId("org-a", "files", 4, owned.fileId()))
                .thenReturn(Optional.of(mapping(owned)));
        FileObject object = new FileObject(new FileId("files:/x.bin"), new FilePath("/x.bin"), Kind.FILE,
                4, "application/octet-stream", Instant.parse("2026-01-01T00:00:00Z"), false);
        when(provider.find(new FilePath("/x.bin"))).thenReturn(Optional.of(
                new VersionedFile(object, new FileVersion("\"provider-v1\""))));
        when(provider.providerObjectRef(new FilePath("/x.bin")))
                .thenReturn(Optional.of("nextcloud-fileid:42"), Optional.of("nextcloud-fileid:99"));
        when(provider.readBoundedIfVersion(object.id(), FilesUserApiService.MAX_DOWNLOAD_BYTES,
                new FileVersion("\"provider-v1\""))).thenReturn(new FileContent(object, new byte[] {1, 2, 3, 4}));

        assertThatThrownBy(() -> service.download(jwt("org-a", "alice"), owned.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("files-mapping-stale"));
        verify(provider).readBoundedIfVersion(object.id(), FilesUserApiService.MAX_DOWNLOAD_BYTES,
                new FileVersion("\"provider-v1\""));
    }

    @Test
    void providerFailureDoesNotExposePrivatePathOrActorInUserError() {
        FilesUserResource owned = file("alice", 4);
        when(resources.find("org-a", owned.fileId())).thenReturn(Optional.of(owned));
        when(bindings.mappingByCanonicalId("org-a", "files", 4, owned.fileId()))
                .thenReturn(Optional.of(mapping(owned)));
        when(provider.find(new FilePath("/x.bin"))).thenThrow(new ApiErrorException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "nextcloud-actor-failed",
                "Nextcloud actor secret for /x.bin is invalid",
                Map.of("providerPath", "/x.bin", "actor", "private-actor")));

        assertThatThrownBy(() -> service.inspect(jwt("org-a", "alice"), owned.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(503);
                    assertThat(error.code()).isEqualTo("files-provider-unavailable");
                    assertThat(error.getMessage()).doesNotContain("Nextcloud", "private-actor", "/x.bin");
                    assertThat(error.details().toString()).doesNotContain("private-actor", "/x.bin");
                });
    }

    @Test
    void otherMemberCannotInspectOrDownloadOwnerResource() {
        FilesUserResource owned = file("alice", 4);
        when(resources.find("org-a", owned.fileId())).thenReturn(Optional.of(owned));
        assertThatThrownBy(() -> service.download(jwt("org-a", "bob"), owned.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status().value()).isEqualTo(404));
        verify(provider, never()).find(any());
    }

    @Test
    void anotherOrganizationCannotResolveTheSameOpaqueFileId() {
        FilesUserResource owned = file("alice", 4);
        when(resources.find("org-b", owned.fileId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inspect(jwt("org-b", "alice"), owned.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status().value()).isEqualTo(404));
        verify(resources).find("org-b", owned.fileId());
        verify(provider, never()).find(any());
    }

    @Test
    void bindingRevisionChangeCannotSilentlyMintOrResolveAnotherFileId() {
        FilesUserResource owned = file("alice", 3);
        when(resources.find("org-a", owned.fileId())).thenReturn(Optional.of(owned));
        assertThatThrownBy(() -> service.inspect(jwt("org-a", "alice"), owned.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("files-binding-identity-unavailable"));
        verify(provider, never()).find(any());
    }

    @Test
    void createRequiresAtomicAbsentNamePreconditionBeforeProviderCall() {
        assertThatThrownBy(() -> service.createFolder(jwt("org-a", "alice"),
                FilesUserApiService.ROOT_ID, "new", null, "0123456789abcdef"))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status().value()).isEqualTo(428));
        verify(provider, never()).createCollectionIfAbsent(any());
        verify(intents, never()).beginUserApi(any());
    }

    @Test
    void binaryUploadPublishesAnOpaqueFileIdOnlyAfterExactReadback() {
        OperationIntent intent = mock(OperationIntent.class);
        when(intent.operationRef()).thenReturn("operation:upload");
        var mutation = new FilesMutationIntentService.PinnedMutation(intent, binding, false);
        when(intents.beginUserApi(any())).thenReturn(mutation);
        when(intents.dispatch(mutation)).thenReturn(mutation);
        when(provider.supportsStableObjectRefs()).thenReturn(true);
        when(provider.supportsConditionalWrite()).thenReturn(true);
        when(provider.supportsBoundedRead()).thenReturn(true);
        FilePath path = new FilePath("/payload.bin");
        FileObject object = new FileObject(new FileId(FilePathCodec.toId(path.value())), path, Kind.FILE,
                4, "application/octet-stream", Instant.parse("2026-01-01T00:00:00Z"), false);
        when(resources.findActivePath("org-a", 4, path.value())).thenReturn(Optional.empty());
        when(provider.find(path)).thenReturn(Optional.empty(),
                Optional.of(new VersionedFile(object, new FileVersion("\"v1\""))));
        when(provider.writeIfAbsent(any())).thenReturn(
                new FilesProviderPort.CreatedObject(object, "nextcloud-object:00000042ocabc"));
        byte[] bytes = {0, 1, (byte) 255, 42};
        when(provider.readBounded(object.id(), FilesUserApiService.MAX_DOWNLOAD_BYTES))
                .thenReturn(new FileContent(object, bytes));
        when(provider.providerObjectRef(path)).thenReturn(Optional.of("nextcloud-object:00000042ocabc"));
        when(bindings.mappingByCanonicalId("org-a", "files", 4, "file:upload"))
                .thenReturn(Optional.of(new ProviderObjectMapping("org-a", "files", 4,
                        "file:upload", "nextcloud-object:00000042ocabc", "weave-user-http-create",
                        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"))));
        when(resources.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        var result = service.upload(jwt("org-a", "alice"), FilesUserApiService.ROOT_ID,
                "payload.bin", "application/octet-stream", bytes, "*", "0123456789abcdef");
        assertThat(result.fileId()).isEqualTo("file:upload");
        assertThat(result.displayPath()).isEqualTo("/payload.bin");
        assertThat(result.allowedActions()).contains("download")
                .doesNotContain("updateContent", "share", "move", "copy", "delete");
        assertThat(result.toString()).doesNotContain("nextcloud-object:00000042ocabc");
        verify(provider).writeIfAbsent(org.mockito.ArgumentMatchers.argThat(write ->
                write.path().equals(path) && java.util.Arrays.equals(write.bytes(), bytes)));
        verify(bindings).saveMapping(org.mockito.ArgumentMatchers.argThat(mapping ->
                mapping.canonicalObjectId().equals("file:upload")
                        && mapping.providerObjectRef().equals("nextcloud-object:00000042ocabc")));
        verify(intents).succeed(eq(mutation), eq(result.fileId() + "\n" + result.revision()), any());
    }

    @Test
    void weakContentValidatorCannotAuthorizeUpdate() {
        assertThatThrownBy(() -> service.update(jwt("org-a", "alice"), "file:stable",
                "text/plain", new byte[] {1}, "W/\"sha256-example\"", "0123456789abcdef"))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status().value()).isEqualTo(412));
        verify(provider, never()).writeIfVersion(any(), any());
        verify(intents, never()).beginUserApi(any());
    }

    @Test
    void pathConditionalWriteWithoutAtomicObjectIdentityNeverDispatches() {
        FilesUserResource owned = file("alice", 4);
        when(resources.find("org-a", owned.fileId())).thenReturn(Optional.of(owned));
        when(provider.supportsBoundedRead()).thenReturn(true);
        when(provider.supportsConditionalWrite()).thenReturn(true);

        assertThatThrownBy(() -> service.update(jwt("org-a", "alice"), owned.fileId(),
                "text/plain", new byte[] {1}, "\"sha256-example\"", "0123456789abcdef"))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(503);
                    assertThat(error.code()).isEqualTo("files-identity-bound-write-unavailable");
                });
        verify(provider, never()).find(any());
        verify(provider, never()).readBoundedIfVersion(any(), eq(FilesUserApiService.MAX_DOWNLOAD_BYTES), any());
        verify(provider, never()).writeIfVersion(any(), any());
        verify(intents, never()).beginUserApi(any());
    }

    @Test
    void successfulIntentRetryNeverReturnsChangedCurrentMetadataAsRecordedResult() {
        OperationIntent intent = mock(OperationIntent.class);
        when(intent.operationRef()).thenReturn("operation:123");
        when(intent.state()).thenReturn(OperationIntent.State.SUCCEEDED);
        when(intent.resultDigest()).thenReturn("sha256:recorded-old-revision");
        when(intents.beginUserApi(any())).thenReturn(
                new FilesMutationIntentService.PinnedMutation(intent, binding, true));
        when(provider.supportsStableObjectRefs()).thenReturn(true);
        when(provider.supportsAtomicCollectionCreate()).thenReturn(true);
        when(provider.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files", "nextcloud-webdav", java.util.Set.of("create_collection"),
                java.util.Map.of(), true, true, true));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FilesUserResource folder = new FilesUserResource("org-a", "file:123", 4,
                "workspace-default", FilesUserApiService.ROOT_ID, "/folder", Kind.COLLECTION,
                "user:alice", FilesUserResource.State.ACTIVE, now, now);
        when(resources.find("org-a", "file:123")).thenReturn(Optional.of(folder));
        when(bindings.mappingByCanonicalId("org-a", "files", 4, "file:123"))
                .thenReturn(Optional.of(new ProviderObjectMapping("org-a", "files", 4,
                        "file:123", "nextcloud-fileid:42", "weave-user-http-create", now, now)));
        FileObject item = new FileObject(new FileId("files:/folder"), new FilePath("/folder"),
                Kind.COLLECTION, 0, null, now, false);
        when(provider.find(new FilePath("/folder")))
                .thenReturn(Optional.of(new VersionedFile(item, new FileVersion("\"new-revision\""))));
        when(provider.providerObjectRef(new FilePath("/folder")))
                .thenReturn(Optional.of("nextcloud-fileid:42"));

        assertThatThrownBy(() -> service.createFolder(jwt("org-a", "alice"),
                FilesUserApiService.ROOT_ID, "folder", "*", "0123456789abcdef"))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("files-idempotent-result-changed"));
        verify(provider, never()).createCollectionIfAbsent(any());
    }

    @Test
    void successfulIntentRetryReturnsRecordedResultAcrossDatabaseTimestampRounding() {
        OperationIntent intent = mock(OperationIntent.class);
        when(intent.operationRef()).thenReturn("operation:123");
        when(intent.state()).thenReturn(OperationIntent.State.SUCCEEDED);
        when(intents.beginUserApi(any())).thenReturn(
                new FilesMutationIntentService.PinnedMutation(intent, binding, true));
        when(provider.supportsStableObjectRefs()).thenReturn(true);
        when(provider.supportsAtomicCollectionCreate()).thenReturn(true);
        when(provider.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files", "nextcloud-webdav", java.util.Set.of("create_collection"),
                java.util.Map.of(), true, true, true));
        Instant rounded = Instant.parse("2026-01-01T00:00:00Z");
        FilesUserResource firstProjection = new FilesUserResource("org-a", "file:123", 4,
                "workspace-default", FilesUserApiService.ROOT_ID, "/folder", Kind.COLLECTION,
                "user:alice", FilesUserResource.State.ACTIVE, rounded, rounded.plusNanos(123));
        FilesUserResource databaseProjection = new FilesUserResource("org-a", "file:123", 4,
                "workspace-default", FilesUserApiService.ROOT_ID, "/folder", Kind.COLLECTION,
                "user:alice", FilesUserResource.State.ACTIVE, rounded, rounded);
        when(resources.find("org-a", "file:123"))
                .thenReturn(Optional.of(firstProjection), Optional.of(databaseProjection));
        when(bindings.mappingByCanonicalId("org-a", "files", 4, "file:123"))
                .thenReturn(Optional.of(new ProviderObjectMapping("org-a", "files", 4,
                        "file:123", "nextcloud-fileid:42", "weave-user-http-create", rounded, rounded)));
        FileObject item = new FileObject(new FileId("files:/folder"), new FilePath("/folder"),
                Kind.COLLECTION, 0, null, rounded, false);
        when(provider.find(new FilePath("/folder")))
                .thenReturn(Optional.of(new VersionedFile(item, new FileVersion("\"v1\""))));
        when(provider.providerObjectRef(new FilePath("/folder")))
                .thenReturn(Optional.of("nextcloud-fileid:42"));
        var recorded = service.inspect(jwt("org-a", "alice"), "file:123");
        when(intent.resultDigest()).thenReturn(FilesMutationIntentService.digest(
                recorded.fileId() + "\n" + recorded.revision()));

        var replay = service.createFolder(jwt("org-a", "alice"),
                FilesUserApiService.ROOT_ID, "folder", "*", "0123456789abcdef");
        assertThat(replay.fileId()).isEqualTo(recorded.fileId());
        assertThat(replay.revision()).isEqualTo(recorded.revision());
        verify(provider, never()).createCollectionIfAbsent(any());
    }

    @Test
    void currentReadOnlyGrantDoesNotAdvertiseMutationOrShareActions() {
        when(authorization.check(any())).thenAnswer(invocation -> {
            var request = (com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest)
                    invocation.getArgument(0);
            return request.permission() == com.massimotter.weave.backend.context.authz.ContextPermission.VIEW
                    ? ContextAuthorizationDecision.allow("read")
                    : ContextAuthorizationDecision.deny("read-only");
        });
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        FilesUserResource folder = new FilesUserResource("org-a", "file:folder", 4,
                "workspace-default", FilesUserApiService.ROOT_ID, "/folder", Kind.COLLECTION,
                "user:alice", FilesUserResource.State.ACTIVE, now, now);
        when(resources.find("org-a", "file:folder")).thenReturn(Optional.of(folder));
        when(bindings.mappingByCanonicalId("org-a", "files", 4, "file:folder"))
                .thenReturn(Optional.of(new ProviderObjectMapping("org-a", "files", 4,
                        "file:folder", "nextcloud-fileid:42", "weave-user-http-create", now, now)));
        FileObject item = new FileObject(new FileId("files:/folder"), new FilePath("/folder"),
                Kind.COLLECTION, 0, null, now, false);
        when(provider.find(new FilePath("/folder")))
                .thenReturn(Optional.of(new VersionedFile(item, new FileVersion("\"v1\""))));
        when(provider.providerObjectRef(new FilePath("/folder")))
                .thenReturn(Optional.of("nextcloud-fileid:42"));
        when(provider.supportsStableObjectRefs()).thenReturn(true);
        when(provider.supportsConditionalWrite()).thenReturn(true);
        when(provider.supportsBoundedRead()).thenReturn(true);
        when(provider.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files", "nextcloud-webdav", java.util.Set.of("create_collection"),
                java.util.Map.of(), true, true, true));

        assertThat(service.inspect(jwt("org-a", "alice"), "file:folder").allowedActions())
                .containsExactly("inspect", "listChildren")
                .doesNotContain("createFolder", "upload", "share", "delete", "move", "copy");
    }

    private FilesUserResource file(String owner, long revision) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new FilesUserResource("org-a", "file:stable", revision, "workspace-default",
                FilesUserApiService.ROOT_ID, "/x.bin", Kind.FILE, "user:" + owner,
                FilesUserResource.State.ACTIVE, now, now);
    }

    private ProviderObjectMapping mapping(FilesUserResource resource) {
        return new ProviderObjectMapping("org-a", "files", 4, resource.fileId(),
                "nextcloud-fileid:42", "weave-user-http-create", resource.createdAt(), resource.modifiedAt());
    }

    private Jwt jwt(String org, String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(subject)
                .claim("weave_tenant_id", org)
                .claim("azp", "weave-app")
                .build();
    }
}
