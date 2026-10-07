package com.massimotter.weave.backend.service.spaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class SpaceUserApiServiceTest {
    private final SpaceAccessPort access = mock(SpaceAccessPort.class);
    private final ContextAuthorizationProperties context =
            new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
    private final SpaceUserApiService service = new SpaceUserApiService(
            OrganizationIdentityContextResolver.configured(context), access);
    private static final String MEMBER_ACCOUNT = IdentityReferences.accountId(
            "https://auth.weave.test/realms/weave", "member");
    private final Jwt member = Jwt.withTokenValue("member")
            .header("alg", "none").subject("member")
            .issuer("https://auth.weave.test/realms/weave")
            .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
            .build();

    @Test
    void listsOnlyDurableVisibleSpacesInBoundedKeysetOrder() {
        when(access.visibleSpaceRefs("tenant-default", MEMBER_ACCOUNT, null, 2))
                .thenReturn(List.of("space:a", "space:b"));
        var first = service.list(member, null, 2);
        assertThat(first.spaces()).extracting(space -> space.spaceRef())
                .containsExactly("space:a", "space:b");
        assertThat(first.nextAfterSpaceRef()).isEqualTo("space:b");
        when(access.visibleSpaceRefs("tenant-default", MEMBER_ACCOUNT, "space:b", 2))
                .thenReturn(List.of("space:c"));
        var second = service.list(member, first.nextAfterSpaceRef(), 2);
        assertThat(second.spaces()).extracting(space -> space.spaceRef()).containsExactly("space:c");
        assertThat(second.nextAfterSpaceRef()).isNull();
    }

    @Test
    void absentRevokedAndForeignSpacesHaveTheSameNotFoundResult() {
        for (String ref : List.of("space:absent", "space:revoked", "space:foreign")) {
            assertThatThrownBy(() -> service.inspect(member, ref))
                    .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                        assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(error.code()).isEqualTo("space-not-found");
                    });
        }
        verify(access, times(3)).allows(eq("tenant-default"), anyString(),
                eq(MEMBER_ACCOUNT), eq(Permission.VIEW));
    }

    @Test
    void workloadAndInvalidPageAreRejectedBeforeRepositoryAccess() {
        Jwt workload = Jwt.withTokenValue("workload")
                .header("alg", "none").subject("cell")
                .issuer("https://auth.weave.test/realms/weave")
                .claim("azp", "weave-mcp-server")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
                .build();
        assertThatThrownBy(() -> service.list(workload, null, 25))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.list(member, null, 101))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.list(member, "bad\nref", 25))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        Jwt withoutRole = Jwt.withTokenValue("no-role")
                .header("alg", "none").subject("member")
                .issuer("https://auth.weave.test/realms/weave")
                .claim("organization", Map.of(HumanJwtTestSupport.ORGANIZATION_ALIAS,
                        Map.of("id", HumanJwtTestSupport.ORGANIZATION_ID)))
                .build();
        assertThatThrownBy(() -> service.list(withoutRole, null, 25))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(access);
    }
}
