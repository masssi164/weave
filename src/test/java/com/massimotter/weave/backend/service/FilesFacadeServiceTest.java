package com.massimotter.weave.backend.service;

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
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesFacadeServiceTest {

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
                    assertThat(exception.code()).isEqualTo("nextcloud-adapter-not-configured");
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

    private FilesFacadeService service(FilesStorageAdapter adapter) {
        return service(adapter, request -> ContextAuthorizationDecision.allow("test allow"));
    }

    private FilesFacadeService service(FilesStorageAdapter adapter, ContextAuthorizationPort contextAuthorizationPort) {
        return new FilesFacadeService(provider(adapter), contextAuthorizationPort);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .build();
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
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public FileUploadResponse upload(String parentPath, MultipartFile file) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public DownloadedFile download(String id) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public void delete(String id) {
            throw new UnsupportedOperationException("not needed");
        }
    }
}
