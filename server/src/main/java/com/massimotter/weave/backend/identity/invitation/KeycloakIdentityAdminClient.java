package com.massimotter.weave.backend.identity.invitation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** Concrete, backend-only Keycloak Organizations anti-corruption boundary. */
@Component
public class KeycloakIdentityAdminClient {
    private final IdentityInvitationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private volatile CachedToken cachedToken;
    private volatile String resolvedOrganizationId;

    @Autowired
    public KeycloakIdentityAdminClient(IdentityInvitationProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(properties.keycloak().timeout()).build(), Clock.systemUTC());
    }

    KeycloakIdentityAdminClient(IdentityInvitationProperties properties, ObjectMapper mapper, HttpClient client, Clock clock) {
        this.properties = properties; this.objectMapper = mapper; this.httpClient = client; this.clock = clock;
    }

    public ProviderInvitation issue(String organizationId, String email, String displayName) {
        Set<String> previous = ids(list(organizationId, email));
        String[] names = splitName(displayName);
        request("POST", adminPath("/organizations/" + encode(organizationId) + "/members/invite-user"),
                form("email", email, "firstName", names[0], "lastName", names[1]), "application/x-www-form-urlencoded", 204);
        List<ProviderInvitation> created = list(organizationId, email).stream()
                .filter(invitation -> !previous.contains(invitation.providerInvitationId())).toList();
        if (created.size() != 1) throw new IllegalStateException("Keycloak invitation result was ambiguous");
        return created.getFirst();
    }

    public List<ProviderInvitation> list(String organizationId) { return list(organizationId, null); }

    public String configuredOrganizationId() {
        if (!properties.keycloak().organizationId().isBlank()) return properties.keycloak().organizationId();
        if (resolvedOrganizationId != null) return resolvedOrganizationId;
        List<JsonNode> matches = values(json(request("GET", adminPath("/organizations?search="
                + encode(properties.keycloak().organizationAlias()) + "&exact=true"), null, null, 200)))
                .filter(org -> properties.keycloak().organizationAlias().equals(org.path("alias").asText())).toList();
        resolvedOrganizationId = exactlyOne(matches, "organization").path("id").asText();
        return resolvedOrganizationId;
    }

    public ProviderInvitation resend(String organizationId, String providerInvitationId) {
        request("POST", invitationPath(organizationId, providerInvitationId) + "/resend", "", "application/json", 204);
        return requireInvitation(organizationId, providerInvitationId);
    }

    public void revoke(String organizationId, String providerInvitationId) {
        request("DELETE", invitationPath(organizationId, providerInvitationId), null, null, 204);
    }

    public boolean isOrganizationMember(String organizationId, String subject) {
        try {
            JsonNode member = json(request("GET", adminPath("/organizations/" + encode(organizationId)
                    + "/members/" + encode(subject)), null, null, 200));
            return subject.equals(member.path("id").asText());
        } catch (KeycloakAdminException exception) {
            if (exception.status() == 404) return false;
            throw exception;
        }
    }

    public void applyRoleAndGroups(String subject, String role, List<String> groups) {
        String roleGroupPath = canonicalRoleGroupPath(role);
        List<ResolvedGroup> resolvedGroups = groups.stream()
                .map(groupPath -> {
                    canonicalGroupName(groupPath);
                    if (!roleGroupPath.equals(groupPath)) {
                        throw new IllegalArgumentException(
                                "Organization group must match the selected canonical human role");
                    }
                    return resolveCanonicalGroup(groupPath);
                })
                .toList();
        String clientUuid = exactlyOne(values(json(request("GET", adminPath("/clients?clientId=weave-app"), null, null, 200)))
                .filter(client -> "weave-app".equals(client.path("clientId").asText())).toList(), "weave-app client").path("id").asText();
        JsonNode roleRepresentation = json(request("GET", adminPath("/clients/" + encode(clientUuid) + "/roles/" + encode(role)), null, null, 200));
        request("POST", adminPath("/users/" + encode(subject) + "/role-mappings/clients/" + encode(clientUuid)),
                "[" + roleRepresentation.toString() + "]", "application/json", 204);
        for (ResolvedGroup resolved : resolvedGroups) {
            request("PUT", adminPath("/users/" + encode(subject) + "/groups/" + encode(resolved.id())), null, null, 204);
        }
    }

    private ResolvedGroup resolveCanonicalGroup(String groupPath) {
        String groupName = canonicalGroupName(groupPath);
        JsonNode root = exactlyOne(values(json(request(
                        "GET",
                        adminPath("/groups?search=weave&exact=true&first=0&max=2"),
                        null,
                        null,
                        200)))
                .filter(candidate -> "/weave".equals(candidate.path("path").asText()))
                .toList(), "organization group root");
        JsonNode child = exactlyOne(values(json(request(
                        "GET",
                        adminPath("/groups/" + encode(root.path("id").asText())
                                + "/children?first=0&max=100"),
                        null,
                        null,
                        200)))
                .filter(candidate -> groupName.equals(candidate.path("name").asText()))
                .filter(candidate -> groupPath.equals(candidate.path("path").asText()))
                .toList(), "organization group path");
        return new ResolvedGroup(child.path("id").asText());
    }

    private String canonicalRoleGroupPath(String role) {
        return switch (role) {
            case "owner" -> "/weave/owners";
            case "admin" -> "/weave/admins";
            case "member" -> "/weave/members";
            case "guest" -> "/weave/guests";
            default -> throw new IllegalArgumentException("Role must be an exact canonical human role");
        };
    }

    private String canonicalGroupName(String groupPath) {
        if (groupPath == null || !groupPath.matches("^/weave/(owners|admins|members|guests)$")) {
            throw new IllegalArgumentException("Organization group must be an exact canonical human group path");
        }
        return groupPath.substring(groupPath.lastIndexOf('/') + 1);
    }

    private List<ProviderInvitation> list(String organizationId, String email) {
        String suffix = email == null ? "" : "?email=" + encode(email);
        return values(json(request("GET", adminPath("/organizations/" + encode(organizationId) + "/invitations" + suffix), null, null, 200)))
                .filter(node -> email == null || email.equalsIgnoreCase(node.path("email").asText()))
                .map(this::projection).toList();
    }
    private ProviderInvitation requireInvitation(String org, String id) {
        return list(org).stream().filter(i -> id.equals(i.providerInvitationId())).findFirst()
                .orElseThrow(() -> new KeycloakAdminException(404, "Keycloak invitation is unavailable"));
    }
    private ProviderInvitation projection(JsonNode node) {
        String id = required(node, "id");
        String email = required(node, "email");
        String display = (node.path("firstName").asText("") + " " + node.path("lastName").asText("")).trim();
        String status = node.path("status").asText("pending").toLowerCase();
        return new ProviderInvitation(id, email, display.isBlank() ? null : display, status,
                instant(node, "expiresAt", "expiration"), instant(node, "createdAt", "sentDate"));
    }
    private Instant instant(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isIntegralNumber()) {
                long epoch = value.asLong(); return Instant.ofEpochMilli(epoch < 10_000_000_000L ? epoch * 1000 : epoch);
            }
            if (value.isTextual() && !value.asText().isBlank()) try { return Instant.parse(value.asText()); } catch (RuntimeException ignored) { }
        }
        return null;
    }
    private Set<String> ids(List<ProviderInvitation> values) {
        Set<String> ids = new HashSet<>(); values.forEach(value -> ids.add(value.providerInvitationId())); return ids;
    }
    private JsonNode exactlyOne(List<JsonNode> values, String name) {
        if (values.size() != 1 || values.getFirst().path("id").asText().isBlank())
            throw new IllegalStateException("Configured Keycloak " + name + " is unavailable or ambiguous");
        return values.getFirst();
    }
    private String required(JsonNode node, String field) {
        String value = node.path(field).asText(""); if (value.isBlank()) throw new IllegalStateException("Keycloak returned an invalid invitation projection"); return value;
    }
    private String invitationPath(String org, String id) { return adminPath("/organizations/" + encode(org) + "/invitations/" + encode(id)); }
    private String request(String method, String path, String body, String contentType, int expected) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path)).timeout(properties.keycloak().timeout())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken());
        if (contentType != null) builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != expected) throw new KeycloakAdminException(response.statusCode(), "Keycloak administration operation failed");
            return response.body();
        } catch (IOException e) { throw new IllegalStateException("Keycloak administration is unavailable", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Keycloak administration was interrupted", e); }
    }
    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && cachedToken.refreshAfter().isAfter(now)) return cachedToken.value();
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (properties.keycloak().clientId() + ":" + properties.keycloak().clientSecret())
                        .getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(resolve("/realms/" + encode(properties.keycloak().realm()) + "/protocol/openid-connect/token"))
                .timeout(properties.keycloak().timeout()).header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .POST(HttpRequest.BodyPublishers.ofString(form("grant_type", "client_credentials"))).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new KeycloakAdminException(response.statusCode(), "Keycloak service-account authentication failed");
            JsonNode token = json(response.body()); String value = required(token, "access_token"); long lifetime = Math.max(10, token.path("expires_in").asLong(60));
            cachedToken = new CachedToken(value, now.plusSeconds(Math.max(5, lifetime - 15))); return value;
        } catch (IOException e) { throw new IllegalStateException("Keycloak authentication is unavailable", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Keycloak authentication was interrupted", e); }
    }
    private JsonNode json(String body) { try { return objectMapper.readTree(body); } catch (JacksonException e) { throw new IllegalStateException("Keycloak returned an invalid response", e); } }
    private URI resolve(String path) { return properties.keycloak().baseUrl().resolve(path); }
    private String adminPath(String suffix) { return "/admin/realms/" + encode(properties.keycloak().realm()) + suffix; }
    private String form(String... values) { StringBuilder result = new StringBuilder(); for (int i=0;i<values.length;i+=2) { if (!result.isEmpty()) result.append('&'); result.append(encode(values[i])).append('=').append(encode(values[i+1])); } return result.toString(); }
    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private String[] splitName(String display) { if (display == null || display.isBlank()) return new String[]{"",""}; String[] parts=display.trim().split("\\s+",2); return new String[]{parts[0],parts.length>1?parts[1]:""}; }
    private Stream<JsonNode> values(JsonNode node) { return StreamSupport.stream(node.spliterator(), false); }

    private record CachedToken(String value, Instant refreshAfter) {}
    private record ResolvedGroup(String id) {}
    public record ProviderInvitation(String providerInvitationId, String email, String displayName,
            String lifecycleStatus, Instant expiresAt, Instant createdAt) {}
    public static final class KeycloakAdminException extends RuntimeException {
        private final int status;
        KeycloakAdminException(int status, String message) { super(message + " with sanitized status " + status); this.status=status; }
        public int status() { return status; }
    }
}
