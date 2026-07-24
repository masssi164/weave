package com.massimotter.weave.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class KeycloakMembershipEventServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");
    private static final String SECRET = "unit-test-secret";
    private final MemberInvitationService invitations = mock(MemberInvitationService.class);
    private final IdentityInvitationProperties properties = properties();
    private final KeycloakMembershipEventService service = new KeycloakMembershipEventService(
            invitations, properties, tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsSignedMembershipEventWithoutRawEmail() throws Exception {
        String hash = "a".repeat(64);
        byte[] body = ("{\"schemaVersion\":1,\"eventId\":\"evt-1\",\"occurredAt\":\"" + NOW
                + "\",\"realmId\":\"realm-1\",\"organizationId\":\"org-1\","
                + "\"userSubject\":\"user-1\",\"eventType\":\"organization_membership_added\","
                + "\"invitedEmailHash\":\"" + hash + "\"}").getBytes(StandardCharsets.UTF_8);

        when(invitations.recordMembershipEventOnce("evt-1", NOW)).thenReturn(true);
        service.accept(body, "evt-1", NOW.toString(), signature(NOW.toString(), body));

        verify(invitations).applyMembershipEvent("org-1", "user-1", hash);
    }

    @Test
    void ignoresReplayAfterSignatureValidation() throws Exception {
        String hash = "a".repeat(64);
        byte[] body = ("{\"schemaVersion\":1,\"eventId\":\"evt-replayed\",\"occurredAt\":\"" + NOW
                + "\",\"realmId\":\"realm-1\",\"organizationId\":\"org-1\","
                + "\"userSubject\":\"user-1\",\"eventType\":\"organization_membership_added\","
                + "\"invitedEmailHash\":\"" + hash + "\"}").getBytes(StandardCharsets.UTF_8);
        when(invitations.recordMembershipEventOnce("evt-replayed", NOW)).thenReturn(false);

        service.accept(body, "evt-replayed", NOW.toString(), signature(NOW.toString(), body));

        verify(invitations, never()).applyMembershipEvent("org-1", "user-1", hash);
    }

    @Test
    void rejectsInvalidSignatureBeforeParsingEvent() {
        ApiErrorException error = assertThrows(ApiErrorException.class,
                () -> service.accept("{}".getBytes(StandardCharsets.UTF_8), "evt-1", NOW.toString(), "00"));
        assertEquals("keycloak-event-signature-invalid", error.code());
    }

    @Test
    void rejectsStaleSignedEvent() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String timestamp = NOW.minusSeconds(600).toString();
        ApiErrorException error = assertThrows(ApiErrorException.class,
                () -> service.accept(body, "evt-1", timestamp, signature(timestamp, body)));
        assertEquals("keycloak-event-stale", error.code());
    }

    private IdentityInvitationProperties properties() {
        IdentityInvitationProperties result = new IdentityInvitationProperties();
        result.setEventsHmacSecret(SECRET);
        return result;
    }

    private String signature(String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
