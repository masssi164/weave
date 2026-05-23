package com.massimotter.weave.backend.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public class ProviderStackReadinessStepDefinitions {

    private static final Set<String> FORBIDDEN_SUPPORT_BUNDLE_FRAGMENTS = Set.of(
            "secret-api-token",
            "access_token=",
            "refresh_token=",
            "CI_JOB_TOKEN=",
            "webhook_secret=",
            "app_password=",
            "Authorization: Bearer",
            "Bearer secret",
            "raw upstream status=",
            "NullPointerException",
            "stackTrace",
            "org.springframework.web");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private RequestPostProcessor caller;
    private MvcResult lastResult;
    private JsonNode lastJson;
    private final List<String> responseBodies = new ArrayList<>();

    @Before
    public void resetProviderStackScenario() {
        caller = null;
        lastResult = null;
        lastJson = null;
        responseBodies.clear();
    }

    @Given("a workspace-scoped product caller")
    public void aWorkspaceScopedProductCaller() {
        caller = jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("aud", List.of("weave-app")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }

    @When("the product app requests provider readiness through Weave")
    public void theProductAppRequestsProviderReadinessThroughWeave() throws Exception {
        perform(get("/api/providers/status"));
    }

    @When("the product app requests profile readiness through Weave")
    public void theProductAppRequestsProfileReadinessThroughWeave() throws Exception {
        perform(get("/api/profile/readiness"));
    }

    @When("the product app requests DevOps summary for workspace {string} and channel {string}")
    public void theProductAppRequestsDevOpsSummaryForWorkspaceAndChannel(String workspaceId, String channelId)
            throws Exception {
        perform(get("/api/workspaces/{workspaceId}/channels/{channelId}/devops/summary", workspaceId, channelId));
    }

    @When("the product app requests Office capabilities through Weave")
    public void theProductAppRequestsOfficeCapabilitiesThroughWeave() throws Exception {
        perform(get("/api/office/capabilities"));
    }

    @When("the product app safely requests an Office launch session for file {string} in mode {string}")
    public void theProductAppSafelyRequestsAnOfficeLaunchSessionForFileInMode(String fileId, String mode)
            throws Exception {
        perform(post("/api/office/launch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fileId", fileId, "requestedMode", mode))));
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(status);
    }

    @Then("the provider registry is visible through {string}")
    public void theProviderRegistryIsVisibleThrough(String route) {
        assertThat(route).isEqualTo("GET /api/providers/status");
        assertThat(lastJson.path("releaseStatus").asText()).isEqualTo("provider-stack-contract-preview");
        assertThat(lastJson.path("providers")).isNotEmpty();
    }

    @Then("backend-owned facades are required")
    public void backendOwnedFacadesAreRequired() {
        assertThat(lastJson.path("backendOwnedFacades").asBoolean()).isTrue();
    }

    @Then("direct Flutter provider calls are refused by contract")
    public void directFlutterProviderCallsAreRefusedByContract() {
        assertThat(lastJson.path("flutterDirectProviderCallsAllowed").asBoolean()).isFalse();
        assertThat(allUnsupportedOperations()).anyMatch(operation -> operation.contains("direct-flutter"));
    }

    @Then("profile readiness is visible through {string}")
    public void profileReadinessIsVisibleThrough(String route) {
        assertThat(route).isEqualTo("GET /api/profile/readiness");
        assertThat(lastJson.path("contractId").asText()).isEqualTo("CEFACADE");
    }

    @Then("profile readiness uses CEFACADE at {string}")
    public void profileReadinessUsesCefacadeAt(String endpoint) {
        assertThat(lastJson.path("contractId").asText()).isEqualTo("CEFACADE");
        assertThat(lastJson.path("endpoint").asText()).isEqualTo(endpoint);
    }

    @Then("profile readiness is backend-owned support-safe and forbids direct provider calls")
    public void profileReadinessIsBackendOwnedSupportSafeAndForbidsDirectProviderCalls() {
        assertThat(lastJson.path("backendOwnedFacade").asBoolean()).isTrue();
        assertThat(lastJson.path("directProviderCallsAllowed").asBoolean()).isFalse();
        assertThat(lastJson.path("supportSafe").asBoolean()).isTrue();
        assertThat(lastJson.path("unsupportedOperations").toString()).contains("direct-frontend-keycloak-admin");
    }

    @Then("provider modules include files calendar boards office contacts forms matrix matrix-auth meetings source-control issue-tracker ci release and identity-realm")
    public void providerModulesIncludeExpectedProviderStackContracts() {
        assertThat(providerModules()).contains(
                "identity-realm",
                "files",
                "calendar",
                "boards",
                "office",
                "contacts",
                "forms",
                "matrix",
                "matrix-auth",
                "meetings",
                "source-control",
                "issue-tracker",
                "ci",
                "release");
    }

    @Then("Identity Forms Contacts Matrix MAS and Meetings readiness is support-safe")
    public void identityFormsContactsMatrixMasAndMeetingsReadinessIsSupportSafe() {
        assertProviderDependencyReference("identity-realm");
        assertProviderDependencyReference("forms");
        assertProviderDependencyReference("contacts");

        JsonNode matrix = providerByModule("matrix");
        assertThat(matrix.path("providerKey").asText()).isEqualTo("synapse-homeserver");
        assertThat(matrix.path("unsupportedOperations").toString()).contains("server-readable-e2ee-message-content");
        assertThat(matrix.at("/diagnostics/directClientProtocolException").asBoolean()).isTrue();
        assertThat(matrix.at("/diagnostics/messageBodiesServerReadable").asBoolean()).isFalse();

        JsonNode matrixAuth = providerByModule("matrix-auth");
        assertThat(matrixAuth.path("providerKey").asText()).isEqualTo("matrix-authentication-service");
        assertThat(matrixAuth.path("unsupportedOperations").toString()).contains("direct-backend-token-login");
        assertThat(matrixAuth.at("/diagnostics/upstreamIdentityProvider").asText()).isEqualTo("keycloak");

        JsonNode meetings = providerByModule("meetings");
        assertThat(meetings.path("providerKey").asText()).isEqualTo("matrix-meetings");
        assertThat(meetings.path("readiness").asText()).isEqualTo("not_configured");
        assertThat(meetings.path("unsupportedOperations").toString()).contains("video-calls-mvp");
        assertThat(meetings.at("/diagnostics/mvpScope").asText()).isEqualTo("deferred");
    }

    @Then("disabled or unconfigured optional providers fail closed")
    public void disabledOrUnconfiguredOptionalProvidersFailClosed() {
        for (JsonNode provider : iterable(lastJson.path("providers"))) {
            String module = provider.path("module").asText();
            if (Set.of("identity-realm", "contacts", "forms", "matrix", "matrix-auth", "meetings", "source-control", "issue-tracker", "ci", "release", "office")
                    .contains(module)) {
                assertThat(provider.path("enabled").asBoolean()).as(module + " enabled").isFalse();
                assertThat(provider.path("configured").asBoolean()).as(module + " configured").isFalse();
                assertThat(provider.path("failClosed").asBoolean()).as(module + " failClosed").isTrue();
                assertThat(provider.path("supportSafe").asBoolean()).as(module + " supportSafe").isTrue();
            }
        }
    }

    @Then("DevOps readiness is read-only support-safe and not configured")
    public void devOpsReadinessIsReadOnlySupportSafeAndNotConfigured() {
        assertThat(lastJson.path("readOnly").asBoolean()).isTrue();
        assertThat(lastJson.path("supportSafe").asBoolean()).isTrue();
        assertThat(lastJson.path("paidFeaturesRequired").asBoolean()).isFalse();
        assertThat(modulesFrom(lastJson.path("providerReadiness"))).contains("source-control", "issue-tracker", "ci", "release");
        for (JsonNode provider : iterable(lastJson.path("providerReadiness"))) {
            assertThat(provider.path("readiness").asText()).isEqualTo("not_configured");
            assertThat(provider.path("enabled").asBoolean()).isFalse();
            assertThat(provider.path("configured").asBoolean()).isFalse();
            assertThat(provider.path("readOnly").asBoolean()).isTrue();
            assertThat(provider.path("failClosed").asBoolean()).isTrue();
            assertThat(provider.path("supportSafe").asBoolean()).isTrue();
            assertThat(provider.path("unsupportedOperations").toString()).contains("premium-ultimate-only-features");
        }
    }

    @Then("disabled optional DevOps providers expose no linked projects repositories issues merge requests pipelines or releases")
    public void disabledOptionalDevOpsProvidersExposeNoProductData() {
        assertThat(lastJson.path("linkedProjects")).isEmpty();
        assertThat(lastJson.path("repositories")).isEmpty();
        assertThat(lastJson.path("openIssues")).isEmpty();
        assertThat(lastJson.path("mergeRequests")).isEmpty();
        assertThat(lastJson.path("pipelines")).isEmpty();
        assertThat(lastJson.path("releases")).isEmpty();
    }

    @Then("Office capabilities are not configured and promise no edit session")
    public void officeCapabilitiesAreNotConfiguredAndPromiseNoEditSession() {
        assertThat(lastJson.path("enabled").asBoolean()).isFalse();
        assertThat(lastJson.path("configured").asBoolean()).isFalse();
        assertThat(lastJson.path("supportSafe").asBoolean()).isTrue();
        assertThat(lastJson.path("launchMode").asText()).isEqualTo("unavailable");
        assertThat(lastJson.path("defaultProvider").asText()).isEqualTo("onlyoffice-community");
        assertThat(lastJson.at("/capabilities/view").asBoolean()).isFalse();
        assertThat(lastJson.at("/capabilities/edit").asBoolean()).isFalse();
        assertThat(lastJson.at("/capabilities/comment").asBoolean()).isFalse();
        assertThat(lastJson.at("/capabilities/review").asBoolean()).isFalse();
        assertThat(lastJson.at("/capabilities/formFill").asBoolean()).isFalse();
        assertThat(providerKeysFrom(lastJson.path("candidates"))).contains("onlyoffice-community", "collabora-code");
        assertThat(iterable(lastJson.path("providerReadiness")))
                .anySatisfy(provider -> assertThat(provider.path("failClosed").asBoolean()).isTrue());
    }

    @Then("Office launch is refused support-safely with {string}")
    public void officeLaunchIsRefusedSupportSafelyWith(String code) {
        assertThat(lastJson.path("code").asText()).isEqualTo(code);
        assertThat(lastJson.at("/details/module").asText()).isEqualTo("office");
        assertThat(lastJson.at("/details/operation").asText()).isEqualTo("launch");
        assertThat(lastJson.at("/details/supportSafe").asBoolean()).isTrue();
        assertThat(lastJson.at("/details/reason").asText()).contains("requestedMode=edit");
    }

    @Then("no provider secrets or raw provider errors are exposed")
    public void noProviderSecretsOrRawProviderErrorsAreExposed() {
        assertThat(responseBodies).isNotEmpty();
        for (String body : responseBodies) {
            for (String forbidden : FORBIDDEN_SUPPORT_BUNDLE_FRAGMENTS) {
                assertThat(body).doesNotContain(forbidden);
            }
        }
    }

    private void perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder) throws Exception {
        assertThat(caller).as("workspace caller must be initialized by the Background step").isNotNull();
        lastResult = mockMvc.perform(requestBuilder.with(caller)).andReturn();
        String body = lastResult.getResponse().getContentAsString();
        responseBodies.add(body);
        lastJson = objectMapper.readTree(body);
    }

    private Set<String> providerModules() {
        return modulesFrom(lastJson.path("providers"));
    }

    private Set<String> modulesFrom(JsonNode providers) {
        return StreamSupport.stream(providers.spliterator(), false)
                .map(provider -> provider.path("module").asText())
                .collect(Collectors.toSet());
    }

    private Set<String> providerKeysFrom(JsonNode providers) {
        return StreamSupport.stream(providers.spliterator(), false)
                .map(provider -> provider.path("providerKey").asText())
                .collect(Collectors.toSet());
    }

    private List<String> allUnsupportedOperations() {
        List<String> operations = new ArrayList<>();
        for (JsonNode provider : iterable(lastJson.path("providers"))) {
            for (JsonNode operation : iterable(provider.path("unsupportedOperations"))) {
                operations.add(operation.asText());
            }
        }
        return operations;
    }

    private void assertProviderDependencyReference(String module) {
        JsonNode provider = providerByModule(module);
        assertThat(provider).as(module + " provider status").isNotNull();
        assertThat(provider.at("/diagnostics/dependency").asText()).startsWith("weave-backend#");
        assertThat(provider.at("/diagnostics/compatibleSeam").asBoolean()).isTrue();
    }

    private JsonNode providerByModule(String module) {
        for (JsonNode provider : iterable(lastJson.path("providers"))) {
            if (module.equals(provider.path("module").asText())) {
                return provider;
            }
        }
        return null;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node::elements;
    }
}
