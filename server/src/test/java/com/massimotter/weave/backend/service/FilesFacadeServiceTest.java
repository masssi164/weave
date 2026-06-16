package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditRequiredException;
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
import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import com.massimotter.weave.backend.service.files.DownloadedFile;
import com.massimotter.weave.backend.service.files.FilesStorageAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        FilesFacadeService unconfigured = service(new StubAdapter(false), new InMemoryAuditEventPublisher());

        assertThatThrownBy(() -> missing.list("/"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("files-adapter-not-configured");
                    assertThat(exception.details()).containsEntry("operation", "list-files");
                });
        assertThatThrownBy(() -> unconfigured.upload("/", null))
                .isInstanceOfSatisfying(ApiErrorException.class, exception ->
                        assertThat(exception.details()).containsEntry("operation", "upload-file"));
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
    void mutatingOperationsPublishSupportSafeAuditWithCanonicalMappingRefs() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        FilesFacadeService service = service(new StubAdapter(true), audit);

        FileItemResponse folder = service.createFolder(new CreateFolderRequest("/Team", "Design"));
        FileUploadResponse upload = service.upload("/Team", null);
        service.delete("files:test-delete");

        assertThat(folder.id()).startsWith("files:");
        assertThat(upload.item().id()).startsWith("files:");
        assertThat(audit.events()).extracting(event -> event.action()).containsExactly(
                AuditAction.FILE_FOLDER_CREATED,
                AuditAction.FILE_UPLOADED,
                AuditAction.FILE_DELETED);
        assertThat(audit.events()).allSatisfy(event -> {
            assertThat(event.tenantId()).isEqualTo("tenant-default");
            assertThat(event.contextId()).isEqualTo("workspace-default");
            assertThat(event.actorRef()).isEqualTo("user:user-123");
            assertThat(event.sourceRef()).isEqualTo("files-facade");
            assertThat(event.payload()).containsEntry("module", "files");
            assertThat(event.payload().get("mappingRef").toString()).startsWith("provider-mapping://files/");
            String rendered = event.toString();
            assertThat(rendered)
                    .doesNotContain("nextcloud")
                    .doesNotContain("/remote.php/dav")
                    .doesNotContain("https://files")
                    .doesNotContain("app-password");
        });
    }

    @Test
    void mutatingOperationsFailClosedWhenAuditPublisherIsMissingBeforeReturningSuccess() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        assertThatThrownBy(() -> service(new StubAdapter(true)).delete("files:test-delete"))
                .isInstanceOf(AuditRequiredException.class);
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
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member")))
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
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member")))
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

    private FilesFacadeService service(FilesStorageAdapter adapter) {
        return service(adapter, request -> ContextAuthorizationDecision.allow("test allow"));
    }

    private FilesFacadeService service(FilesStorageAdapter adapter, ContextAuthorizationPort contextAuthorizationPort) {
        return service(adapter, contextAuthorizationPort, defaultContextAuthorizationProperties());
    }

    private FilesFacadeService service(
            FilesStorageAdapter adapter,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties) {
        return service(adapter, contextAuthorizationPort, contextAuthorizationProperties, null);
    }

    private FilesFacadeService service(FilesStorageAdapter adapter, InMemoryAuditEventPublisher auditPublisher) {
        return service(adapter, request -> ContextAuthorizationDecision.allow("test allow"), defaultContextAuthorizationProperties(), auditPublisher);
    }

    private FilesFacadeService service(
            FilesStorageAdapter adapter,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            InMemoryAuditEventPublisher auditPublisher) {
        return new FilesFacadeService(
                provider(adapter),
                contextAuthorizationPort,
                contextAuthorizationProperties,
                workspaceCapabilityService(),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-06-16T12:00:00Z"), ZoneOffset.UTC));
    }

    private ContextAuthorizationProperties defaultContextAuthorizationProperties() {
        return new ContextAuthorizationProperties(null, null, null, null, null, List.of(), List.of(), List.of());
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
                .claim("realm_access", java.util.Map.of("roles", roles))
                .claim("groups", groups)
                .build();
    }

    @Configuration
    @EnableConfigurationProperties(ContextAuthorizationProperties.class)
    @Import(ContextAuthorizationConfiguration.class)
    static class ContextAuthorizationTestConfiguration {
    }

    private ObjectProvider<FilesStorageAdapter> provider(FilesStorageAdapter adapter) {
        return new ObjectProvider<>() {
            @Override
            public FilesStorageAdapter getObject(Object... args) {
                return adapter;
            }

            @Override
            public FilesStorageAdapter getIfAvailable() {
                return adapter;
            }

            @Override
            public FilesStorageAdapter getIfUnique() {
                return adapter;
            }

            @Override
            public FilesStorageAdapter getObject() {
                return adapter;
            }

            @Override
            public Iterator<FilesStorageAdapter> iterator() {
                return adapter == null ? List.<FilesStorageAdapter>of().iterator() : List.of(adapter).iterator();
            }
        };
    }

    private static final class StubAdapter implements FilesStorageAdapter {

        private final boolean configured;

        private StubAdapter(boolean configured) {
            this.configured = configured;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public FileListResponse list(String path) {
            return new FileListResponse(path, List.of(new FileItemResponse(
                    "files:test",
                    "readme.md",
                    path + "/readme.md",
                    "file",
                    "text/markdown",
                    12L,
                    OffsetDateTime.parse("2026-04-26T08:00:00Z"),
                    true)), null);
        }

        @Override
        public FileItemResponse createFolder(CreateFolderRequest request) {
            return new FileItemResponse(
                    "files:folder-design",
                    request.name(),
                    request.parentPath() + "/" + request.name(),
                    "folder",
                    null,
                    null,
                    OffsetDateTime.parse("2026-04-26T08:00:00Z"),
                    false);
        }

        @Override
        public FileUploadResponse upload(String parentPath, MultipartFile file) {
            return new FileUploadResponse(new FileItemResponse(
                    "files:uploaded-readme",
                    "readme.md",
                    parentPath + "/readme.md",
                    "file",
                    "text/markdown",
                    12L,
                    OffsetDateTime.parse("2026-04-26T08:00:00Z"),
                    true));
        }

        @Override
        public DownloadedFile download(String id) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public void delete(String id) {
        }
    }
}
