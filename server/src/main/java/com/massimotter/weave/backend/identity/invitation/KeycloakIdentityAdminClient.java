package com.massimotter.weave.backend.identity.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KeycloakIdentityAdminClient {
    private final IdentityInvitationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public KeycloakIdentityAdminClient(IdentityInvitationProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(properties.keycloak().timeout()).build());
    }

    KeycloakIdentityAdminClient(IdentityInvitationProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public ProviderInvitation issue(MemberInvitation invitation) {
        String organizationId = organizationId();
        String[] names = splitName(invitation.displayName());
        String body = form("email", invitation.invitedEmail(), "firstName", names[0], "lastName", names[1]);
        request("POST", adminPath("/organizations/" + encode(organizationId) + "/members/invite-user"), body,
                "application/x-www-form-urlencoded", 204);
        return newestInvitation(organizationId, invitation.invitedEmail());
    }

    public ProviderInvitation resend(MemberInvitation invitation) {
        requireProviderId(invitation);
        request("POST", adminPath("/organizations/" + encode(organizationId()) + "/invitations/"
                + encode(invitation.providerInvitationId()) + "/resend"), "", "application/json", 204);
        return newestInvitation(organizationId(), invitation.invitedEmail());
    }

    public void revoke(MemberInvitation invitation) {
        requireProviderId(invitation);
        request("DELETE", adminPath("/organizations/" + encode(organizationId()) + "/invitations/"
                + encode(invitation.providerInvitationId())), null, null, 204);
    }

    public boolean isAcceptedMember(MemberInvitation invitation, String subject, String verifiedEmail) {
        if (!invitation.invitedEmail().equalsIgnoreCase(verifiedEmail)) {
            return false;
        }
        JsonNode member = json(request("GET", adminPath("/organizations/" + encode(organizationId())
                + "/members/" + encode(subject)), null, null, 200));
        String memberEmail = member.path("email").asText("");
        return subject.equals(member.path("id").asText()) && invitation.invitedEmail().equalsIgnoreCase(memberEmail);
    }

    private ProviderInvitation newestInvitation(String organizationId, String email) {
        JsonNode invitations = json(request("GET", adminPath("/organizations/" + encode(organizationId)
                + "/invitations?email=" + encode(email)), null, null, 200));
        JsonNode newest = values(invitations)
                .filter(invitation -> email.equalsIgnoreCase(invitation.path("email").asText()))
                .max(Comparator.comparingLong(invitation -> invitation.path("sentDate").asLong(0)))
                .orElseThrow(() -> new IllegalStateException("Keycloak did not return the issued organization invitation"));
        String id = newest.path("id").asText("");
        if (id.isBlank()) {
            throw new IllegalStateException("Keycloak returned an invitation without an id");
        }
        return new ProviderInvitation(id);
    }

    private String organizationId() {
        JsonNode organizations = json(request("GET", adminPath("/organizations?search="
                + encode(properties.keycloak().organizationAlias()) + "&exact=true"), null, null, 200));
        return values(organizations)
                .filter(org -> properties.keycloak().organizationAlias().equals(org.path("alias").asText()))
                .map(org -> org.path("id").asText())
                .filter(id -> !id.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Configured Keycloak organization is unavailable"));
    }

    private String request(String method, String path, String body, String contentType, int expectedStatus) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                .timeout(properties.keycloak().timeout())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken());
        if (contentType != null) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != expectedStatus) {
                throw new IllegalStateException("Keycloak invitation operation failed with sanitized status " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("Keycloak invitation provider is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak invitation provider request was interrupted", exception);
        }
    }

    private String accessToken() {
        String body = form("grant_type", "client_credentials", "client_id", properties.keycloak().clientId(),
                "client_secret", properties.keycloak().clientSecret());
        HttpRequest request = HttpRequest.newBuilder(resolve("/realms/" + encode(properties.keycloak().realm())
                        + "/protocol/openid-connect/token"))
                .timeout(properties.keycloak().timeout())
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Keycloak service-account authentication failed with sanitized status " + response.statusCode());
            }
            String token = json(response.body()).path("access_token").asText("");
            if (token.isBlank()) {
                throw new IllegalStateException("Keycloak service-account response did not contain an access token");
            }
            return token;
        } catch (IOException exception) {
            throw new IllegalStateException("Keycloak service-account authentication is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak service-account authentication was interrupted", exception);
        }
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Keycloak returned an invalid response", exception);
        }
    }

    private URI resolve(String path) {
        return properties.keycloak().baseUrl().resolve(path);
    }

    private String adminPath(String suffix) {
        return "/admin/realms/" + encode(properties.keycloak().realm()) + suffix;
    }

    private String form(String... values) {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < values.length; index += 2) {
            if (body.length() > 0) body.append('&');
            body.append(encode(values[index])).append('=').append(encode(values[index + 1]));
        }
        return body.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String[] splitName(String displayName) {
        if (displayName == null || displayName.isBlank()) return new String[] {"", ""};
        String[] names = displayName.trim().split("\\s+", 2);
        return new String[] {names[0], names.length > 1 ? names[1] : ""};
    }

    private Stream<JsonNode> values(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }

    private void requireProviderId(MemberInvitation invitation) {
        if (invitation.providerInvitationId() == null || invitation.providerInvitationId().isBlank()) {
            throw new IllegalStateException("Invitation has no provider reference");
        }
    }

    public record ProviderInvitation(String providerInvitationId) {}
}
