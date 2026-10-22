package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.spaces.DefaultSpaceBootstrapResponse;
import com.massimotter.weave.backend.service.spaces.DefaultSpaceBootstrapService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.spaces.bootstrap.enabled=true",
        "weave.spaces.bootstrap.environment=dogfood"
})
@AutoConfigureMockMvc
class DefaultSpaceBootstrapEnabledIntegrationTest {
    private static final String TOKEN = "a-constant-test-credential-with-at-least-thirty-two-bytes";
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean DefaultSpaceBootstrapService spaces;

    @DynamicPropertySource
    static void secretRef(DynamicPropertyRegistry registry) throws IOException {
        Path directory = Files.createTempDirectory("weave-space-bootstrap-test-");
        Path token = directory.resolve("token");
        Files.writeString(token, TOKEN);
        Files.setPosixFilePermissions(token, PosixFilePermissions.fromString("rw-------"));
        token.toFile().deleteOnExit();
        directory.toFile().deleteOnExit();
        registry.add("weave.spaces.bootstrap.token-file", token::toString);
    }

    @Test void dedicatedHeaderProtectsTheExceptionalRouteAndIsConsumedAfterSuccess() throws Exception {
        mvc.perform(post(DefaultSpaceBootstrapController.PATH)).andExpect(status().isBadRequest());
        mvc.perform(post(DefaultSpaceBootstrapController.PATH)
                .header(DefaultSpaceBootstrapController.CREDENTIAL_HEADER, "incorrect"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(spaces);
        when(spaces.prepare()).thenReturn(DefaultSpaceBootstrapResponse.passed("CREATED"));
        mvc.perform(post(DefaultSpaceBootstrapController.PATH)
                .header(DefaultSpaceBootstrapController.CREDENTIAL_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("weave.default-space-bootstrap/v1"))
                .andExpect(jsonPath("$.spaceRef").value("workspace-default"))
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andExpect(jsonPath("$.ownerAccessVerified").value(true))
                .andExpect(jsonPath("$.containsSecretValues").value(false));
        mvc.perform(post(DefaultSpaceBootstrapController.PATH)
                .header(DefaultSpaceBootstrapController.CREDENTIAL_HEADER, TOKEN))
                .andExpect(status().isUnauthorized());
        verify(spaces, times(1)).prepare();
    }
}
