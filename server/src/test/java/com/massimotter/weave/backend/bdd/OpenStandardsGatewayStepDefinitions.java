package com.massimotter.weave.backend.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.service.FilesFacadeService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public class OpenStandardsGatewayStepDefinitions {

    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of(
            "Nextcloud",
            "remote.php",
            "files.weave.test",
            "matrix.weave.test",
            "Authorization",
            "Bearer ",
            "access_token",
            "refresh_token",
            "app_password",
            "SecretRef",
            "credentialref://",
            "rawProviderPayload",
            "adminDiagnostic");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilesFacadeService filesFacadeService;

    private RequestPostProcessor caller;
    private JsonNode lastJson;
    private String filesCredentialId;
    private final List<String> responseBodies = new ArrayList<>();

    @Before
    public void resetOpenStandardsScenario() {
        SecurityContextHolder.clearContext();
        caller = null;
        lastJson = null;
        filesCredentialId = null;
        responseBodies.clear();
    }

    @Given("an authenticated member has a valid Weave OIDC session")
    public void anAuthenticatedMemberHasAValidWeaveOidcSession() {
        caller = workspaceJwt();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwtPrincipal(), null));
    }

    @Given("the OpenAPI contract is generated")
    public void theOpenapiContractIsGenerated() {
        caller = workspaceJwt();
    }

    @Given("a member creates a scoped Files WebDAV device credential")
    public void aMemberCreatesAScopedFilesWebdavDeviceCredential() throws Exception {
        caller = workspaceJwt();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwtPrincipal(), null));
        perform(post("/api/files/client-setup/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "label", "BDD WebDAV client",
                        "clientType", "webdav"))));
        assertThat(lastJson.path("state").asText()).isEqualTo("active");
        assertThat(lastJson.path("secretMaterialReturned").asBoolean()).isTrue();
        assertThat(lastJson.path("secret").asText()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(lastJson.path("webDavBasePath").asText()).isEqualTo("/dav/files");
        filesCredentialId = lastJson.path("credentialId").asText();
        assertThat(filesCredentialId).startsWith("files_device_");
    }

    @When("the member loads the organization manifest")
    public void theMemberLoadsTheOrganizationManifest() throws Exception {
        perform(get("/api/organization/manifest"));
    }

    @When("the contract is inspected for Files, Calendar, and Chat")
    public void theContractIsInspectedForFilesCalendarAndChat() throws Exception {
        perform(get("/v3/api-docs"));
    }

    @When("the credential is revoked")
    public void theCredentialIsRevoked() throws Exception {
        perform(delete("/api/files/client-setup/credentials/{credentialId}", filesCredentialId));
        assertThat(lastJson.path("state").asText()).isEqualTo("revoked");
        assertThat(lastJson.path("secretMaterialReturned").asBoolean()).isFalse();
    }

    @Then("Files advertises the Weave WebDAV facade at {string}")
    public void filesAdvertisesTheWeaveWebdavFacadeAt(String path) {
        // OPEN_STANDARDS_MANIFEST_CONTRACT
        assertThat(lastJson.at("/clientAccessDiscovery/files/surfaces").toString()).contains(path);
        assertThat(lastJson.at("/clientAccessDiscovery/files/credentialLifecycle/status").asText())
                .isEqualTo("revocable_device_grants_available");
        assertThat(lastJson.at("/clientAccessDiscovery/files/credentialLifecycle/lifecyclePaths").toString())
                .contains("/api/files/client-setup/credentials");
    }

    @Then("Calendar advertises the Weave CalDAV facade at {string}")
    public void calendarAdvertisesTheWeaveCaldavFacadeAt(String path) {
        assertThat(lastJson.at("/clientAccessDiscovery/calendar/surfaces").toString()).contains(path);
    }

    @Then("Chat advertises a Matrix Client-Server endpoint")
    public void chatAdvertisesAMatrixClientServerEndpoint() {
        assertThat(lastJson.at("/clientAccessDiscovery/chat/surfaces").toString())
                .contains("Weave Matrix Client-Server projection")
                .contains("encrypted_data_plane_available")
                .contains("/_matrix/client");
    }

    @Then("Calls advertises MatrixRTC Profile 0 without a member Calls API")
    public void callsAdvertisesMatrixRtcProfileZeroWithoutAMemberCallsApi() {
        assertThat(lastJson.at("/clientAccessDiscovery/meetings-calls/productApiBasePath").asText())
                .isEqualTo("/_matrix/client");
        assertThat(lastJson.at("/clientAccessDiscovery/meetings-calls/surfaces").toString())
                .contains("MatrixRTC Profile 0")
                .doesNotContain("/api/calls");
    }

    @Then("no provider URL, provider credential, raw provider payload, SecretRef value, or admin diagnostic is exposed")
    public void noProviderUrlProviderCredentialRawProviderPayloadSecretRefValueOrAdminDiagnosticIsExposed() {
        assertSupportSafeResponses();
    }

    @Then("it contains only setup, readiness, revoke, manifest, admin, and generated convenience surfaces")
    public void itContainsOnlySetupReadinessRevokeManifestAdminAndGeneratedConvenienceSurfaces() {
        // OPENAPI_CONTROL_PLANE_CONTRACT
        JsonNode paths = lastJson.path("paths");
        assertThat(paths.has("/api/files/readiness")).isTrue();
        assertThat(paths.has("/api/files/native-provider-setup")).isTrue();
        assertThat(paths.has("/api/files/client-setup/credentials")).isTrue();
        assertThat(paths.has("/api/files/client-setup/credentials/{credentialId}")).isTrue();
        assertThat(paths.has("/api/calendar/native-sync-setup")).isTrue();
        assertThat(paths.has("/api/calendar/client-setup/credentials")).isTrue();
        assertThat(paths.has("/api/chat/readiness")).isTrue();
    }

    @Then("obsolete Calendar and Chat REST data-plane routes are absent from OpenAPI")
    public void obsoleteCalendarAndChatRestDataPlaneRoutesAreAbsentFromOpenapi() {
        // OPENAPI_LEGACY_DATA_PLANE_REMOVED
        JsonNode paths = lastJson.path("paths");
        assertThat(paths.has("/api/calendar/events")).isFalse();
        assertThat(paths.has("/api/calendar/events/{id}")).isFalse();
        assertThat(paths.has("/api/chat/conversations")).isFalse();
        assertThat(paths.has("/api/chat/conversations/{conversationId}/messages")).isFalse();
    }

    @Then("it does not expose durable Files, Calendar, or Chat member data-plane routes")
    public void itDoesNotExposeDurableFilesCalendarOrChatMemberDataPlaneRoutes() {
        JsonNode paths = lastJson.path("paths");
        assertThat(paths.has("/api/files/{id}/download")).isFalse();
        assertThat(paths.has("/api/files/{id}")).isFalse();
        assertThat(paths.has("/api/chat/messages")).isFalse();
        assertThat(paths.has("/api/calendar/caldav/{path}")).isFalse();
    }

    @Then("no provider credential, provider URL, SecretRef value, bearer token value, app password, or raw downstream payload is exposed")
    public void noProviderCredentialProviderUrlSecretrefValueBearerTokenValueAppPasswordOrRawDownstreamPayloadIsExposed() {
        // NO_PROVIDER_CREDENTIALS_CONTRACT
        // PROTOCOL_SURFACE_NO_PROVIDER_CREDENTIALS
        assertSupportSafeResponses();
    }

    @Then("subsequent Files setup credential use fails support-safely")
    public void subsequentFilesSetupCredentialUseFailsSupportSafely() {
        // FILES_WEBDAV_DEVICE_CREDENTIAL_CONTROL_PLANE
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwtPrincipal(), null));
        assertThatThrownBy(() -> filesFacadeService.requireActiveSetupCredential(filesCredentialId))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("files-setup-credential-revoked");
                    assertThat(exception.details())
                            .containsEntry("webDavFacadePath", "/dav/files")
                            .containsEntry("diagnosticsRedacted", true);
                    assertThat(exception.getMessage()).doesNotContain("Nextcloud", "Bearer", "app_password");
                });
    }

    private void perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder)
            throws Exception {
        MvcResult result = mockMvc.perform(builder.with(caller)).andReturn();
        assertThat(result.getResponse().getStatus()).isBetween(200, 299);
        String body = result.getResponse().getContentAsString();
        responseBodies.add(body);
        if (!body.isBlank()) {
            lastJson = objectMapper.readTree(body);
        }
    }

    private RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.weave.test/realms/weave")
                        .claim("aud", List.of("weave-app"))
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                        .claim("groups", List.of("weave-file-uploaders")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }

    private Jwt jwtPrincipal() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.weave.test/realms/weave")
                .claim("aud", List.of("weave-app"))
                .claim("weave_tenant_id", "tenant-default")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                .claim("groups", List.of("weave-file-uploaders"))
                .build();
    }

    private void assertSupportSafeResponses() {
        String combined = String.join("\n", responseBodies);
        assertThat(combined).doesNotContain(FORBIDDEN_FRAGMENTS.toArray(String[]::new));
    }
}
