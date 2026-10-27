package com.massimotter.weave.backend.agentruntime.provider.identity;

import com.massimotter.weave.backend.security.workload.domain.WorkloadReferences;

import com.massimotter.weave.backend.security.keycloak.port.KeycloakAdminAccessTokenProvider;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.security.workload.domain.WorkloadEntitlementObservation;
import com.massimotter.weave.backend.security.workload.domain.MemberIdentityBinding;
import com.massimotter.weave.backend.security.workload.port.WorkloadEntitlementAuthority;
import com.massimotter.weave.backend.security.workload.port.WorkloadEntitlementAuthorityException;
import com.massimotter.weave.backend.security.workload.port.WorkloadEntitlementDeniedException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.massimotter.weave.backend.security.workload.provider.KeycloakWorkloadEntitlementAuthority;

/** Future runtime person-directory projection over the shared Keycloak membership authority. */
public final class KeycloakRuntimeIdentityAuthority extends KeycloakWorkloadEntitlementAuthority
        implements RuntimePersonDirectory {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_ORGANIZATION_MEMBERS = 10_000;

    public KeycloakRuntimeIdentityAuthority(Settings settings, KeycloakAdminAccessTokenProvider tokens, ObjectMapper mapper) {
        super(settings, tokens, mapper);
    }

    KeycloakRuntimeIdentityAuthority(Settings settings, KeycloakAdminAccessTokenProvider tokens,
            ObjectMapper mapper, HttpClient client, Clock clock) {
        super(settings, tokens, mapper, client, clock);
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
                    "/organizations/" + path(organizationId()) + "/members/" + path(subject),
                    Set.of(200, 404));
            if (member == null
                    || !subject.equals(text(member, "id"))
                    || !member.path("enabled").asBoolean(false)) {
                throw new RuntimePersonNotFoundException("The requested runtime person does not exist");
            }
            return new ResolvedRuntimePerson(
                    command.organizationRef(),
                    command.personRef(),
                    new MemberIdentityBinding(settings.issuer().toString(), subject));
        } catch (RuntimePersonNotFoundException | RuntimePersonDirectoryException failure) {
            throw failure;
        } catch (WorkloadEntitlementAuthorityException unavailable) {
            throw new RuntimePersonDirectoryException(
                    "The authoritative runtime person directory is unavailable", unavailable);
        }
    }

    private List<JsonNode> organizationMembers() {
        List<JsonNode> members = new ArrayList<>();
        for (int first = 0; first < MAX_ORGANIZATION_MEMBERS; first += PAGE_SIZE) {
            JsonNode page = get(
                    "/organizations/" + path(organizationId())
                            + "/members?first=" + first + "&max=" + PAGE_SIZE,
                    Set.of(200));
            if (page == null || !page.isArray()) {
                throw new WorkloadEntitlementAuthorityException(
                        "Keycloak returned an invalid organization member projection");
            }
            page.forEach(members::add);
            if (page.size() < PAGE_SIZE) {
                return List.copyOf(members);
            }
        }
        throw new WorkloadEntitlementAuthorityException(
                "The Keycloak organization member projection exceeds its safe bound");
    }

}
