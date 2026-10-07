package com.massimotter.weave.backend.service.spaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.files.FilesUserApiService;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class SpaceRelationshipUserServiceTest {
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String ACCOUNT = IdentityReferences.accountId(ISSUER, "member");
    private final SpaceAccessPort spaces = mock(SpaceAccessPort.class);
    private final FilesUserResourceRepository records = mock(FilesUserResourceRepository.class);
    private final FilesUserApiService files = mock(FilesUserApiService.class);
    private final ContextAuthorizationProperties context =
            new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
    private final SpaceRelationshipUserService service = new SpaceRelationshipUserService(
            OrganizationIdentityContextResolver.configured(context), context, spaces, records, files);
    private final Jwt member = Jwt.withTokenValue("member").header("alg", "none")
            .issuer(ISSUER).subject("member")
            .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
            .build();

    @Test
    void showsOnlyConfirmedMaterializedOwnerFileWithStableRelationIdentity() {
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 25))
                .thenReturn(List.of(resource("file:stable")));
        var result = service.list(member, "workspace-default", null, 25);
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.relationships().getFirst().relationRef()).isEqualTo("relation:file:file:stable");
        assertThat(result.relationships().getFirst().targetRef()).isEqualTo("file:stable");
        assertThat(result.relationships().getFirst().targetKind()).isEqualTo("FILE");
        verify(files).inspect(member, "file:stable");
    }

    @Test
    void revokedSpaceAndProviderRightsUncertaintyFailClosed() {
        assertThatThrownBy(() -> service.list(member, "workspace-default", null, 25))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(records, files);

        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 25))
                .thenReturn(List.of(resource("file:stale")));
        when(files.inspect(member, "file:stale")).thenThrow(new ApiErrorException(
                HttpStatus.NOT_FOUND, "file-not-found", "unavailable", Map.of()));
        assertThatThrownBy(() -> service.list(member, "workspace-default", null, 25))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.code()).isEqualTo("space-resource-check-unavailable");
                });
    }

    @Test
    void deniedFileCapabilityPublishesNoResourceOrCursor() {
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 1))
                .thenReturn(List.of(resource("file:private")));
        when(files.inspect(member, "file:private")).thenThrow(new ApiErrorException(
                HttpStatus.FORBIDDEN, "files-forbidden", "denied", Map.of()));
        var result = service.list(member, "workspace-default", null, 1);
        assertThat(result.relationships()).isEmpty();
        assertThat(result.nextAfterRelationRef()).isNull();
    }

    private static FilesUserResource resource(String fileId) {
        return new FilesUserResource("tenant-default", fileId, 1, "workspace-default",
                "file:root", "/stable.txt", Kind.FILE, "user:member",
                FilesUserResource.State.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }
}
