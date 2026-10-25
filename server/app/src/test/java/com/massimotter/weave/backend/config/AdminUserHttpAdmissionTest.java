package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;
import com.massimotter.weave.backend.security.http.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.security.http.ApiAccessDeniedHandler;

import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.identity.profile.port.ProductProfileOverrideRepository;

import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.massimotter.weave.backend.controller.AdminControlPlaneController;
import com.massimotter.weave.backend.controller.IdentityController;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.model.admin.AdminControlPlaneResponse;
import com.massimotter.weave.backend.model.admin.AdminCurrentActorResponse;
import com.massimotter.weave.backend.service.AdminControlPlaneService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.identity.profile.port.ProductProfileOverrideRepository;
import com.massimotter.weave.backend.identity.profile.application.ProductProfileService;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Signed HTTP bearers traverse the production filter chains and decoder validators. */
@WebMvcTest(controllers = {AdminControlPlaneController.class, IdentityController.class},
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, AdminApiSecurityConfiguration.class,
    ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorResponseWriter.class,
    ApiExceptionHandler.class, AdminUserHttpAdmissionTest.Decoders.class})
@TestPropertySource(properties = {
    "weave.context.authorization.default-tenant-id=acme",
    "weave.security.primary-organization.keycloak-id=test-primary-organization",
    "weave.security.primary-organization.keycloak-alias=weave-dogfood"
})
class AdminUserHttpAdmissionTest {
  private static final String ISSUER = "https://auth.weave.test/realms/weave";
  private static final String AUDIENCE = "https://api.weave.test/api";
  private static final String SUBJECT = "admitted-administrator";
  private static final SigningAuthority AUTHORITY = SigningAuthority.start();

  @Autowired MockMvc mvc;
  @MockitoBean AdminControlPlaneService admin;
  @MockitoBean ProductProfileService profiles;

  @BeforeEach
  void projections() {
    var identities = OrganizationIdentityContextResolver.configured(
        new ContextAuthorizationProperties("weave_tenant_id", "tenant_id", "acme",
            "sub", "user:", List.of(), List.of(), List.of()));
    when(admin.overview(any())).thenAnswer(invocation -> {
      var actor = identities.resolve(invocation.getArgument(0));
      return new AdminControlPlaneResponse("admin-control-plane-v1",
          new AdminCurrentActorResponse(actor.accountId(), actor.organizationId(), actor.issuer(), actor.roles()),
          actor.organizationId(), "Organization", null, null,
          false, true, true, true, false, Instant.EPOCH, List.of(), List.of(),
          null, null, List.of(), null, List.of(), List.of(), Map.of());
    });
    var productProfiles = new ProductProfileService(mock(ProductProfileOverrideRepository.class), OrganizationIdentityContextResolver.defaults());
    when(profiles.authenticatedUser(any())).thenAnswer(invocation ->
        productProfiles.authenticatedUser(invocation.getArgument(0)));
  }

  @AfterAll
  static void closeSigningAuthority() {
    AUTHORITY.server.stop(0);
  }

  @Test
  void adminSignedBearerReadsItsCurrentActorButCannotEnterUserMe() throws Exception {
    for (String role : List.of("owner", "admin")) {
      String bearer = token("weave-admin-console", role);
      mvc.perform(get("/api/admin/control-plane").header("Authorization", "Bearer " + bearer))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.currentActor.accountRef").value(IdentityReferences.accountId(ISSUER, SUBJECT)))
          .andExpect(jsonPath("$.currentActor.organizationId").value("acme"))
          .andExpect(jsonPath("$.currentActor.identityIssuer").value(ISSUER))
          .andExpect(jsonPath("$.currentActor.roles[0]").value(role))
          .andExpect(jsonPath("$.currentActor.subject").doesNotExist());
      mvc.perform(get("/api/me").header("Authorization", "Bearer " + bearer))
          .andExpect(status().isUnauthorized());
    }
    verifyNoInteractions(profiles);
  }

  @Test
  void nativeSignedBearerReadsUserMeButCannotEnterAdminControlPlane() throws Exception {
    String bearer = token("weave-app", "owner");
    mvc.perform(get("/api/me").header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(IdentityReferences.accountId(ISSUER, SUBJECT)));
    mvc.perform(get("/api/admin/control-plane").header("Authorization", "Bearer " + bearer))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(admin);
  }

