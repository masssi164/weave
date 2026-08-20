package com.massimotter.weave.backend.service;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.JpaAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.NativePinnedMutation;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.PreparedMutation;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CorruptMutationStateException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.LockConflictException;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.ContentProfile;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Ingress;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort.NativeLockMove;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort.NativeResult;
import com.massimotter.weave.backend.files.port.ReplayableFileContent;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.application.OperationIntentService.BeginCommand;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import com.massimotter.weave.backend.security.device.InMemoryDeviceCredentialRepository;
import com.massimotter.weave.backend.service.files.WebDavPutRequest;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class FilesFacadeNativeAuditIdempotencyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-20T08:00:00Z");
    private static final String CONTENT_DIGEST = "sha256:" + "a".repeat(64);
    private static final String PLAN_DIGEST = "sha256:" + "b".repeat(64);
    private static final String IDEMPOTENCY_KEY = "files-native-audit-0001";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulNativeReplayReusesOneStableJpaAuditEvent() {
        JpaAuditEventPublisher audit = auditPublisher("files_native_audit_replay");
        NativeFlow flow = nativeFlow("replay", audit);
        OperationIntent created = intent("replay", OperationIntent.State.CREATED);
        OperationIntent succeeded = intent("replay", OperationIntent.State.SUCCEEDED);
        PreparedMutation initial = prepared(created, flow.binding(), false);
        PreparedMutation retry = prepared(succeeded, flow.binding(), true);
        NativePinnedMutation first = new NativePinnedMutation(created, flow.binding(), flow.plan(), false);
        NativePinnedMutation replay = new NativePinnedMutation(succeeded, flow.binding(), flow.plan(), true);
        when(flow.intents().prepare(any())).thenReturn(initial, retry);
        when(flow.intents().beginNative(
                eq(initial),
                any(com.massimotter.weave.backend.files.application.FilesScope.class),
                org.mockito.ArgumentMatchers.<java.util.function.Supplier<Sealed>>any()))
                .thenReturn(first);
        when(flow.intents().resumeNative(retry)).thenReturn(replay);
        when(flow.intents().hasExistingOperation("tenant-default", IDEMPOTENCY_KEY))
                .thenReturn(false, true);

        var firstResult = flow.facade().putWebDavFile(
                "/audit.txt", putRequest("audit".getBytes(StandardCharsets.UTF_8), "text/plain"),
                null, "*", null, IDEMPOTENCY_KEY);
        FilesProviderPort adapter = (FilesProviderPort) flow.nativeAdapter();
        when(adapter.find(new FilePath("/audit.txt"))).thenReturn(Optional.of(new VersionedFile(
                new FileObject(
                        new FileId(firstResult.item().id()),
                        new FilePath(firstResult.item().path()),
                        Kind.FILE,
                        firstResult.item().size(),
                        firstResult.item().mimeType(),
                        CREATED_AT,
                        false),
                new FileVersion(CONTENT_DIGEST))));
        var replayedResult = flow.facade().putWebDavFile(
                "/audit.txt", putRequest("audit".getBytes(StandardCharsets.UTF_8), "text/plain"),
                null, "*", null, IDEMPOTENCY_KEY);

        assertThat(replayedResult).isEqualTo(firstResult);
        assertThat(operationIntentAudits(audit)).singleElement().satisfies(event -> {
            assertThat(event.occurredAt()).isEqualTo(CREATED_AT);
            assertThat(event.payload())
                    .containsEntry("operation", "webdav-put")
                    .containsEntry("providerBindingRevision", "1")
                    .containsEntry("result", "recorded");
        });
        verify(flow.nativeAdapter(), times(2)).execute(
                any(), any(), eq(flow.plan()), any(),
                eq("files-operation-intent:" + created.operationRef()),
                nullable(NativeLockMove.class));
    }

    @Test
    void deterministicNativeFailureReusesAuditBeforeJpaBackedSettlement() {
        JpaAuditEventPublisher audit = auditPublisher("files_native_audit_failure");
        NativeFlow flow = nativeFlow("failure", audit);
        OperationIntent created = intent("failure", OperationIntent.State.CREATED);
        PreparedMutation initial = prepared(created, flow.binding(), false);
        NativePinnedMutation mutation = new NativePinnedMutation(
                created, flow.binding(), flow.plan(), false);
        when(flow.intents().prepare(any())).thenReturn(initial);
        when(flow.intents().beginNative(
                eq(initial),
                any(com.massimotter.weave.backend.files.application.FilesScope.class),
                org.mockito.ArgumentMatchers.<java.util.function.Supplier<Sealed>>any()))
                .thenReturn(mutation);
        when(flow.nativeAdapter().execute(
                any(), any(), eq(flow.plan()), any(), anyString(), nullable(NativeLockMove.class)))
                .thenThrow(new ApiErrorException(
                        HttpStatus.CONFLICT,
                        "files-native-deterministic-failure",
                        "The planned native Files mutation was rejected.",
                        Map.of("module", "files", "diagnosticsRedacted", true)));
        when(flow.intents().failNative(
                mutation,
                "files-native-deterministic-failure",
                "files-operation-intent:" + created.operationRef()))
                .thenReturn(intent("failure", OperationIntent.State.FAILED));

        assertThatThrownBy(() -> flow.facade().putWebDavFile(
                "/audit.txt", putRequest("audit".getBytes(StandardCharsets.UTF_8), "text/plain"),
                null, null, null, IDEMPOTENCY_KEY))
                .isInstanceOf(ApiErrorException.class);

        verify(flow.intents()).failNative(
                mutation,
                "files-native-deterministic-failure",
                "files-operation-intent:" + created.operationRef());
        assertThat(operationIntentAudits(audit)).singleElement().satisfies(event ->
                assertThat(event.payload()).containsEntry("result", "recorded"));
    }

    @Test
    void missingHeadOnPublicRetryIsAStableRedactedServiceUnavailableFailure() {
        JpaAuditEventPublisher audit = auditPublisher("files_native_missing_head_retry");
        NativeFlow flow = nativeFlow("missing-head-retry", audit);
        OperationIntent created = intent("missing-head-retry", OperationIntent.State.CREATED);
        PreparedMutation retry = prepared(created, flow.binding(), true);
        when(flow.intents().prepare(any())).thenReturn(retry);
        when(flow.intents().resumeNative(retry)).thenThrow(
                new CorruptMutationStateException("stream head missing"));

        assertThatThrownBy(() -> flow.facade().putWebDavFile(
                        "/audit.txt",
                        putRequest("audit".getBytes(StandardCharsets.UTF_8), "text/plain"),
                        null,
                        null,
                        null,
                        IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.code()).isEqualTo(
                            "files-native-mutation-state-corrupt");
                    assertThat(failure.details())
                            .containsEntry("diagnosticsRedacted", true)
                            .doesNotContainValue("stream head missing");
                });
        verify(flow.nativeAdapter(), never()).execute(
                any(), any(), any(), any(), anyString(), nullable(NativeLockMove.class));
    }

    @Test
    void framingAndDeclaredOversizeFailBeforeTheRequestBodyOrIngressStoreOpens() {
        JpaAuditEventPublisher audit = auditPublisher("files_native_streaming_framing");
        NativeFlow flow = nativeFlow("streaming-framing", audit);
        FilesStreamingContentPort streaming =
                (FilesStreamingContentPort) flow.nativeAdapter();
        AtomicBoolean opened = new AtomicBoolean();

        assertThatThrownBy(() -> flow.facade().putWebDavFile(
                        "/too-large.bin",
                        new WebDavPutRequest(
                                List.of("1025"),
                                List.of("application/octet-stream"),
                                List.of(),
                                List.of(),
                                () -> {
                                    opened.set(true);
                                    return new ByteArrayInputStream(new byte[0]);
                                }),
                        null,
                        null,
                        null,
                        IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(failure.code()).isEqualTo("files-content-too-large");
                });

        assertThatThrownBy(() -> flow.facade().putWebDavFile(
                        "/encoded.bin",
                        new WebDavPutRequest(
                                List.of(),
                                List.of("application/octet-stream"),
                                List.of("gzip"),
                                List.of("chunked"),
                                () -> {
                                    opened.set(true);
                                    return new ByteArrayInputStream(new byte[0]);
                                }),
                        null,
                        null,
                        null,
                        IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(failure.code()).isEqualTo("files-content-coding-unsupported");
                });

        assertThatThrownBy(() -> flow.facade().putWebDavFile(
                        "/ambiguous.bin",
                        new WebDavPutRequest(
                                List.of("0"),
                                List.of("application/octet-stream"),
                                List.of(),
                                List.of("chunked"),
                                () -> {
                                    opened.set(true);
                                    return new ByteArrayInputStream(new byte[0]);
                                }),
                        null,
                        null,
                        null,
                        IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(failure.code()).isEqualTo("file-upload-invalid-request");
                });

        assertThat(opened).isFalse();
        verify(streaming, never()).receive(any(), anyString(), any());
        assertThat(operationIntentAudits(audit)).isEmpty();
    }

    @Test
    void stalePutPreconditionFailsBeforeTheRequestBodyOrIngressStoreOpens() {
        JpaAuditEventPublisher audit = auditPublisher("files_native_streaming_stale_precondition");
        NativeFlow flow = nativeFlow("streaming-stale-precondition", audit);
        FilesProviderPort adapter = (FilesProviderPort) flow.nativeAdapter();
        FilesStreamingContentPort streaming = (FilesStreamingContentPort) flow.nativeAdapter();
        AtomicBoolean opened = new AtomicBoolean();
        FilePath target = new FilePath("/already.bin");
        when(adapter.find(target)).thenReturn(Optional.of(new VersionedFile(
                new FileObject(
                        new FileId("file:already"),
                        target,
                        Kind.FILE,
                        7,
                        "application/octet-stream",
                        CREATED_AT,
                        false),
                new FileVersion("sha256:already"))));

        assertThatThrownBy(() -> flow.facade().putWebDavFile(
                        target.value(),
                        new WebDavPutRequest(
                                List.of("7"),
                                List.of("application/octet-stream"),
                                List.of(),
                                List.of(),
                                () -> {
                                    opened.set(true);
                                    return new ByteArrayInputStream(new byte[7]);
                                }),
                        null,
                        "*",
                        null,
                        IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                    assertThat(failure.code()).isEqualTo("files-precondition-failed");
                });

        assertThat(opened).isFalse();
        verify(streaming, never()).receive(any(), anyString(), any());
        verify(flow.intents(), never()).prepare(any());
    }

    @Test
    void deterministicNativeLockFailureReplayReusesOneStableJpaAuditEvent() {
        JpaAuditEventPublisher audit = auditPublisher("files_native_lock_audit_failure");
        FilesProviderPort adapter = mock(
                FilesProviderPort.class,
                withSettings().extraInterfaces(NativeFilesDurableMutationPort.class));
        when(adapter.configured()).thenReturn(true);
        when(adapter.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files",
                "weave-native",
                Set.of("lock"),
                Map.of(),
                true,
                true,
                true));
        FilesMutationIntentService intents = mock(FilesMutationIntentService.class);
        NativeFilesLockRepository nativeLocks = mock(NativeFilesLockRepository.class);
        FilesLockService genericLocks = mock(FilesLockService.class);
        PreparedMutation prepared = preparedLockFailure();
        when(intents.prepare(any())).thenReturn(prepared);
        when(nativeLocks.acquire(
                eq(prepared.candidate()),
                eq(prepared.beginCommand()),
                eq("workspace-default"),
                eq(new FilePath("/audit.txt")),
                any(Duration.class),
                eq("files-operation-intent:" + prepared.candidate().operationRef())))
                .thenThrow(new LockConflictException(new FilePath("/audit.txt")));
        ContextAuthorizationProperties authorization = new ContextAuthorizationProperties(
                null, null, null, null, null, List.of(), List.of(), List.of());
        FilesFacadeService facade = new FilesFacadeService(
                provider(adapter),
                request -> ContextAuthorizationDecision.allow("test allow"),
                authorization,
                OrganizationIdentityContextResolver.configured(authorization),
                workspaceCapabilityService(),
                new DeviceCredentialService(new InMemoryDeviceCredentialRepository()),
                audit,
                genericLocks,
                intents,
                nativeLocks,
                (com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService) null,
                (com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy) null);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt(), null));

        for (int replay = 0; replay < 2; replay++) {
            assertThatThrownBy(() -> facade.lockWebDavPath(
                    "/audit.txt", null, IDEMPOTENCY_KEY))
                    .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                        assertThat(failure.status()).isEqualTo(HttpStatus.LOCKED);
                        assertThat(failure.code()).isEqualTo("files-locked");
                    });
        }

        assertThat(operationIntentAudits(audit)).singleElement().satisfies(event -> {
            assertThat(event.occurredAt()).isEqualTo(CREATED_AT);
            assertThat(event.idempotencyKey())
                    .isEqualTo("files-operation-intent:" + prepared.candidate().operationRef());
            assertThat(event.payload())
                    .containsEntry("operation", "webdav-lock")
                    .containsEntry("result", "recorded");
        });
        verify(nativeLocks, times(2)).acquire(
                eq(prepared.candidate()),
                eq(prepared.beginCommand()),
                eq("workspace-default"),
                eq(new FilePath("/audit.txt")),
                any(Duration.class),
                eq("files-operation-intent:" + prepared.candidate().operationRef()));
    }

    private NativeFlow nativeFlow(String suffix, JpaAuditEventPublisher audit) {
        FilesProviderPort adapter = mock(
                FilesProviderPort.class,
                withSettings().extraInterfaces(
                        NativeFilesDurableMutationPort.class,
                        FilesStreamingContentPort.class));
        NativeFilesDurableMutationPort nativeAdapter = (NativeFilesDurableMutationPort) adapter;
        FilesStreamingContentPort streaming = (FilesStreamingContentPort) adapter;
        FilesMutationIntentService intents = mock(FilesMutationIntentService.class);
        FilesLockService locks = mock(FilesLockService.class);
        Sealed plan = plan(suffix);
        ProviderBinding binding = new ProviderBinding(
                "tenant-default",
                "files",
                1,
                "weave-native",
                "secretref:files:native",
                ProviderBinding.State.ACTIVE,
                CREATED_AT);
        FileObject item = new FileObject(
                new FileId("file:audit:" + suffix),
                new FilePath("/audit.txt"),
                Kind.FILE,
                5,
                "text/plain",
                CREATED_AT,
                false);
        NativeResult result = new NativeResult(
                item,
                new FileVersion(CONTENT_DIGEST),
                "\"weave-audit\"",
                true);
        when(adapter.configured()).thenReturn(true);
        when(adapter.readiness()).thenReturn(ProviderReadiness.ready("files-native-ready"));
        when(adapter.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files",
                "weave-native",
                Set.of(
                        "write",
                        "files.content_streaming_read",
                        "files.content_streaming_write"),
                Map.of(),
                true,
                true,
                true));
        when(adapter.scoped(any())).thenReturn(adapter);
        when(streaming.contentProfile()).thenReturn(new ContentProfile(1024, 65_536, 1, 1));
        org.mockito.Mockito.doNothing().when(streaming).requireStreamingReady();
        when(streaming.receive(any(), anyString(), any())).thenAnswer(invocation -> {
            Long declaredLength = invocation.getArgument(0);
            String mediaType = invocation.getArgument(1);
            ReplayableFileContent.StreamFactory source = invocation.getArgument(2);
            byte[] bytes;
            try (var input = source.openStream()) {
                bytes = input.readAllBytes();
            }
            assertThat(declaredLength).isEqualTo((long) bytes.length);
            ReplayableFileContent content = new ReplayableFileContent(
                    bytes.length,
                    FilesMutationIntentService.digest(bytes),
                    mediaType,
                    () -> new ByteArrayInputStream(bytes));
            return new Ingress() {
                @Override public ReplayableFileContent content() { return content; }
                @Override public <T> T bindThroughPlanCommit(
                        String operationRef,
                        java.util.function.Supplier<T> transaction) {
                    return transaction.get();
                }
                @Override public boolean releaseIfTerminal() { return true; }
                @Override public void close() { }
            };
        });
        when(adapter.find(any())).thenAnswer(invocation -> {
            FilePath path = invocation.getArgument(0);
            if (!path.root()) {
                return Optional.empty();
            }
            return Optional.of(new VersionedFile(
                    new FileObject(
                            new FileId("collection:root"),
                            path,
                            Kind.COLLECTION,
                            0,
                            null,
                            CREATED_AT,
                            false),
                    FileVersion.unknown()));
        });
        when(nativeAdapter.plan(any(), any(), any())).thenReturn(plan);
        when(nativeAdapter.execute(
                any(), any(), eq(plan), any(), anyString(), nullable(NativeLockMove.class)))
                .thenReturn(result);

        ContextAuthorizationProperties authorizationProperties = new ContextAuthorizationProperties(
                null, null, null, null, null, List.of(), List.of(), List.of());
        FilesFacadeService facade = new FilesFacadeService(
                provider(adapter),
                request -> ContextAuthorizationDecision.allow("test allow"),
                authorizationProperties,
                workspaceCapabilityService(),
                new DeviceCredentialService(new InMemoryDeviceCredentialRepository()),
                audit,
                locks,
                intents,
                null,
                null);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt(), null));
        return new NativeFlow(facade, intents, nativeAdapter, binding, plan);
    }

    private PreparedMutation prepared(
            OperationIntent intent,
            ProviderBinding binding,
            boolean retry) {
        return new PreparedMutation(intent, binding, null, retry);
    }

    private PreparedMutation preparedLockFailure() {
        BeginCommand command = new BeginCommand(
                IDEMPOTENCY_KEY,
                "tenant-default",
                new HumanActor("user:user-123", "user-123"),
                "files",
                new ProtocolProjection("webdav", "webdav-lock", "weave.webdav.files/v1"),
                FilesMutationIntentService.digest("webdav-lock"),
                FilesMutationIntentService.digest("/audit.txt\nacquire"),
                List.of("file-path:" + FilesMutationIntentService.digest("/audit.txt")),
                "policy:test",
                "entitlement:test",
                1);
        OperationIntent candidate = new OperationIntent(
                "operation:files-native-audit:lock-failure",
                command.idempotencyKey(),
                command.organizationRef(),
                command.actor(),
                command.domain(),
                command.projection(),
                command.actionDigest(),
                command.canonicalArgumentsDigest(),
                command.objectRefs(),
                command.policyRevision(),
                command.entitlementRevision(),
                command.providerBindingRevision(),
                OperationIntent.State.CREATED,
                "outbox:files-native-audit:lock-failure",
                null,
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT);
        ProviderBinding binding = new ProviderBinding(
                "tenant-default",
                "files",
                1,
                "weave-native",
                "secretref:files:native",
                ProviderBinding.State.ACTIVE,
                CREATED_AT);
        return new PreparedMutation(candidate, binding, command, false);
    }

    private OperationIntent intent(String suffix, OperationIntent.State state) {
        boolean terminal = state.terminal();
        String operationRef = "operation:files-native-audit:" + suffix;
        return new OperationIntent(
                operationRef,
                IDEMPOTENCY_KEY,
                "tenant-default",
                new HumanActor("user:user-123", "user-123"),
                "files",
                new ProtocolProjection("webdav", "webdav-put", "weave.webdav.files/v1"),
                CONTENT_DIGEST,
                PLAN_DIGEST,
                List.of("file-path:" + CONTENT_DIGEST),
                "policy:test",
                "entitlement:test",
                1,
                state,
                "outbox:files-native-audit:" + suffix,
                null,
                null,
                terminal ? CONTENT_DIGEST : null,
                terminal ? "files-operation-intent:" + operationRef : null,
                CREATED_AT,
                terminal ? CREATED_AT.plusSeconds(1) : CREATED_AT);
    }

    private Sealed plan(String suffix) {
        Target target = new Target(
                0,
                CREATED,
                null,
                "file:audit:" + suffix,
                null,
                "/audit.txt",
                Kind.FILE,
                ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "v1/audit/" + suffix,
                5,
                "text/plain",
                CONTENT_DIGEST,
                CONTENT_DIGEST,
                "\"weave-audit\"",
                CREATED_AT,
                false,
                CREATED_AT);
        return new FilesMutationPlan.Draft(
                        "operation:files-native-audit:" + suffix,
                        "tenant-default",
                        "workspace-default",
                        PLAN_DIGEST,
                        FilesMutationPlan.OperationKind.PUT,
                        1,
                        FilesMutationPlan.EntityTagCondition.notSupplied(),
                        FilesMutationPlan.EntityTagCondition.notSupplied(),
                        false,
                        List.of(target),
                        List.of(FilesMutationPlan.Fence.absent(
                                0,
                                FilesMutationPlan.FenceRole.REQUEST_TARGET,
                                "/audit.txt")))
                .seal(PLAN_DIGEST, PLAN_DIGEST, CREATED_AT);
    }

    private List<com.massimotter.weave.backend.audit.AuditEvent> operationIntentAudits(
            JpaAuditEventPublisher audit) {
        return audit.events().stream()
                .filter(event -> event.action() == AuditAction.FILES_OPERATION_INTENT_RECORDED)
                .toList();
    }

    private JpaAuditEventPublisher auditPublisher(String semanticName) {
        DriverManagerDataSource dataSource = JpaTestDatabase.entityFirstDataSource(semanticName);
        AuditEventJpaRepository repository = JpaTestDatabase.repository(
                dataSource,
                AuditEventJpaRepository.class);
        return new JpaAuditEventPublisher(
                repository,
                JsonMapper.builder().findAndAddModules().build());
    }

    private WorkspaceCapabilityService workspaceCapabilityService() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        return new WorkspaceCapabilityService(
                properties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));
    }

    private WebDavPutRequest putRequest(byte[] content, String mediaType) {
        return new WebDavPutRequest(
                List.of(Integer.toString(content.length)),
                List.of(mediaType),
                List.of(),
                List.of(),
                () -> new ByteArrayInputStream(content));
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "tenant-default")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
                .build();
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
                return List.of(adapter).iterator();
            }
        };
    }

    private record NativeFlow(
            FilesFacadeService facade,
            FilesMutationIntentService intents,
            NativeFilesDurableMutationPort nativeAdapter,
            ProviderBinding binding,
            Sealed plan) {
    }
}
