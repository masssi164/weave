package com.massimotter.weave.backend.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
            "WEAVE_LIVEKIT_API_KEY=secret",
            "WEAVE_LIVEKIT_API_SECRET=secret",
            "livekit api secret",
            "room_token=",
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
                        .claim("iss", "https://auth.weave.test/realms/weave")
                        .claim("aud", List.of("weave-app")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }

    @Given("a workspace-scoped admin product caller")
    public void aWorkspaceScopedAdminProductCaller() {
        caller = jwt().jwt(jwt -> jwt
                        .subject("admin-123")
                        .claim("iss", "https://auth.weave.test/realms/weave")
                        .claim("aud", List.of("weave-app"))
                        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("admin")))))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
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
        assertThat(lastJson.path("releaseStatus").asString()).isEqualTo("provider-stack-contract-v1");
        assertThat(lastJson.path("providerConfigSource").asString()).isEqualTo("admin-control-plane-selected-provider-mappings");
        assertThat(lastJson.path("adminSelectedMappingsRequired").asBoolean()).isTrue();
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
        assertThat(lastJson.path("contractId").asString()).isEqualTo("CEFACADE");
    }

    @Then("profile readiness uses CEFACADE at {string}")
    public void profileReadinessUsesCefacadeAt(String endpoint) {
        assertThat(lastJson.path("contractId").asString()).isEqualTo("CEFACADE");
        assertThat(lastJson.path("endpoint").asString()).isEqualTo(endpoint);
    }

    @Then("profile readiness is backend-owned support-safe and forbids direct provider calls")
    public void profileReadinessIsBackendOwnedSupportSafeAndForbidsDirectProviderCalls() {
        assertThat(lastJson.path("backendOwnedFacade").asBoolean()).isTrue();
        assertThat(lastJson.path("directProviderCallsAllowed").asBoolean()).isFalse();
        assertThat(lastJson.path("supportSafe").asBoolean()).isTrue();
        assertThat(lastJson.path("unsupportedOperations").toString()).contains("direct-frontend-keycloak-admin");
    }

    @Then("provider modules include files calendar boards office meetings contacts forms source-control issue-tracker ci and release")
    public void providerModulesIncludeExpectedProviderStackContracts() {
        assertThat(providerModules()).contains(
                "files",
                "calendar",
                "boards",
                "meetings",
                "office",
                "contacts",
                "forms",
                "source-control",
                "issue-tracker",
                "ci",
                "release");
        assertThat(providerModules()).doesNotContain("identity-realm", "matrix-auth");
    }

    @Then("provider category contracts separate feature capabilities from default and external adapters")
    public void providerCategoryContractsSeparateFeatureCapabilitiesFromAdapters() {
        assertCategoryContract(
                "chat",
                Set.of("chat.read", "chat.send"),
                Set.of("synapse-homeserver"),
                Set.of("microsoft-teams"));
        assertCategoryContract(
                "files",
                Set.of("files.read", "files.upload"),
                Set.of("nextcloud-files"),
                Set.of("sharepoint", "onedrive"));
        assertCategoryContract(
                "documents-collaboration",
                Set.of("documents.view", "documents.edit", "documents.collaborate"),
                Set.of("onlyoffice"),
                Set.of("microsoft-365-office"));
    }

    @Then("provider choice models include recommended self-hosted defaults and risk-aware external providers")
    public void providerChoiceModelsIncludeRecommendedSelfHostedDefaultsAndRiskAwareExternalProviders() {
        for (String categoryKey : List.of("chat", "files", "boards-tasks")) {
            JsonNode contract = categoryByKey(categoryKey).path("contract");
            assertThat(stringsFrom(contract.path("choiceModels").findValues("choiceModel")))
                    .contains("recommended_self_hosted_default", "external_existing_provider", "managed_cloud_provider");
            assertThat(contract.path("choiceModels").toString())
                    .contains("privacy", "compliance", "vendor lock-in")
                    .doesNotContain("Authorization", "Bearer", "access_token");
        }
    }

    @Then("a mixed provider posture can keep Teams chat SharePoint files and OpenProject tasks behind stable category contracts")
    public void mixedProviderPostureKeepsStableCategoryContracts() {
        assertThat(stringsFrom(categoryByKey("chat").at("/contract/externalAdapters")))
                .contains("microsoft-teams");
        assertThat(stringsFrom(categoryByKey("files").at("/contract/externalAdapters")))
                .contains("sharepoint", "onedrive");
        assertThat(stringsFrom(categoryByKey("boards-tasks").at("/contract/defaultAdapters")))
                .contains("openproject-primary");

        for (String categoryKey : List.of("chat", "files", "boards-tasks")) {
            assertThat(stringsFrom(categoryByKey(categoryKey).at("/contract/stableMemberImpactStates")))
                    .containsExactlyInAnyOrder(
                            "available",
                            "disabled_by_policy",
                            "not_configured",
                            "degraded",
                            "unavailable",
                            "coming_later");
        }
    }

    @Then("member impact states are stable across provider adapters")
    public void memberImpactStatesAreStableAcrossProviderAdapters() {
        for (JsonNode category : iterable(lastJson.path("categories"))) {
            assertThat(stringsFrom(category.at("/contract/stableMemberImpactStates")))
                    .containsExactlyInAnyOrder(
                            "available",
                            "disabled_by_policy",
                            "not_configured",
                            "degraded",
                            "unavailable",
                            "coming_later");
            assertThat(category.at("/contract/normalMembersConfigureProviders").asBoolean()).isFalse();
            assertThat(category.path("memberImpact").asString()).doesNotContain("secret", "Authorization", "access_token");
        }
    }

    @Then("Calls readiness treats LiveKit as a replaceable SFU and fails closed support-safely")
    public void callsReadinessTreatsLiveKitAsAReplaceableSfuAndFailsClosedSupportSafely() {
        JsonNode provider = providerByModule("meetings");
        assertThat(provider).as("meetings provider status").isNotNull();
        assertThat(provider.path("providerKey").asString()).isEqualTo("livekit");
        assertThat(provider.path("configured").asBoolean()).isFalse();
        assertThat(provider.path("failClosed").asBoolean()).isTrue();
        assertThat(provider.path("supportSafe").asBoolean()).isTrue();
        assertThat(provider.at("/diagnostics/activeSfuAdapter").asString()).isEqualTo("livekit");
        assertThat(provider.at("/diagnostics/livekitUrlConfigured").asBoolean()).isFalse();
        assertThat(provider.at("/diagnostics/apiKeyConfigured").asBoolean()).isFalse();
        assertThat(provider.at("/diagnostics/apiSecretConfigured").asBoolean()).isFalse();
        assertThat(provider.at("/diagnostics/tokenEndpointConfigured").asBoolean()).isFalse();
        assertThat(provider.toString()).doesNotContain("matrix-meetings");
    }

    @Then("platform identity is absent from the provider patch panel while Forms and Contacts keep dependent seams")
    public void identityReadinessIsKeycloakMediatedWhileOptionalSeamsRemainDependent() {
        assertThat(providerModules()).doesNotContain("identity-realm", "matrix-auth");
        assertThat(iterable(lastJson.path("categories")))
                .allSatisfy(category -> assertThat(category.path("category").asString())
                        .isNotEqualTo("identity-idm"));
        assertProviderDependencyReference("forms");
        assertProviderDependencyReference("contacts");
    }

    @Then("disabled or unconfigured optional providers fail closed")
    public void disabledOrUnconfiguredOptionalProvidersFailClosed() {
        for (JsonNode provider : iterable(lastJson.path("providers"))) {
            String module = provider.path("module").asString();
            if (Set.of("contacts", "forms", "source-control", "issue-tracker", "ci", "release", "office")
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
            assertThat(provider.path("readiness").asString()).isEqualTo("not_configured");
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
        assertThat(lastJson.path("launchMode").asString()).isEqualTo("unavailable");
        assertThat(lastJson.path("defaultProvider").asString()).isEqualTo("onlyoffice");
        assertThat(lastJson.at("/capabilities/view").asBoolean()).isFalse();
        assertThat(lastJson.at("/capabilities/edit").asBoolean()).isFalse();
        assertThat(lastJson.at("/capabilities/comment").asBoolean()).isFalse();
        assertThat(lastJson.at("/capabilities/review").asBoolean()).isFalse();
        assertThat(lastJson.at("/capabilities/formFill").asBoolean()).isFalse();
        assertThat(providerKeysFrom(lastJson.path("candidates"))).contains("onlyoffice", "collabora");
        assertThat(iterable(lastJson.path("providerReadiness")))
                .anySatisfy(provider -> assertThat(provider.path("failClosed").asBoolean()).isTrue());
    }

    @Then("Office launch is refused support-safely with {string}")
    public void officeLaunchIsRefusedSupportSafelyWith(String code) {
        assertThat(lastJson.path("code").asString()).isEqualTo(code);
        assertThat(lastJson.at("/details/module").asString()).isEqualTo("office");
        assertThat(lastJson.at("/details/operation").asString()).isEqualTo("launch");
        assertThat(lastJson.at("/details/supportSafe").asBoolean()).isTrue();
        assertThat(lastJson.at("/details/reason").asString()).contains("requestedMode=edit");
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
                .map(provider -> provider.path("module").asString())
                .collect(Collectors.toSet());
    }

    private Set<String> providerKeysFrom(JsonNode providers) {
        return StreamSupport.stream(providers.spliterator(), false)
                .map(provider -> provider.path("providerKey").asString())
                .collect(Collectors.toSet());
    }

    private List<String> allUnsupportedOperations() {
        List<String> operations = new ArrayList<>();
        for (JsonNode provider : iterable(lastJson.path("providers"))) {
            for (JsonNode operation : iterable(provider.path("unsupportedOperations"))) {
                operations.add(operation.asString());
            }
        }
        return operations;
    }

    private void assertProviderDependencyReference(String module) {
        JsonNode provider = providerByModule(module);
        assertThat(provider).as(module + " provider status").isNotNull();
        assertThat(provider.at("/diagnostics/dependency").asString()).startsWith("weave-backend#");
        assertThat(provider.at("/diagnostics/compatibleSeam").asBoolean()).isTrue();
    }

    private JsonNode providerByModule(String module) {
        for (JsonNode provider : iterable(lastJson.path("providers"))) {
            if (module.equals(provider.path("module").asString())) {
                return provider;
            }
        }
        return null;
    }

    private void assertCategoryContract(
            String categoryKey,
            Set<String> featureCapabilities,
            Set<String> defaultAdapters,
            Set<String> externalAdapters) {
        JsonNode category = categoryByKey(categoryKey);
        assertThat(category).as(categoryKey + " category").isNotNull();
        JsonNode contract = category.path("contract");
        assertThat(contract.path("category").asString()).isEqualTo(categoryKey);
        assertThat(stringsFrom(contract.path("featureCapabilities"))).containsAll(featureCapabilities);
        assertThat(stringsFrom(contract.path("defaultAdapters"))).containsAll(defaultAdapters);
        assertThat(stringsFrom(contract.path("externalAdapters"))).containsAll(externalAdapters);
        assertThat(contract.path("adminSelectable").asBoolean()).isTrue();
        assertThat(contract.path("normalMembersConfigureProviders").asBoolean()).isFalse();
    }

    private JsonNode categoryByKey(String categoryKey) {
        for (JsonNode category : iterable(lastJson.path("categories"))) {
            if (categoryKey.equals(category.path("category").asString())) {
                return category;
            }
        }
        return null;
    }

    private Set<String> stringsFrom(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
    }

    private Set<String> stringsFrom(List<JsonNode> nodes) {
        return nodes.stream()
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node::iterator;
    }
}