  @Test
  void invalidSignedTokensNeverReachEitherProductProjection() throws Exception {
    for (String client : List.of("weave-app", "weave-admin-console")) {
      String path = client.equals("weave-app") ? "/api/me" : "/api/admin/control-plane";
      for (String invalid : List.of("expired", "wrong-audience", "wrong-issuer", "service-account")) {
        Instant now = Instant.now();
        String bearer = signed(invalid.equals("service-account") ? "weave-identity-admin" : client, invalid.equals("wrong-issuer") ? "https://other.invalid/realm" : ISSUER,
            invalid.equals("wrong-audience") ? "https://other.invalid/api" : AUDIENCE,
            invalid.equals("service-account") ? "service-account-client" : SUBJECT,
            HumanJwtTestSupport.organizationWithRole("owner"),
            invalid.equals("expired") ? now.minusSeconds(900) : now,
            invalid.equals("expired") ? now.minusSeconds(600) : now.plusSeconds(300));
        mvc.perform(get(path).header("Authorization", "Bearer " + bearer))
            .andExpect(status().isUnauthorized());
      }
    }
    verifyNoInteractions(admin, profiles);
  }

  @Test
  void foreignOrganizationAndNonAdministratorAreDeniedBeforeAdminProjection() throws Exception {
    for (String role : List.of("member", "guest")) {
      mvc.perform(get("/api/admin/control-plane")
              .header("Authorization", "Bearer " + token("weave-admin-console", role)))
          .andExpect(status().isForbidden());
    }
    var foreign = Map.of(HumanJwtTestSupport.ORGANIZATION_ALIAS,
        Map.of("id", "foreign-organization", "groups", List.of("/owners"),
            "resource_access", Map.of("weave-app", Map.of("roles", List.of("owner")))));
    mvc.perform(get("/api/admin/control-plane").header("Authorization", "Bearer " + signed(
            "weave-admin-console", ISSUER, AUDIENCE, SUBJECT, foreign,
            Instant.now(), Instant.now().plusSeconds(300))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(admin, profiles);
  }

  private static String token(String client, String role) throws Exception {
    return signed(client, ISSUER, AUDIENCE, SUBJECT,
        HumanJwtTestSupport.organizationWithRole(role), Instant.now(), Instant.now().plusSeconds(300));
  }

  private static String signed(String client, String issuer, String audience,
      String subject, Map<String, ?> organization, Instant issued, Instant expires) throws Exception {
    var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audience)
        .claim("azp", client).claim("scope", "weave:workspace")
        .claim("weave_tenant_id", "acme").claim("organization", organization)
        .issueTime(Date.from(issued)).expirationTime(Date.from(expires)).build();
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(AUTHORITY.key.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(AUTHORITY.key));
    return jwt.serialize();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Decoders {
    @Bean @Primary JwtDecoder jwtDecoder() {
      return new JwtDecoderConfig().jwtDecoder(properties(), new WeaveSecurityProperties(AUDIENCE, "weave-app"));
    }
    @Bean("adminApiJwtDecoder") JwtDecoder adminApiJwtDecoder() {
      return new JwtDecoderConfig().adminApiJwtDecoder(properties(), new WeaveSecurityProperties(AUDIENCE, "weave-app"));
    }
    private OAuth2ResourceServerProperties properties() {
      var properties = new OAuth2ResourceServerProperties();
      properties.getJwt().setIssuerUri(ISSUER);
      properties.getJwt().setJwkSetUri("http://127.0.0.1:" + AUTHORITY.server.getAddress().getPort() + "/jwks");
      return properties;
    }
  }

  private record SigningAuthority(RSAKey key, HttpServer server) {
    static SigningAuthority start() {
      try {
        RSAKey key = new RSAKeyGenerator(2048).keyID("owned-http-admission-key").generate();
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, jwks.length);
          try (var output = exchange.getResponseBody()) { output.write(jwks); }
        });
        server.start();
        return new SigningAuthority(key, server);
      } catch (Exception failure) {
        throw new IllegalStateException("Owned signing fixture could not start", failure);
      }
    }
  }
}
