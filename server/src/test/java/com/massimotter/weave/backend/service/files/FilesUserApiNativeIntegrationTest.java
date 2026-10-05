package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Exercises the User API service with real provider, mappings, intents and JPA transactions. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:files-user-integration;DB_CLOSE_DELAY=-1",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "weave.files.provider=weave-native",
        "weave.provider-bindings.bootstrap.files.enabled=true",
        "weave.provider-bindings.bootstrap.files.organization-ref=org:files-user-integration",
        "weave.provider-bindings.bootstrap.files.adapter-key=weave-native",
        "weave.provider-bindings.bootstrap.files.configuration-ref=profile:weave-native"
})
class FilesUserApiNativeIntegrationTest {
    @TempDir static Path directory;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry properties) {
        properties.add("weave.files.native.filesystem-root", () -> directory.resolve("blobs").toString());
    }

    @Autowired private FilesUserApiService files;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ContextAuthorizationPort authorization;
    @MockitoBean private WorkspaceCapabilityService capabilities;

    @Test
    void uploadPublishesStableIdentityAndReplaysWithExactBinaryContent() {
        when(authorization.check(any())).thenReturn(ContextAuthorizationDecision.allow("member"));
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
        assertThatThrownBy(() -> files.inspect(member("outsider"), created.fileId()))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        failure -> assertThat(failure.status().value()).isEqualTo(404));
    }

    private Jwt member(String subject) {
        return Jwt.withTokenValue("test-token").header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave").subject(subject)
                .claim("weave_tenant_id", "org:files-user-integration")
                .claim("azp", "weave-app").build();
    }
}
