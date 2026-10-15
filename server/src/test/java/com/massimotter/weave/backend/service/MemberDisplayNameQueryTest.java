package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderMember;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MemberDisplayNameQueryTest {
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private final KeycloakIdentityAdminClient identities = mock(KeycloakIdentityAdminClient.class);
    private final ProductProfileOverrideRepository profiles = mock(ProductProfileOverrideRepository.class);

    private MemberDisplayNameQuery query() {
        var properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER);
        return new MemberDisplayNameQuery(identities, profiles,
                OrganizationIdentityContextResolver.defaults(), properties);
    }

    @Test
    void returnsEditedProfileThenCanonicalProviderNameOnlyForAdmittedEnabledMembers() {
        when(identities.configuredOrganizationId()).thenReturn("provider-org");
        when(identities.members("provider-org")).thenReturn(List.of(
                member("edited", "Original name", true), member("fallback", "Weave E2E Member", true),
                member("hidden", "Hidden name", true), member("disabled", "Disabled name", false)));
        when(profiles.findByPrimaryIdentityKey(IdentityReferences.primaryIdentityKey(ISSUER, "edited")))
                .thenReturn(new ProductProfileOverride("Edited human name", null, null, null, Map.of(), "workspace"));

        assertThat(query().displayNames(jwt(ISSUER, "tenant-default"), List.of("edited", "fallback", "disabled")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("edited", "Edited human name", "fallback", "Weave E2E Member"));
        verify(profiles, never()).findByPrimaryIdentityKey(IdentityReferences.primaryIdentityKey(ISSUER, "hidden"));
        verify(profiles, never()).findByPrimaryIdentityKey(IdentityReferences.primaryIdentityKey(ISSUER, "disabled"));
    }

    @Test
    void foreignOrganizationOrIssuerCannotReachIdentityOrProfileAuthority() {
        for (Jwt jwt : List.of(jwt(ISSUER, "other-org"), jwt("https://foreign.invalid/realms/weave", "tenant-default"))) {
            assertThatThrownBy(() -> query().displayNames(jwt, List.of("peer")))
                    .isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(identities, profiles);
    }

    @Test
    void emptyAdmissionDoesNotFetchProviderInventory() {
        assertThat(query().displayNames(jwt(ISSUER, "tenant-default"), List.of())).isEmpty();
        verifyNoInteractions(identities, profiles);
    }

    @Test
    void currentAdmissionRequiresLiveEnabledUnambiguousMemberWithoutUsingNamesOrProfiles() {
        when(identities.configuredOrganizationId()).thenReturn("provider-org");
        when(identities.requireMember("provider-org", "peer")).thenReturn(member("peer", "", true));
        query().requireCurrentMember(jwt(ISSUER, "tenant-default"), ISSUER, "peer");
        verifyNoInteractions(profiles);
        for (ProviderMember invalid : List.of(member("peer", "Name", false),
                member("other", "Name", true),
                new ProviderMember("peer", "", "Name", List.of("member", "admin"), List.of(), true))) {
            when(identities.requireMember("provider-org", "peer")).thenReturn(invalid);
            assertThatThrownBy(() -> query().requireCurrentMember(jwt(ISSUER, "tenant-default"), ISSUER, "peer"))
                    .isInstanceOf(com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery.Denied.class);
        }
    }

    @Test
    void offboardedAndUnavailableMembershipFailClosedWithTypedNonProviderErrors() {
        when(identities.configuredOrganizationId()).thenReturn("provider-org");
        for (int status : List.of(404, 503)) {
            doThrow(new KeycloakIdentityAdminClient.KeycloakAdminException(status, "private provider detail"))
                    .when(identities).requireMember("provider-org", "peer");
            assertThatThrownBy(() -> query().requireCurrentMember(jwt(ISSUER, "tenant-default"), ISSUER, "peer"))
                    .isInstanceOf(status == 404
                            ? com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery.Denied.class
                            : com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery.Unavailable.class)
                    .hasMessageNotContaining("private");
        }
        verifyNoInteractions(profiles);
    }

    @Test
    void currentAdmissionRejectsForeignCallerOrRecipientBeforeProviderAccess() {
        for (Jwt caller : List.of(jwt(ISSUER, "other-org"), jwt("https://foreign.invalid", "tenant-default"))) {
            assertThatThrownBy(() -> query().requireCurrentMember(caller, ISSUER, "peer"))
                    .isInstanceOf(com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery.Denied.class);
        }
        assertThatThrownBy(() -> query().requireCurrentMember(jwt(ISSUER, "tenant-default"), "https://foreign.invalid", "peer"))
                .isInstanceOf(com.massimotter.weave.backend.identity.CurrentOrganizationMemberQuery.Denied.class);
        verifyNoInteractions(identities, profiles);
    }

    private static ProviderMember member(String subject, String name, boolean enabled) {
        return new ProviderMember(subject, "not-projected@example.invalid", name, List.of("member"), List.of(), enabled);
    }

    private static Jwt jwt(String issuer, String organization) {
        return Jwt.withTokenValue("test-token").header("alg", "none").subject("caller")
                .claim("iss", issuer).claim("weave_tenant_id", organization).build();
    }
}
