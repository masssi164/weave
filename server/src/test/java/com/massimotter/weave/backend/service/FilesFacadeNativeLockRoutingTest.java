package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService.PreparedMutation;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.LockResult;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.UnlockResult;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort;
import com.massimotter.weave.backend.operation.application.OperationIntentService.BeginCommand;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import com.massimotter.weave.backend.security.device.InMemoryDeviceCredentialRepository;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class FilesFacadeNativeLockRoutingTest {

    private static final Instant NOW = Instant.parse("2026-08-20T13:00:00Z");
    private static final String TOKEN = "opaquelocktoken:cur_native_lock_token";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void weaveNativeLockRefreshAndUnlockUseOnlyCompositeRepository() {
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
        PreparedMutation acquire = prepared("acquire", "webdav-lock");
        PreparedMutation refresh = prepared("refresh", "webdav-lock");
        PreparedMutation unlock = prepared("unlock", "webdav-unlock");
        when(intents.prepare(any())).thenReturn(acquire, refresh, unlock);
        when(nativeLocks.acquire(
                eq(acquire.candidate()),
                eq(acquire.beginCommand()),
                eq("workspace-default"),
                eq(new FilePath("/Team/locked.txt")),
                any(Duration.class),
                anyString()))
                .thenReturn(new LockResult(acquire.candidate(), TOKEN, NOW.plusSeconds(3600), false));
        when(nativeLocks.refresh(
                eq(refresh.candidate()),
                eq(refresh.beginCommand()),
                eq("workspace-default"),
                eq(new FilePath("/Team/locked.txt")),
                eq(TOKEN),
                any(Duration.class),
                anyString()))
                .thenReturn(new LockResult(refresh.candidate(), TOKEN, NOW.plusSeconds(3700), false));
        when(nativeLocks.unlock(
                eq(unlock.candidate()),
                eq(unlock.beginCommand()),
                eq("workspace-default"),
                eq(new FilePath("/Team/locked.txt")),
                eq(TOKEN),
                anyString()))
                .thenReturn(new UnlockResult(unlock.candidate(), false));
        FilesFacadeService facade = facade(adapter, genericLocks, intents, nativeLocks);

        var acquired = facade.lockWebDavPath(
                "/Team/locked.txt", null, "native-lock-acquire-0001");
        var refreshed = facade.lockWebDavPath(
                "/Team/locked.txt", "(<" + TOKEN + ">)", "native-lock-refresh-0001");
        facade.unlockWebDavPath(
                "/Team/locked.txt", "<" + TOKEN + ">", "native-lock-unlock-0001");

        assertThat(acquired.token()).isEqualTo(TOKEN);
        assertThat(refreshed.token()).isEqualTo(TOKEN);
        verify(nativeLocks).acquire(
                eq(acquire.candidate()),
                eq(acquire.beginCommand()),
                eq("workspace-default"),
                eq(new FilePath("/Team/locked.txt")),
                any(Duration.class),
                anyString());
        verify(nativeLocks).refresh(
                eq(refresh.candidate()),
                eq(refresh.beginCommand()),
                eq("workspace-default"),
                eq(new FilePath("/Team/locked.txt")),
                eq(TOKEN),
                any(Duration.class),
                anyString());
        verify(nativeLocks).unlock(
                eq(unlock.candidate()),
                eq(unlock.beginCommand()),
                eq("workspace-default"),
                eq(new FilePath("/Team/locked.txt")),
                eq(TOKEN),
                anyString());
        verifyNoInteractions(genericLocks);
        verify(adapter, never()).scoped(any());
    }

    private FilesFacadeService facade(
            FilesProviderPort adapter,
            FilesLockService genericLocks,
            FilesMutationIntentService intents,
            NativeFilesLockRepository nativeLocks) {
        ContextAuthorizationProperties authorization = new ContextAuthorizationProperties(
                null, null, null, null, null, List.of(), List.of(), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt(), null));
        return new FilesFacadeService(
                provider(adapter),
                request -> ContextAuthorizationDecision.allow("test allow"),
                authorization,
                OrganizationIdentityContextResolver.configured(authorization),
                workspaceCapabilityService(),
                new DeviceCredentialService(new InMemoryDeviceCredentialRepository()),
                new InMemoryAuditEventPublisher(),
                genericLocks,
                intents,
                nativeLocks,
                (McpWorkloadAuthorizationService) null,
                (McpExchangedTokenPolicy) null);
    }

    private PreparedMutation prepared(String suffix, String operation) {
        String idempotencyKey = "native-lock-" + suffix + "-0001";
        BeginCommand command = new BeginCommand(
                idempotencyKey,
                "tenant-default",
                new HumanActor("user:user-123", "user-123"),
                "files",
                new ProtocolProjection("webdav", operation, "weave.webdav.files/v1"),
                FilesMutationIntentService.digest(operation),
                FilesMutationIntentService.digest("arguments:" + suffix),
                List.of("file-path:" + FilesMutationIntentService.digest("/Team/locked.txt")),
                "policy:test",
                "entitlement:test",
                1);
        OperationIntent candidate = new OperationIntent(
                "operation:native-lock:" + suffix,
                idempotencyKey,
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
                "outbox:native-lock:" + suffix,
                null,
                null,
                null,
                null,
                NOW,
                NOW);
        ProviderBinding binding = new ProviderBinding(
                "tenant-default",
                "files",
                1,
                "weave-native",
                "secretref:files:native",
                ProviderBinding.State.ACTIVE,
                NOW);
        return new PreparedMutation(candidate, binding, command, false);
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
            @Override public FilesProviderPort getObject(Object... args) { return adapter; }
            @Override public FilesProviderPort getIfAvailable() { return adapter; }
            @Override public FilesProviderPort getIfUnique() { return adapter; }
            @Override public FilesProviderPort getObject() { return adapter; }
        };
    }
}
