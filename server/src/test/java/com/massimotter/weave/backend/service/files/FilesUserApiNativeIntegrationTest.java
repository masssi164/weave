package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort;
import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the User API service with real provider, mappings, intents and JPA transactions. */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "weave.files.provider=weave-native",
        "weave.provider-bindings.bootstrap.files.enabled=true",
        "weave.provider-bindings.bootstrap.files.organization-ref=org:files-user-integration",
        "weave.provider-bindings.bootstrap.files.adapter-key=weave-native",
        "weave.provider-bindings.bootstrap.files.configuration-ref=profile:weave-native"
})
@Testcontainers
@Tag("postgres")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FilesUserApiNativeIntegrationTest {
    @TempDir static Path directory;
    @Container private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        properties.add("weave.files.native.filesystem-root", () -> directory.resolve("blobs").toString());
    }

    @Autowired private FilesUserApiService files;
    @Autowired private SpaceProvisioningPort spaces;
    @Autowired private SpaceMembershipAdministrationPort memberships;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ContextAuthorizationPort authorization;
    @MockitoBean private WorkspaceCapabilityService capabilities;

    @Test
    void uploadPublishesStableIdentityAndReplaysWithExactBinaryContent() {
        when(authorization.check(any())).thenReturn(ContextAuthorizationDecision.allow("member"));
        String organization = "org:files-user-integration";
        String owner = account("owner");
        String alice = account("alice");
        // The outsider may view the Space, but never Alice's owner-only File.
        spaces.provision(organization, "workspace-default", owner,
                Set.of(alice, account("outsider")));
        var view = memberships.get(organization, "workspace-default", owner, alice);
        memberships.grant(organization, "workspace-default", owner, alice,
                Set.of(Permission.VIEW, Permission.EDIT), view.strongEtag(), false);
        Jwt member = member("alice");
        byte[] bytes = {0, 10, (byte) 255, 34, 92, 127};

        assertThat(files.list(member, null).allowedActions()).contains("upload");
        var created = files.upload(member, "file:root", "binary-proof.bin", "application/octet-stream",
                bytes, "*", "native-integration-upload");
        var replayed = files.upload(member, "file:root", "binary-proof.bin", "application/octet-stream",
                bytes, "*", "native-integration-upload");

        assertThat(replayed.fileId()).isEqualTo(created.fileId());
        assertThat(replayed.revision()).isEqualTo(created.revision());
        assertThat(files.list(member, null).items()).singleElement()
                .satisfies(item -> assertThat(item.fileId()).isEqualTo(created.fileId()));
        assertThat(files.download(member, created.fileId()).bytes()).containsExactly(bytes);
        assertThat(files.list(member("outsider"), null, "workspace-default").items()).isEmpty();
        assertThatThrownBy(() -> files.inspect(member("outsider"), created.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        failure -> assertThat(failure.status().value()).isEqualTo(404));
    }

    @Test
    void sharedFilesRequireCurrentSpaceGrantsAndPreservePrivateOwnershipAndValidators() {
        when(authorization.check(any())).thenReturn(ContextAuthorizationDecision.allow("member"));
        String organization = "org:files-user-integration";
        String space = "workspace-shared";
        String owner = account("shared-owner");
        spaces.provision(organization, space, owner, Set.of(account("shared-a"), account("shared-b")));
        for (String subject : Set.of("shared-a", "shared-b")) {
            var current = memberships.get(organization, space, owner, account(subject));
            memberships.grant(organization, space, owner, account(subject),
                    Set.of(Permission.VIEW, Permission.EDIT), current.strongEtag(), false);
        }
        Jwt a = member("shared-a");
        Jwt b = member("shared-b");
        byte[] first = {1, 2, 3};
        var created = files.upload(a, "file:root", "shared-content.bin", "application/octet-stream",
                first, "*", "shared-integration-upload", space);
        assertThat(files.list(b, null, space).items()).singleElement()
                .satisfies(item -> assertThat(item.fileId()).isEqualTo(created.fileId()));
        // Shared membership does not grant the unrelated default private Space.
        assertThatThrownBy(() -> files.list(b, null))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        failure -> assertThat(failure.status().value()).isEqualTo(403));
        var downloaded = files.download(b, created.fileId());
        assertThat(downloaded.bytes()).containsExactly(first);
        byte[] changed = {9, 8, 7};
        files.update(b, created.fileId(), "application/octet-stream", changed,
                downloaded.etag(), "shared-integration-update");
        assertThat(files.download(a, created.fileId()).bytes()).containsExactly(changed);
        assertThatThrownBy(() -> files.update(a, created.fileId(), "application/octet-stream", first,
                downloaded.etag(), "shared-stale-version-update"))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        failure -> assertThat(failure.status().value()).isEqualTo(412));
        assertThatThrownBy(() -> files.download(member("shared-c"), created.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        failure -> assertThat(failure.status().value()).isEqualTo(403));
        var bGrant = memberships.get(organization, space, owner, account("shared-b"));
        memberships.revoke(organization, space, owner, account("shared-b"), bGrant.strongEtag());
        assertThatThrownBy(() -> files.download(b, created.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        failure -> assertThat(failure.status().value()).isEqualTo(403));
    }

    private Jwt member(String subject) {
        return Jwt.withTokenValue("test-token").header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave").subject(subject)
                .claim("weave_tenant_id", "org:files-user-integration")
                .claim("azp", "weave-app").build();
    }

    private String account(String subject) {
        return IdentityReferences.accountId("https://auth.weave.test/realms/weave", subject);
    }
}
