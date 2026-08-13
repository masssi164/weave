package com.massimotter.weave.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
      "weave.identity.invitations.bootstrap-owner.enabled=false"
    })
@AutoConfigureMockMvc
class BootstrapOwnerInvitationDisabledIntegrationTest {
  @Autowired MockMvc mockMvc;

  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void bootstrapEndpointDoesNotExistWhenTheOneShotAuthorityIsDisabled() throws Exception {
    mockMvc
        .perform(
            post(BootstrapOwnerInvitationController.PATH)
                .header(BootstrapOwnerInvitationController.CREDENTIAL_HEADER, "not-a-secret")
                .header("Idempotency-Key", "disabled-bootstrap-check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"owner@example.org","displayName":"Weave Owner"}
                    """))
        .andExpect(status().isNotFound());
  }
}
