package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.files.port.FilesProviderPort;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Proves candidate adapters can coexist without changing the native default composition. */
@SpringBootTest(properties = {
        "weave.files.provider=weave-native",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.weave.test/realms/weave/protocol/openid-connect/certs"
})
class CanonicalNativeFilesPrimarySelectionTest {

    @Autowired
    private Map<String, FilesProviderPort> filesProviders;

    @Autowired
    private FilesProviderPort primaryFilesProvider;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void nativeAndNextcloudProvidersCoexistWithNativeAsLegacyPrimary() {
        assertThat(filesProviders)
                .containsOnlyKeys("weaveNativeFilesAdapter", "nextcloudFilesAdapter");
        assertThat(filesProviders.get("weaveNativeFilesAdapter"))
                .isInstanceOf(WeaveNativeFilesAdapter.class);
        assertThat(filesProviders.get("nextcloudFilesAdapter"))
                .isInstanceOf(NextcloudFilesAdapter.class);
        assertThat(primaryFilesProvider).isSameAs(filesProviders.get("weaveNativeFilesAdapter"));
    }
}
