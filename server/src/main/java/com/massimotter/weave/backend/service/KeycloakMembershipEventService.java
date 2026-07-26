package com.massimotter.weave.backend.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.identity.KeycloakMembershipEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KeycloakMembershipEventService {
    private final MemberInvitationService invitations;
    private final IdentityInvitationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public KeycloakMembershipEventService(MemberInvitationService invitations, IdentityInvitationProperties properties,
            ObjectMapper objectMapper) {
        this(invitations, properties, objectMapper, Clock.systemUTC());
    }
    KeycloakMembershipEventService(MemberInvitationService invitations, IdentityInvitationProperties properties,
            ObjectMapper objectMapper, Clock clock) {
        this.invitations=invitations; this.properties=properties; this.objectMapper=objectMapper; this.clock=clock;
    }

    public void accept(byte[] body, String headerEventId, String timestamp, String signature) {
        if (properties.eventsHmacSecret().isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "keycloak-event-receiver-disabled");
        Instant sentAt;
        try { sentAt=Instant.parse(timestamp); } catch (RuntimeException e) { throw error(HttpStatus.UNAUTHORIZED, "keycloak-event-signature-invalid"); }
        Instant now=clock.instant();
        if (sentAt.isBefore(now.minus(properties.eventFreshness())) || sentAt.isAfter(now.plusSeconds(30)))
            throw error(HttpStatus.UNAUTHORIZED, "keycloak-event-stale");
        byte[] expected=hmac(timestamp.getBytes(StandardCharsets.UTF_8), body);
        byte[] supplied;
        try { supplied=HexFormat.of().parseHex(signature); } catch (RuntimeException e) { throw error(HttpStatus.UNAUTHORIZED, "keycloak-event-signature-invalid"); }
        if (!MessageDigest.isEqual(expected, supplied)) throw error(HttpStatus.UNAUTHORIZED, "keycloak-event-signature-invalid");
        KeycloakMembershipEvent event;
        try { event=objectMapper.readValue(body, KeycloakMembershipEvent.class); }
        catch (JacksonException e) { throw error(HttpStatus.BAD_REQUEST, "keycloak-event-invalid"); }
        if (event.schemaVersion()!=1 || !headerEventId.equals(event.eventId()) || event.occurredAt()==null
                || !"organization_membership_added".equals(event.eventType()))
            throw error(HttpStatus.BAD_REQUEST, "keycloak-event-invalid");
        if (!invitations.recordMembershipEventOnce(event.eventId(), event.occurredAt())) return;
        invitations.applyMembershipEvent(event.organizationId(), event.userSubject(), event.invitedEmailHash());
    }
    private byte[] hmac(byte[] timestamp, byte[] body) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(properties.eventsHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp); mac.update((byte) '.'); return mac.doFinal(body);
        } catch (Exception e) { throw new IllegalStateException("HMAC-SHA256 is unavailable", e); }
    }
    private ApiErrorException error(HttpStatus status, String code) {
        return new ApiErrorException(status, code, "The Keycloak event could not be accepted.", Map.of());
    }
}
