package com.massimotter.weave.backend.agentruntime.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthorityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementDeniedException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectoryException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonNotFoundException;
import com.massimotter.weave.backend.identity.IdentityReferences;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only Keycloak 26.7 Admin REST adapter for current per-person Weaver entitlement. */
public final class KeycloakRuntimeEntitlementAuthority implements RuntimeEntitlementAuthority, RuntimePersonDirectory {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_GROUPS = 10_000;
    private static final int MAX_ORGANIZATION_MEMBERS = 10_000;
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final String SOURCE_PROVIDER = "keycloak";

    private final Settings settings;
    private final KeycloakAdminAccessTokenProvider accessTokens;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final Clock clock;

    public KeycloakRuntimeEntitlementAuthority(
            Settings settings,
            KeycloakAdminAccessTokenProvider accessTokens,
            ObjectMapper mapper) {
        this(settings, accessTokens, mapper,
                HttpClient.newBuilder().connectTimeout(settings.timeout()).build(), Clock.systemUTC());
    }

    KeycloakRuntimeEntitlementAuthority(
            Settings settings,
            KeycloakAdminAccessTokenProvider accessTokens,
            ObjectMapper mapper,
            HttpClient httpClient,
            Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.accessTokens = Objects.requireNonNull(accessTokens, "accessTokens");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RuntimeEntitlementObservation observe(ObserveEntitlementCommand command) {
        Objects.requireNonNull(command, "command");
        if (!settings.enabled()) {
            throw new RuntimeEntitlementDeniedException("The governed Weaver runtime is disabled by policy");
        }
        if (!settings.issuer().toString().equals(command.memberBinding().issuer())) {
            throw new RuntimeEntitlementDeniedException("The member identity is not bound to the configured IDM");
        }
        if (!settings.organizationRef().equals(command.organizationRef())) {
            throw new RuntimeEntitlementDeniedException("The member identity is not bound to the configured organization");
        }

        JsonNode organizationMember = get(
                "/organizations/" + path(settings.organizationId()) + "/members/"
                        + path(command.memberBinding().subject()),
                Set.of(200, 404));
        if (organizationMember == null
                || !command.memberBinding().subject().equals(organizationMember.path("id").asText())) {
            throw new RuntimeEntitlementDeniedException(
                    "The authoritative identity is not a current organization member");
        }

        JsonNode user = get("/users/" + path(command.memberBinding().subject()), Set.of(200, 404));
        if (user == null
                || !command.memberBinding().subject().equals(user.path("id").asText())
                || !user.path("enabled").asBoolean(false)) {
            throw new RuntimeEntitlementDeniedException("The authoritative member is absent or disabled");
        }

        List<Group> eligible = groups(command.memberBinding().subject()).stream()
                .filter(this::isEligible)
                .sorted(Comparator.comparing(Group::path).thenComparing(Group::id))
                .toList();
        if (eligible.isEmpty()) {
            throw new RuntimeEntitlementDeniedException("The member has no current Weaver entitlement group");
        }

        StringBuilder source = new StringBuilder("weave.agent-runtime.keycloak-groups/v1");
        for (Group group : eligible) {
            append(source, group.id());
            append(source, group.path());
        }
        String sourceGroupRef = RuntimeWorkloadOwnership.fingerprint(source.toString());
        StringBuilder capability = new StringBuilder("weave.agent-runtime.capability-policy/v1");
        append(capability, sourceGroupRef);
        settings.allowedCapabilities().forEach(value -> append(capability, value));
        String capabilityRevision = RuntimeWorkloadOwnership.fingerprint(capability.toString());
        Instant observedAt = clock.instant();
        return new RuntimeEntitlementObservation(
                command.organizationRef(), command.personRef(), command.memberBinding(), SOURCE_PROVIDER,
                sourceGroupRef, capabilityRevision, observedAt, observedAt.plus(settings.observationTtl()));
    }

    @Override
    public ResolvedRuntimePerson resolve(ResolveRuntimePersonCommand command) {
        Objects.requireNonNull(command, "command");
        if (!settings.organizationRef().equals(command.organizationRef())
                || !command.personRef().matches("acct_[a-f0-9]{32}")) {
            throw new RuntimePersonNotFoundException("The requested runtime person does not exist");
        }
        try {
            String subject = null;
            for (JsonNode member : organizationMembers()) {
                String candidate = text(member, "id");
                if (IdentityReferences.accountId(settings.issuer().toString(), candidate)
                        .equals(command.personRef())) {
                    if (subject != null && !subject.equals(candidate)) {
                        throw new RuntimePersonDirectoryException(
                                "The runtime person reference resolves ambiguously");
                    }
                    subject = candidate;
                }
            }
            if (subject == null) {
                throw new RuntimePersonNotFoundException("The requested runtime person does not exist");
            }
            JsonNode member = get(
                    "/organizations/" + path(settings.organizationId()) + "/members/" + path(subject),
                    Set.of(200, 404));
            JsonNode user = get("/users/" + path(subject), Set.of(200, 404));
            if (member == null || user == null
                    || !subject.equals(member.path("id").asText())
                    || !subject.equals(user.path("id").asText())
                    || !user.path("enabled").asBoolean(false)) {
                throw new RuntimePersonNotFoundException("The requested runtime person does not exist");
            }
            return new ResolvedRuntimePerson(
                    command.organizationRef(),
                    command.personRef(),
                    new RuntimeMemberBinding(settings.issuer().toString(), subject));
        } catch (RuntimePersonNotFoundException | RuntimePersonDirectoryException failure) {
            throw failure;
        } catch (RuntimeEntitlementAuthorityException unavailable) {
            throw new RuntimePersonDirectoryException(
                    "The authoritative runtime person directory is unavailable", unavailable);
        }
    }

    private List<JsonNode> organizationMembers() {
        List<JsonNode> members = new ArrayList<>();
        for (int first = 0; first < MAX_ORGANIZATION_MEMBERS; first += PAGE_SIZE) {
            JsonNode page = get(
                    "/organizations/" + path(settings.organizationId())
                            + "/members?first=" + first + "&max=" + PAGE_SIZE,
                    Set.of(200));
            if (page == null || !page.isArray()) {
                throw new RuntimeEntitlementAuthorityException(
                        "Keycloak returned an invalid organization member projection");
            }
            page.forEach(members::add);
            if (page.size() < PAGE_SIZE) {
                return List.copyOf(members);
            }
        }
        throw new RuntimeEntitlementAuthorityException(
                "The Keycloak organization member projection exceeds its safe bound");
    }

    private List<Group> groups(String subject) {
        List<Group> groups = new ArrayList<>();
        for (int first = 0; first < MAX_GROUPS; first += PAGE_SIZE) {
            JsonNode page = get("/users/" + path(subject) + "/groups?briefRepresentation=true&first="
                    + first + "&max=" + PAGE_SIZE, Set.of(200));
            if (page == null || !page.isArray()) {
                throw new RuntimeEntitlementAuthorityException(
                        "Keycloak returned an invalid entitlement group projection");
            }
            for (JsonNode item : page) {
                String id = text(item, "id");
                String name = text(item, "name");
                String groupPath = item.path("path").asText("");
                if (groupPath.isBlank()) {
                    groupPath = "/" + name;
                }
                groups.add(new Group(id, name, groupPath));
            }
            if (page.size() < PAGE_SIZE) {
                return List.copyOf(groups);
            }
        }
        throw new RuntimeEntitlementAuthorityException(
                "The Keycloak entitlement group projection exceeds its safe bound");
    }

    private boolean isEligible(Group group) {
        for (String configured : settings.eligibleGroups()) {
            String configuredPath = configured.startsWith("/") ? configured : "/" + configured;
            if (group.path().equals(configuredPath)
                    || group.path().startsWith(configuredPath + "/")
                    || (!configured.startsWith("/") && group.name().equals(configured))) {
                return true;
            }
        }
        return false;
    }

    private JsonNode get(String suffix, Set<Integer> acceptedStatuses) {
        String token = accessTokens.accessToken();
        Response first = send(suffix, token);
        if (first.status() == 401) {
            accessTokens.invalidate(token);
            first = send(suffix, accessTokens.accessToken());
        }
        if (!acceptedStatuses.contains(first.status())) {
            throw new RuntimeEntitlementAuthorityException(
                    "Keycloak entitlement lookup failed with sanitized status " + first.status());
        }
        if (first.status() == 404) {
            return null;
        }
        try {
            return mapper.readTree(first.body());
        } catch (IOException invalid) {
            throw new RuntimeEntitlementAuthorityException(
                    "Keycloak returned an invalid entitlement response", invalid);
        }
    }

    private Response send(String suffix, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeEntitlementAuthorityException("The Keycloak administration token is unavailable");
        }
        HttpRequest request = HttpRequest.newBuilder(settings.adminBaseUrl().resolve(adminPath(suffix)))
                .timeout(settings.timeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new RuntimeEntitlementAuthorityException(
                            "Keycloak entitlement response exceeds its safe bound");
                }
                return new Response(response.statusCode(), bytes);
            }
        } catch (IOException unavailable) {
            throw new RuntimeEntitlementAuthorityException("Keycloak entitlement lookup is unavailable", unavailable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeEntitlementAuthorityException("Keycloak entitlement lookup was interrupted", interrupted);
        }
    }

    private String adminPath(String suffix) {
        return "/admin/realms/" + path(settings.realm()) + suffix;
    }

    private static String path(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new RuntimeEntitlementAuthorityException(
                    "Keycloak returned an incomplete entitlement projection");
        }
        return value;
    }

    private static void append(StringBuilder target, String value) {
        target.append('\u0000').append(value.length()).append(':').append(value);
    }

    public record Settings(
            boolean enabled,
            URI adminBaseUrl,
            URI issuer,
            String organizationRef,
            String organizationId,
            String realm,
            Duration timeout,
            Duration observationTtl,
            List<String> eligibleGroups,
            List<String> allowedCapabilities) {
        public Settings {
            requireHttp(adminBaseUrl, "adminBaseUrl", false);
            requireHttp(issuer, "issuer", true);
            if (organizationRef == null || organizationRef.isBlank() || organizationRef.length() > 255) {
                throw new IllegalArgumentException("organizationRef is required");
            }
            if (organizationId == null || organizationId.isBlank()
                    || organizationId.length() > 255 || organizationId.contains("/")) {
                throw new IllegalArgumentException("organizationId is required");
            }
            if (realm == null || realm.isBlank() || realm.contains("/")) {
                throw new IllegalArgumentException("realm is required");
            }
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            if (observationTtl == null
                    || observationTtl.compareTo(Duration.ofSeconds(30)) < 0
                    || observationTtl.compareTo(Duration.ofMinutes(15)) > 0) {
                throw new IllegalArgumentException("observationTtl must be between 30 seconds and 15 minutes");
            }
            eligibleGroups = normalized(eligibleGroups, "eligibleGroups");
            allowedCapabilities = normalized(allowedCapabilities, "allowedCapabilities");
        }

        private static List<String> normalized(List<String> values, String field) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException(field + " must not be empty");
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String value : values) {
                if (value == null || value.isBlank() || value.length() > 255) {
                    throw new IllegalArgumentException(field + " contains an invalid value");
                }
                normalized.add(value.trim());
            }
            if (normalized.size() != values.size() || new HashSet<>(normalized).size() != normalized.size()) {
                throw new IllegalArgumentException(field + " must contain unique values");
            }
            return List.copyOf(normalized);
        }

        private static void requireHttp(URI uri, String field, boolean httpsOnly) {
            if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || (!httpsOnly && "http".equalsIgnoreCase(uri.getScheme())))) {
                throw new IllegalArgumentException(field + " must be an absolute "
                        + (httpsOnly ? "HTTPS" : "HTTP(S)") + " URI");
            }
        }
    }

    private record Group(String id, String name, String path) {}

    private record Response(int status, byte[] body) {}
}
