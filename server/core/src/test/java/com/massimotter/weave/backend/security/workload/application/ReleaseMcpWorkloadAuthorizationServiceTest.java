package com.massimotter.weave.backend.security.workload.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.massimotter.weave.backend.security.workload.domain.*;
import com.massimotter.weave.backend.security.workload.port.*;
import com.massimotter.weave.backend.security.workload.jwt.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.identity.IdentityReferences;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ReleaseMcpWorkloadAuthorizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String API_RESOURCE = "https://api.weave.test/api";
    private static final String SUBJECT = "service-account-cell-subject";
    private static final String CLIENT = "weaver-cell-test";
    private static final String PROFILE_ID = "rp_test";
    private static final String PROFILE_HASH = "sha256:" + "2".repeat(64);
    private static final String ENTITLEMENT_REVISION = "sha256:" + "3".repeat(64);
    private static final String SOURCE_GROUP = "sha256:" + "4".repeat(64);
    private static final String CAPABILITY_REVISION = "sha256:" + "5".repeat(64);
    private static final MemberIdentityBinding MEMBER = new MemberIdentityBinding(ISSUER, "member-subject");
    private static final String RELEASE_PERSON = IdentityReferences.accountId(ISSUER, "member-subject");

    private final WorkloadEntitlementAuthority entitlementAuthority = mock(WorkloadEntitlementAuthority.class);
    private final McpExchangedTokenPolicy tokenPolicy = new McpExchangedTokenPolicy(API_RESOURCE, "weave-mcp-server");

    @Test
    void releaseBindingAuthorizesWithoutAnyRuntimeCellOrProfile() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(releaseBinding(true)));
        when(entitlementAuthority.observe(any())).thenReturn(releaseObservation());
        var release = new ReleaseMcpWorkloadAuthorizationService(
                bindings, entitlementAuthority, Set.of("calendar.read", "calendar.write", "files.read"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var principal = release.authorize(token());

        assertThat(principal.workloadClientId()).isEqualTo("weaver-cell-release-test");
        assertThat(principal.memberBinding()).isEqualTo(MEMBER);
        assertThat(principal.visibleToolClasses()).containsExactly("calendar.read");
        assertThat(principal.bindingAuditKey()).isEqualTo("workloadBindingRef");
        assertThat(principal.bindingRef()).isEqualTo("mcp-binding:test");
        assertThat(principal.policyRevision()).isEqualTo("mcp-binding:" + PROFILE_HASH);
        assertThat(principal.memberEntitlementRevision()).isEqualTo("mcp-entitlement:" + CAPABILITY_REVISION);
        assertThat(principal.authorizationExpiresAt()).isEqualTo(NOW.plusSeconds(30));
        verify(entitlementAuthority).observe(any());
    }

    @Test
    void releaseBindingAcceptsObservationStampedDuringAuthoritativeLookup() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(releaseBinding(true)));
        WorkloadEntitlementObservation current = releaseObservation();
        when(entitlementAuthority.observe(any())).thenReturn(new WorkloadEntitlementObservation(
                current.organizationRef(), current.personRef(), current.memberBinding(),
                current.contextPrincipalClaim(), current.sourceProvider(), current.sourceGroupRef(),
                current.capabilityRevision(), NOW.plusMillis(5), NOW.plusSeconds(60)));
        Clock progressingClock = mock(Clock.class);
        when(progressingClock.instant()).thenReturn(NOW, NOW.plusMillis(10));
        var release = new ReleaseMcpWorkloadAuthorizationService(
                bindings, entitlementAuthority, Set.of("calendar.read", "calendar.write", "files.read"),
                progressingClock);

        assertThat(release.authorize(token()).visibleToolClasses()).containsExactly("calendar.read");
    }

    @Test
    void releaseBindingRevocationAndScopeChangesFailClosedWithoutCellFallback() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        var release = new ReleaseMcpWorkloadAuthorizationService(
                bindings, entitlementAuthority, Set.of("calendar.read", "calendar.write", "files.read"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(releaseBinding(false)));
        assertThatThrownBy(() -> release.authorize(token()))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo("identity-binding"));

        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> release.authorize(token()))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo("identity-binding"));

        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(releaseBinding(true)));
        assertThatThrownBy(() -> release.authorize(token("files.read")))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo("identity-binding"));
    }

    @Test
    void releaseCalendarWriteStillRequiresCurrentAdministrativeMemberRole() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        when(entitlementAuthority.observe(any())).thenReturn(releaseObservation());
        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(new ReleaseMcpBinding(
                "mcp-binding:test", ISSUER, SUBJECT, "weaver-cell-release-test",
                "org:test", RELEASE_PERSON, MEMBER, PROFILE_HASH,
                NOW.plusSeconds(60), Set.of("calendar.write"), true)));
        var release = new ReleaseMcpWorkloadAuthorizationService(
                bindings, entitlementAuthority, Set.of("calendar.read", "calendar.write", "files.read"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> release.authorize(token("calendar.write")))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo("tool-scope"));
        when(entitlementAuthority.currentWeaveRoles(any())).thenReturn(Set.of("admin"));
        assertThat(release.authorize(token("calendar.write")).visibleToolClasses())
                .containsExactly("calendar.write");
    }

    @Test
    void releaseBindingCannotBroadenTheServerCapabilityCeiling() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(new ReleaseMcpBinding(
                "mcp-binding:test", ISSUER, SUBJECT, "weaver-mcp-member-test",
                "org:test", RELEASE_PERSON, MEMBER, PROFILE_HASH,
                NOW.plusSeconds(60), Set.of("calendar.write"), true)));
        var release = new ReleaseMcpWorkloadAuthorizationService(
                bindings, entitlementAuthority, Set.of("files.read", "calendar.read"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> release.authorize(token("calendar.write")))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo("identity-binding"));
        verify(entitlementAuthority, never()).observe(any());
    }

    @Test
    void releaseBindingAuthorityOutageDoesNotRevealPrivateCause() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        when(bindings.findByWorkload(ISSUER, SUBJECT))
                .thenThrow(new IllegalStateException("private binding path"));
        var release = new ReleaseMcpWorkloadAuthorizationService(
                bindings, entitlementAuthority, Set.of("calendar.read", "calendar.write", "files.read"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> release.authorize(token()))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.authorityUnavailable()).isTrue())
                .hasMessageNotContaining("private binding path");
    }

    @Test
    void releaseBindingRejectsCrossOrganizationObservationAndCurrentAuthorityLoss() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(releaseBinding(true)));
        var release = new ReleaseMcpWorkloadAuthorizationService(
                bindings, entitlementAuthority, Set.of("calendar.read", "calendar.write", "files.read"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        WorkloadEntitlementObservation current = releaseObservation();
        when(entitlementAuthority.observe(any())).thenReturn(new WorkloadEntitlementObservation(
                "org:other", current.personRef(), current.memberBinding(), current.contextPrincipalClaim(),
                current.sourceProvider(), current.sourceGroupRef(), current.capabilityRevision(),
                current.observedAt(), current.expiresAt()));
        assertThatThrownBy(() -> release.authorize(token()))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo("entitlement-mismatch"));

        when(entitlementAuthority.observe(any()))
                .thenThrow(new WorkloadEntitlementAuthorityException("private Keycloak failure"));
        assertThatThrownBy(() -> release.authorize(token()))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.authorityUnavailable()).isTrue())
                .hasMessageNotContaining("private Keycloak failure");
    }

    @Test
    void rejectsExpiredOrOverlongTokensBeforeLookingUpProtectedBindings() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        var service = new ReleaseMcpWorkloadAuthorizationService(bindings, entitlementAuthority,
                Set.of("calendar.read"), Clock.fixed(NOW, ZoneOffset.UTC));
        for (ExchangedWorkloadToken rejected : List.of(
                new ExchangedWorkloadToken(ISSUER, SUBJECT, "weave-mcp-server", Set.of("calendar.read"),
                        NOW.minusSeconds(30), NOW, "expired"),
                new ExchangedWorkloadToken(ISSUER, SUBJECT, "weave-mcp-server", Set.of("calendar.read"),
                        NOW, NOW.plusSeconds(61), "overlong"))) {
            assertThatThrownBy(() -> service.authorize(rejected))
                    .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                            failure -> assertThat(failure.reasonCode()).isEqualTo("token-lifetime"));
        }
        verifyNoInteractions(bindings, entitlementAuthority);
    }

    @Test
    void rejectsAuthorityObservationsOutsideTheCurrentMemberAndTimeBoundary() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(releaseBinding(true)));
        var service = new ReleaseMcpWorkloadAuthorizationService(bindings, entitlementAuthority,
                Set.of("calendar.read"), Clock.fixed(NOW, ZoneOffset.UTC));
        for (WorkloadEntitlementObservation rejected : List.of(
                new WorkloadEntitlementObservation("org:test", RELEASE_PERSON,
                        new MemberIdentityBinding(ISSUER, "other-member"), "test-member", "keycloak",
                        SOURCE_GROUP, CAPABILITY_REVISION, NOW, NOW.plusSeconds(45)),
                new WorkloadEntitlementObservation("org:test", RELEASE_PERSON, MEMBER, "test-member", "keycloak",
                        SOURCE_GROUP, CAPABILITY_REVISION, NOW.plusSeconds(1), NOW.plusSeconds(45)),
                new WorkloadEntitlementObservation("org:test", RELEASE_PERSON, MEMBER, "test-member", "keycloak",
                        SOURCE_GROUP, CAPABILITY_REVISION, NOW.minusSeconds(45), NOW))) {
            when(entitlementAuthority.observe(any())).thenReturn(rejected);
            assertThatThrownBy(() -> service.authorize(token()))
                    .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                            failure -> assertThat(failure.reasonCode()).isEqualTo("entitlement-mismatch"));
        }
    }

    @Test
    void rechecksTokenExpiryAfterTheAuthoritativeLookupCompletes() {
        ReleaseMcpBindingRepository bindings = mock(ReleaseMcpBindingRepository.class);
        when(bindings.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(releaseBinding(true)));
        when(entitlementAuthority.observe(any())).thenReturn(releaseObservation());
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(31));
        var service = new ReleaseMcpWorkloadAuthorizationService(bindings, entitlementAuthority,
                Set.of("calendar.read"), clock);
        assertThatThrownBy(() -> service.authorize(token()))
                .isInstanceOfSatisfying(McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo("entitlement-mismatch"));
    }

    private static ReleaseMcpBinding releaseBinding(boolean active) {
        return new ReleaseMcpBinding(
                "mcp-binding:test", ISSUER, SUBJECT, "weaver-cell-release-test",
                "org:test", RELEASE_PERSON, MEMBER, PROFILE_HASH,
                NOW.plusSeconds(60), Set.of("calendar.read"), active);
    }

    private static WorkloadEntitlementObservation releaseObservation() {
        WorkloadEntitlementObservation current = observation();
        return new WorkloadEntitlementObservation(
                current.organizationRef(), RELEASE_PERSON, current.memberBinding(),
                current.contextPrincipalClaim(), current.sourceProvider(), current.sourceGroupRef(),
                current.capabilityRevision(), current.observedAt(), current.expiresAt());
    }

    private ExchangedWorkloadToken token() {
        return token("calendar.read");
    }

    private ExchangedWorkloadToken token(String scope) {
        return tokenPolicy.resolve(jwt(scope));
    }

    private static Jwt jwt(String scope) {
        return Jwt.withTokenValue("exchanged-secret-token")
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .issuer(ISSUER)
                .subject(SUBJECT)
                .audience(List.of(API_RESOURCE))
                .claim("azp", "weave-mcp-server")
                .claim("scope", scope)
                .claim("realm_access", Map.of())
                .claim("resource_access", Map.of())
                .jti("exchange-jti")
                .issuedAt(NOW.minusSeconds(1))
                .expiresAt(NOW.plusSeconds(30))
                .build();
    }

    private static WorkloadEntitlementObservation observation() {
        return new WorkloadEntitlementObservation(
                "org:test",
                "person:test",
                MEMBER,
                "test-member",
                "keycloak",
                SOURCE_GROUP,
                CAPABILITY_REVISION,
                NOW,
                NOW.plusSeconds(45));
    }
}
