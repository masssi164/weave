package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.model.identity.IdentitySessionReconcileResponse;
import com.massimotter.weave.backend.service.MemberInvitationService;
import java.util.List;
import java.util.Map;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.security.oauth2.jwt.Jwt;

class IdentitySessionControllerTest {

    @Test
    void reportsAccessUpdateAndPreventsCaching() {
        MemberInvitationService invitations = mock(MemberInvitationService.class);
        Jwt jwt = memberJwt();
        when(invitations.reconcileAuthenticated(jwt)).thenReturn(true);

        var response = new IdentitySessionController(invitations).reconcile(jwt);

        assertThat(response.getBody())
                .isEqualTo(IdentitySessionReconcileResponse.accessUpdated());
        assertThat(response.getHeaders().getCacheControl())
                .isEqualTo(CacheControl.noStore().getHeaderValue());
    }

    @Test
    void reportsUnchangedWithoutInventingAccess() {
        MemberInvitationService invitations = mock(MemberInvitationService.class);
        Jwt jwt = memberJwt();
        when(invitations.reconcileAuthenticated(jwt)).thenReturn(false);

        var response = new IdentitySessionController(invitations).reconcile(jwt);

        assertThat(response.getBody())
                .isEqualTo(IdentitySessionReconcileResponse.unchanged());
    }

    @Test
    void recordsOnlyBoundedTimingAndPreservesTheOriginalFailure() {
        MemberInvitationService invitations = mock(MemberInvitationService.class);
        Jwt jwt = memberJwt();
        IllegalStateException failure = new IllegalStateException("private token and provider payload");
        Logger logger = (Logger) LoggerFactory.getLogger(IdentitySessionController.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            when(invitations.reconcileAuthenticated(jwt)).thenReturn(false);
            new IdentitySessionController(invitations).reconcile(jwt);
            when(invitations.reconcileAuthenticated(jwt)).thenThrow(failure);
            try {
                new IdentitySessionController(invitations).reconcile(jwt);
                throw new AssertionError("The original failure must propagate");
            } catch (IllegalStateException actual) {
                assertThat(actual).isSameAs(failure);
            }
            assertThat(captured.list).hasSize(4);
            assertThat(captured.list.get(0).getFormattedMessage()).isEqualTo(
                    "WEAVE_IDENTITY_SESSION_DIAGNOSTIC operation=reconcile phase=started supportSafe=true");
            assertThat(captured.list.get(1).getFormattedMessage()).matches(
                    "WEAVE_IDENTITY_SESSION_DIAGNOSTIC operation=reconcile phase=ended outcome=completed elapsedMillis=[0-9]{1,6} supportSafe=true");
            assertThat(captured.list.get(3).getFormattedMessage()).matches(
                    "WEAVE_IDENTITY_SESSION_DIAGNOSTIC operation=reconcile phase=ended outcome=failed elapsedMillis=[0-9]{1,6} supportSafe=true");
            for (var event : captured.list) {
                assertThat(event.getFormattedMessage()).doesNotContain(
                        "member-token", "member-1", "member@example.invalid", "private", "https://");
                assertThat(event.getThrowableProxy()).isNull();
            }
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    private Jwt memberJwt() {
        return Jwt.withTokenValue("member-token")
                .header("alg", "none")
                .subject("member-1")
                .issuer("https://auth.example.invalid/realms/weave")
                .claim("weave_tenant", "weave-dogfood")
                .claim("email", "member@example.invalid")
                .claim("email_verified", true)
                .claim(
                        "organization",
                        HumanJwtTestSupport
                                .organizationWithRoles(List.of()))
                .build();
    }
}
