package com.massimotter.weave.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.security.port.MemberSessionStateStore;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.Jwt;

class MemberSessionRevocationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private final MemberSessionStateStore store = mock(MemberSessionStateStore.class);
    private final OrganizationIdentityContextResolver identities = OrganizationIdentityContextResolver.configured(
            new ContextAuthorizationProperties(null, null, null, null, null, null, null, null));

    @Test
    @Tag("context")
    void sharedSecurityContextStartsAndChecksSessionsWithoutChatClasses() {
        when(store.isSessionRevoked(any(), any())).thenReturn(true);
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("com.massimotter.weave.backend.matrix", "com.massimotter.weave.backend.chat"))
                .withUserConfiguration(MemberSessionRevocationService.class, MemberSessionCutoffService.class)
                .withBean("memberSessionStateStore", MemberSessionStateStore.class, () -> store)
                .withBean(OrganizationIdentityContextResolver.class, () -> identities)
                .run(context -> {
                    assertThat(context).hasSingleBean(MemberSessionRevocationService.class).hasSingleBean(MemberSessionCutoffService.class);
                    var service = context.getBean(MemberSessionRevocationService.class);
                    service.revoke(token("sid", "session", NOW.plusSeconds(60)));
                    assertThat(service.revoked(token("sid", "session", NOW.plusSeconds(60)))).isTrue();
                });
    }

    @Test
    void sessionIdentityAndMinimumRetentionMatchExistingStoredHashes() throws Exception {
        MemberSessionRevocationService service = new MemberSessionRevocationService(store, identities, Clock.fixed(NOW, ZoneOffset.UTC));
        Jwt jwt = token("sid", " session ", NOW.plusSeconds(60));
        service.revoke(jwt);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(store).revokeSession(hash.capture(), org.mockito.ArgumentMatchers.eq(NOW),
                org.mockito.ArgumentMatchers.eq(NOW.plusSeconds(30L * 86_400L)));
        var identity = identities.resolve(jwt);
        String expected = identity.organizationId() + "\u0000" + identity.issuer() + "\u0000" + identity.subject() + "\u0000session:session";
        assertThat(hash.getValue()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(expected.getBytes(StandardCharsets.UTF_8))));
        assertThat(hash.getValue()).hasSize(64).doesNotContain("subject", "session");
    }

    @Test
    void laterBearerExpiryIsRetainedAndPersistenceFailureCannotAdmitTheSession() {
        MemberSessionRevocationService service = new MemberSessionRevocationService(store, identities, Clock.fixed(NOW, ZoneOffset.UTC));
        Instant expires = NOW.plusSeconds(60L * 86_400L);
        service.revoke(token("session_state", "session", expires));
        verify(store).revokeSession(any(), org.mockito.ArgumentMatchers.eq(NOW), org.mockito.ArgumentMatchers.eq(expires));
        doThrow(new IllegalStateException("storage unavailable")).when(store).deleteExpiredSessions(NOW);
        assertThatThrownBy(() -> service.revoked(token("sid", "session", expires))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fallbackJtiIdentityRemainsScopedAndNoRawBearerIsPersisted() {
        MemberSessionRevocationService service = new MemberSessionRevocationService(store, identities, Clock.fixed(NOW, ZoneOffset.UTC));
        service.revoke(token("jti", "one", NOW.plusSeconds(60)));
        service.revoke(token("jti", "two", NOW.plusSeconds(60)));
        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(store, org.mockito.Mockito.times(2)).revokeSession(hashes.capture(), any(), any());
        assertThat(hashes.getAllValues()).doesNotHaveDuplicates().allSatisfy(hash -> assertThat(hash).hasSize(64));
    }

    private Jwt token(String claim, String value, Instant expires) {
        return Jwt.withTokenValue("never-persist-this-bearer").header("alg", "none").subject("subject")
                .issuer("https://auth.weave.test/realms/weave").issuedAt(NOW.minusSeconds(10)).expiresAt(expires)
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")).claim(claim, value).build();
    }
}
