package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Minimal member names from the existing identity and product-profile authorities. */
@Service
public final class MemberDisplayNameQuery implements com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery {
    private final KeycloakIdentityAdminClient identities;
    private final ProductProfileOverrideRepository profiles;
    private final OrganizationIdentityContextResolver contexts;
    private final String trustedIssuer;

    public MemberDisplayNameQuery(KeycloakIdentityAdminClient identities,
            ProductProfileOverrideRepository profiles, OrganizationIdentityContextResolver contexts,
            OAuth2ResourceServerProperties properties) {
        this.identities = identities;
        this.profiles = profiles;
        this.contexts = contexts;
        this.trustedIssuer = properties.getJwt().getIssuerUri();
    }

    @Override
    public void requireCurrentMember(Jwt jwt, String identityIssuer, String subject) {
        var caller = contexts.resolve(jwt);
        if (!contexts.matchesConfiguredTenant(jwt) || trustedIssuer == null
                || !trustedIssuer.equals(caller.issuer()) || !caller.issuer().equals(identityIssuer)
                || subject == null || subject.isBlank()) {
            throw new Denied();
        }
        try {
            var member = identities.requireMember(identities.configuredOrganizationId(), subject);
            if (member == null) throw new Unavailable();
            if (!subject.equals(member.subject()) || !member.enabled() || member.roles().size() != 1
                    || !Set.of("owner", "admin", "member", "guest").contains(member.roles().getFirst())) {
                throw new Denied();
            }
        } catch (KeycloakIdentityAdminClient.KeycloakAdminException error) {
            if (error.status() == 404) throw new Denied();
            throw new Unavailable();
        } catch (Denied | Unavailable error) {
            throw error;
        } catch (RuntimeException error) {
            throw new Unavailable();
        }
    }

    /** Call only after current domain admission; never returns email, roles or provider metadata. */
    public Map<String, String> displayNames(Jwt jwt, Collection<String> admittedSubjects) {
        var caller = contexts.resolve(jwt);
        if (!contexts.matchesConfiguredTenant(jwt) || trustedIssuer == null
                || !trustedIssuer.equals(caller.issuer()) || admittedSubjects.size() > 1000) {
            throw new IllegalStateException("Member directory identity authority is unavailable");
        }
        Set<String> subjects = Set.copyOf(admittedSubjects);
        if (subjects.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (var member : identities.members(identities.configuredOrganizationId())) {
            if (!subjects.contains(member.subject()) || !member.enabled() || member.roles().size() != 1) continue;
            var profile = profiles.findByPrimaryIdentityKey(
                    IdentityReferences.primaryIdentityKey(caller.issuer(), member.subject()));
            String name = profile != null && profile.displayName() != null && !profile.displayName().isBlank()
                    ? profile.displayName() : member.displayName();
            if (name != null && !name.isBlank()) result.put(member.subject(), name);
        }
        return Map.copyOf(result);
    }
}
