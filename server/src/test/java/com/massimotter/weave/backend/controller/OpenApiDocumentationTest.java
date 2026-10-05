package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "weave.identity.invitations.bootstrap-owner.enabled=true",
        "weave.identity.invitations.bootstrap-owner.token-file=/openapi-export/owner-bootstrap-token"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // Metadata export reads the real HTTP handlers and transport models; it does
    // not exercise the Matrix wire codec. Runtime/protocol tests retain JNI.
    @MockitoBean(enforceOverride = true)
    private MatrixProtocolCoreService matrixProtocolCore;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @AfterEach
    void metadataExportDoesNotInvokeMatrixProtocolRuntime() {
        verifyNoInteractions(matrixProtocolCore);
    }

    @Test
    void calendarProductRoutesHaveExplicitUserOperationsAndTypedTemporalPreconditions() throws Exception {
        mockMvc.perform(get("/v3/api-docs/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/calendar/calendars'].get.operationId").value("listUserCalendars"))
                .andExpect(jsonPath("$.paths['/api/calendar/calendars/{calendarId}/events'].get.operationId").value("queryCalendarAgenda"))
                .andExpect(jsonPath("$.paths['/api/calendar/calendars/{calendarId}/events'].post.operationId").value("createCalendarEvent"))
                .andExpect(jsonPath("$.paths['/api/calendar/calendars/{calendarId}/events/{eventId}'].get.operationId").value("getCalendarEvent"))
                .andExpect(jsonPath("$.paths['/api/calendar/calendars/{calendarId}/events/{eventId}'].put.operationId").value("updateCalendarEvent"))
                .andExpect(jsonPath("$.paths['/api/calendar/calendars/{calendarId}/events/{eventId}'].delete.operationId").value("deleteCalendarEvent"))
                .andExpect(jsonPath("$.paths['/api/calendar/calendars/{calendarId}/events/{eventId}'].put.responses['412']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/calendars/{calendarId}/events/{eventId}'].delete.responses['428']").exists())
                .andExpect(jsonPath("$.components.schemas.CalendarTimeValue.properties.kind.enum").value(hasItems("DATE", "FLOATING", "UTC", "ZONED")))
                .andExpect(jsonPath("$.components.schemas.CalendarTimeValue.properties.date.format").value("date"))
                .andExpect(jsonPath("$.components.schemas.CalendarTimeValue.properties.localDateTime.format").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CalendarTimeValue.additionalProperties").value(false))
                .andExpect(jsonPath("$.components.schemas.CalendarEventRecurrence.additionalProperties").value(false))
                .andExpect(jsonPath("$.components.schemas.CalendarEventOverride.additionalProperties").value(false))
                .andExpect(jsonPath("$.components.schemas.CalendarEventAttendee.additionalProperties").value(false))
                .andExpect(jsonPath("$.components.schemas.CalendarEventWriteRequest.additionalProperties").value(false))
                .andExpect(jsonPath("$.components.schemas.CalendarEventRecurrence.required").value(hasItems("interval")))
                .andExpect(jsonPath("$.components.schemas.CalendarUserEvent.properties.providerRef").doesNotExist());
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/calendar/calendars']").doesNotExist());
    }

    @Test
    void workspaceDiagnosticsAreAdminOnlyAndHomeCountsAreExplicitlyUnknown() throws Exception {
        mockMvc.perform(get("/v3/api-docs/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/workspace/capability-policy']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/workspace/release-readiness']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/workspace/capability-policy']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/workspace/release-readiness']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.WorkspaceCapabilityPolicyResponse").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.WorkspaceReleaseReadinessResponse").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeSectionResponse.properties.itemCount.type").value(hasItems("integer", "null")))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeSectionResponse.properties.itemCount.minimum").value(0));
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/workspace/capability-policy'].get.operationId").value("capabilityPolicy"))
                .andExpect(jsonPath("$.paths['/api/admin/workspace/release-readiness'].get.operationId").value("releaseReadiness"))
                .andExpect(jsonPath("$.paths['/api/admin/workspace/capability-policy'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/workspace/release-readiness'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/workspace/home']").doesNotExist());
    }

    @Test
    void exportedOperationIdsAreExplicitUniqueAndIdenticalAcrossDocuments() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode combined = mapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Set<String> httpMethods = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
        Map<String, String> operationIds = new HashMap<>();
        for (String group : new String[] {"user", "admin"}) {
            JsonNode document = mapper.readTree(mockMvc.perform(get("/v3/api-docs/" + group))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            for (var pathEntry : document.path("paths").properties()) {
                String path = pathEntry.getKey();
                for (var methodEntry : pathEntry.getValue().properties()) {
                    String method = methodEntry.getKey();
                    if (!httpMethods.contains(method)) {
                        continue;
                    }
                    String location = method.toUpperCase(java.util.Locale.ROOT) + " " + path;
                    var handlers = handlerMapping.getHandlerMethods().entrySet().stream()
                            .filter(entry -> entry.getKey().getPatternValues().contains(path))
                            .filter(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                                    .anyMatch(requestMethod -> requestMethod.name().equalsIgnoreCase(method)))
                            .map(Map.Entry::getValue)
                            .distinct()
                            .toList();
                    assertEquals(1, handlers.size(), "Expected one server handler for " + location);
                    Operation declared = handlers.getFirst().getMethodAnnotation(Operation.class);
                    assertNotNull(declared, "Missing explicit @Operation for " + location);
                    assertFalse(declared.operationId().isBlank(), "Missing explicit operationId for " + location);
                    assertEquals(declared.operationId(), methodEntry.getValue().path("operationId").asText(),
                            "Grouped export changed the declared operationId for " + location);
                    assertEquals(declared.operationId(),
                            combined.path("paths").path(path).path(method).path("operationId").asText(),
                            "Combined export changed the declared operationId for " + location);
                    assertNull(operationIds.putIfAbsent(declared.operationId(), location),
                            "Duplicate declared operationId " + declared.operationId() + " at " + location);
                }
            }
        }
        assertFalse(operationIds.isEmpty(), "No User/Admin operations were checked");
    }

    @Test
    void separatesUserAndAdminOperations() throws Exception {
        MvcResult user = mockMvc.perform(get("/v3/api-docs/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/me']").exists())
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}'].patch").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/move']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/copy']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/files/items/uploads'].post.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/files/items/uploads'].post.responses['415'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/files/items/folders'].post.responses['412'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/content'].get.responses['200'].headers['ETag'].schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/content'].get.responses['200'].headers['Content-Digest'].schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/content'].get.responses['200'].headers['Content-Length'].schema.type")
                        .value("integer"))
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/content'].get.responses['200'].headers['Content-Type'].schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/content'].get.responses['304'].headers['ETag'].schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/admin/control-plane']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/providers/status']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/providers/status']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/bootstrap/owner-invitation']").doesNotExist())
                .andReturn();
        MvcResult admin = mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/control-plane']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/providers/status'].get.operationId").value("status"))
                .andExpect(jsonPath("$.components.schemas.ProviderRegistryResponse.required", hasItems("organizationId", "filesBinding")))
                .andExpect(jsonPath("$.components.schemas.FilesBindingStatusResponse.properties.bindingState.enum",
                        hasItems("ACTIVE", "NO_ACTIVE_BINDING")))
                .andExpect(jsonPath("$.components.schemas.FilesBindingStatusResponse.properties.readiness.enum",
                        hasItems("CONFIGURED", "NOT_CONFIGURED", "UNAVAILABLE")))
                .andExpect(jsonPath("$.paths['/api/providers/status']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/bootstrap/owner-invitation']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/organizations/{organizationId}/invitations'].get.operationId")
                        .value("listOrganizationInvitations"))
                .andExpect(jsonPath("$.paths['/api/admin/organizations/{organizationId}/invitations'].get.responses['200'].content['*/*'].schema.type")
                        .value("array"))
                .andExpect(jsonPath("$.paths['/api/admin/organizations/{organizationId}/invitations'].get.responses['200'].content['*/*'].schema.items['$ref']")
                        .value("#/components/schemas/MemberInvitationResponse"))
                .andExpect(jsonPath("$.paths['/api/admin/organizations/{organizationId}/invitations/{invitationHandle}/resend'].post.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/MemberInvitationResponse"))
                .andExpect(jsonPath("$.paths['/api/admin/organizations/{organizationId}/invitations'].get.security[0]['bearer-jwt']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/me']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/agent-runtimes/{personRef}']").doesNotExist())
                .andReturn();

        String exportPath = System.getProperty("weave.openapi.export.path");
        if (exportPath != null && !exportPath.isBlank()) {
            Path directory = Path.of(exportPath).getParent();
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("weave-user-openapi.raw.json"),
                    user.getResponse().getContentAsString());
            Files.writeString(directory.resolve("weave-admin-openapi.raw.json"),
                    admin.getResponse().getContentAsString());
        }
    }

    @Test
    void exposesOpenApiDescription() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Weave Backend API"))
                .andExpect(jsonPath("$.paths['/api/me']").exists())
                .andExpect(jsonPath("$.paths['/api/health/live']").exists())
                .andExpect(jsonPath("$.paths['/api/health/ready']").exists())
                .andExpect(jsonPath("$.paths['/api/platform/config']").exists())
                .andExpect(jsonPath("$.paths['/api/platform/status']").exists())
                .andExpect(jsonPath("$.paths['/api/bootstrap/owner-invitation'].post.operationId")
                        .value("bootstrapOwnerInvitation"))
                .andExpect(jsonPath("$.paths['/api/profile']").exists())
                .andExpect(jsonPath("$.paths['/api/profile'].get").exists())
                .andExpect(jsonPath("$.paths['/api/profile'].get.operationId").value("getProductProfile"))
                .andExpect(jsonPath("$.paths['/api/profile'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/profile'].patch.operationId").value("updateProductProfile"))
                .andExpect(jsonPath("$.paths['/api/profile/sync-status']").exists())
                .andExpect(jsonPath("$.paths['/api/profile/sync-status'].get.operationId").value("getProductProfileSyncStatus"))
                .andExpect(jsonPath("$.paths['/api/files']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/files/upload']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/files/folders']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/files/items'].get.operationId").value("listFilesItems"))
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}'].get.operationId")
                        .value("getFilesItem"))
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/content'].get.operationId")
                        .value("downloadFilesItemContent"))
                .andExpect(jsonPath("$.paths['/api/files/items/folders'].post.operationId")
                        .value("createFilesFolder"))
                .andExpect(jsonPath("$.paths['/api/files/items/uploads'].post.operationId")
                        .value("uploadFilesItemContent"))
                .andExpect(jsonPath("$.paths['/api/files/items/{fileId}/content'].put.operationId")
                        .value("updateFilesItemContent"))
                .andExpect(jsonPath("$.paths['/api/files/readiness']").exists())
                .andExpect(jsonPath("$.paths['/api/files/readiness'].get.operationId").value("getFilesReadiness"))
                .andExpect(jsonPath("$.paths['/api/files/readiness'].get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/WorkspaceCapabilityStatusResponse"))
                .andExpect(jsonPath("$.paths['/api/files/native-provider-setup']").exists())
                .andExpect(jsonPath("$.paths['/api/files/native-provider-setup'].get.operationId")
                        .value("getFilesNativeProviderSetup"))
                .andExpect(jsonPath("$.paths['/api/files/native-provider-setup'].get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/FileNativeProviderSetupResponse"))
                .andExpect(jsonPath("$.paths['/api/files/client-setup/credentials']").exists())
                .andExpect(jsonPath("$.paths['/api/files/client-setup/credentials/{credentialId}']").exists())
                .andExpect(jsonPath("$.paths['/api/files/{id}/download']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/files/{id}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/calendar/events']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/calendar/client-setup']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/native-sync-setup']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/native-sync-setup'].get.operationId")
                        .value("getCalendarNativeSyncSetup"))
                .andExpect(jsonPath("$.paths['/api/calendar/native-sync-setup'].get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/CalendarNativeSyncSetupResponse"))
                .andExpect(jsonPath("$.paths['/api/calendar/access-policy']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/client-setup/credentials']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/client-setup/credentials/{credentialId}']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/client-setup/apple.mobileconfig']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/events/{id}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/calls']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/calls/native-boundary-setup']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/calls/{id}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/calls/{id}/join']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/workspace/capabilities']").exists())
                .andExpect(jsonPath("$.paths['/api/workspace/home'].get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/WorkspaceHomeResponse"))
                .andExpect(jsonPath("$.paths['/api/workspace/release-readiness']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/providers/status']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspace/capabilities']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/workspace/release-readiness']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/interop/status']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/slack/status']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/slack/oauth/callback']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/slack/events']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/slack/messages']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/teams/contract']").exists())
                .andExpect(jsonPath("$.paths['/api/guest/access-contract']").exists())
                .andExpect(jsonPath("$.paths['/api/guest/invitations']").exists())
                .andExpect(jsonPath("$.paths['/api/migration/dry-runs']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/providers/replacements/dry-run']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/control-plane'].get.operationId").value("getAdminControlPlane"))
                .andExpect(jsonPath("$.paths['/api/admin/policies/capability-whitelist'].get.operationId").value("getCapabilityWhitelist"))
                .andExpect(jsonPath("$.paths['/api/admin/policies/capability-whitelist'].patch.operationId").value("updateCapabilityWhitelist"))
                .andExpect(jsonPath("$.paths['/api/admin/providers/readiness-tests'].post.operationId").value("testProviderReadiness"))
                .andExpect(jsonPath("$.paths['/api/admin/provider-capability-health'].get.operationId")
                        .value("getProviderCapabilityHealth"))
                .andExpect(jsonPath("$.paths['/api/admin/providers/replacements/dry-run'].post.operationId").value("dryRunProviderReplacement"))
                .andExpect(jsonPath("$.paths['/api/chat/conversations']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/chat/conversations/{conversationId}/messages']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/chat/conversations/{conversationId}/weaver/scout/summaries']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/workspace/weaver/runtime-profile']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/workspace/weaver/mcp/servers/{serverKey}/tools']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/chat/readiness'].get.operationId").value("getAdminChatReadiness"))
                .andExpect(jsonPath("$.paths['/api/admin/chat/provider-replacements/dry-run'].post.operationId").value("dryRunChatProviderReplacement"))
                .andExpect(jsonPath("$.paths['/api/connectors/boundary']").exists())
                .andExpect(jsonPath("$.paths['/api/connectors/manifest/validate']").exists())
                .andExpect(jsonPath("$.components.schemas.BoardsWorkspaceResponse.properties.syncMetadata").exists())
                .andExpect(jsonPath("$.components.schemas.ProviderRegistryResponse.properties.providers").exists())
                .andExpect(jsonPath("$.components.schemas.ProviderStatusResponse.properties.providerKey.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ProviderStatusResponse.properties.diagnostics.type").value("object"))
                .andExpect(jsonPath("$.components.schemas.BoardsSyncMetadataResponse.properties.provider.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.BoardsSyncMetadataResponse.properties.nextCursors.type").value("object"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.code.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.message.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.requestId.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.supportRef.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.memberImpact.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceCapabilityStatusResponse.properties.supportRef.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeResponse.properties.recentActivity.type")
                        .value("array"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeResponse.properties.recentActivity.items['$ref']")
                        .value("#/components/schemas/WorkspaceHomeRecentActivityResponse"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.activityRef.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.domain.enum[0]")
                        .value("files"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.action.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.occurredAt.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.visibility.enum[0]")
                        .value("workspace"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.visibility.enum[1]")
                        .value("private"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.actorRefHash.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.actorIsCurrentUser.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.components.schemas.WorkspaceHomeRecentActivityResponse.properties.supportSafe.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.required", hasItems(
                        "code",
                        "message",
                        "details",
                        "requestId",
                        "supportRef")))
                .andExpect(jsonPath("$.components.responses.UnauthorizedError.description").value("Missing or invalid bearer token."))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes['owner-bootstrap-token'].type").value("apiKey"))
                .andReturn();

        String exportPath = System.getProperty("weave.openapi.export.path");
        if (exportPath != null && !exportPath.isBlank()) {
            Path path = Path.of(exportPath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, result.getResponse().getContentAsString());
        }
    }
}
