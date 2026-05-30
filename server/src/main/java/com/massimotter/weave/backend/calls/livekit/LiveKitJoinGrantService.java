package com.massimotter.weave.backend.calls.livekit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.calls.domain.CallRole;
import com.massimotter.weave.backend.calls.domain.JoinGrant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class LiveKitJoinGrantService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String apiKey;
    private final String apiSecret;
    private final String mediaUrl;
    private final Duration ttl;
    private final Clock clock;

    public LiveKitJoinGrantService(String apiKey, String apiSecret, String mediaUrl, Duration ttl, Clock clock) {
        this.apiKey = requireText(apiKey, "apiKey");
        this.apiSecret = requireText(apiSecret, "apiSecret");
        this.mediaUrl = requireText(mediaUrl, "mediaUrl");
        this.ttl = ttl == null ? Duration.ofMinutes(10) : ttl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        if (this.ttl.isNegative() || this.ttl.isZero() || this.ttl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("LiveKit join grant TTL must be between 1 second and 30 minutes");
        }
    }

    public JoinGrant issueGrant(String meetingId, String roomName, String personRef, CallRole role) {
        String normalizedMeetingId = requireText(meetingId, "meetingId");
        String normalizedRoomName = requireText(roomName, "roomName");
        String normalizedPersonRef = requireText(personRef, "personRef");
        CallRole normalizedRole = java.util.Objects.requireNonNull(role, "role must not be null");
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        List<String> permissions = permissions(normalizedRole);
        String token = token(normalizedRoomName, normalizedPersonRef, normalizedRole, issuedAt, expiresAt);
        return new JoinGrant(
                "join-grant:" + normalizedMeetingId + ":" + normalizedPersonRef,
                normalizedMeetingId,
                normalizedRoomName,
                normalizedPersonRef,
                normalizedRole,
                permissions,
                mediaUrl,
                token,
                issuedAt,
                expiresAt,
                Map.of(
                        "provider", "livekit",
                        "tokenReturned", true,
                        "apiSecretReturned", false,
                        "ttlSeconds", ttl.toSeconds(),
                        "supportSafe", true));
    }

    private List<String> permissions(CallRole role) {
        return switch (role) {
            case HOST -> List.of("roomJoin", "canPublish", "canSubscribe", "canPublishData", "roomAdmin");
            case PARTICIPANT -> List.of("roomJoin", "canPublish", "canSubscribe", "canPublishData");
            case VIEWER -> List.of("roomJoin", "canSubscribe");
            case RECORDER_SERVICE -> List.of("roomJoin", "canSubscribe", "hidden", "recorder");
        };
    }

    private String token(String roomName, String personRef, CallRole role, Instant issuedAt, Instant expiresAt) {
        try {
            String header = base64Url(OBJECT_MAPPER.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            Map<String, Object> videoGrant = new LinkedHashMap<>();
            videoGrant.put("room", roomName);
            videoGrant.put("roomJoin", true);
            videoGrant.put("canPublish", role == CallRole.HOST || role == CallRole.PARTICIPANT);
            videoGrant.put("canSubscribe", true);
            videoGrant.put("canPublishData", role == CallRole.HOST || role == CallRole.PARTICIPANT);
            if (role == CallRole.HOST) {
                videoGrant.put("roomAdmin", true);
            }
            if (role == CallRole.RECORDER_SERVICE) {
                videoGrant.put("hidden", true);
                videoGrant.put("recorder", true);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", apiKey);
            payload.put("sub", personRef);
            payload.put("nbf", issuedAt.getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());
            payload.put("video", videoGrant);
            String body = base64Url(OBJECT_MAPPER.writeValueAsBytes(payload));
            String unsigned = header + "." + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return unsigned + "." + base64Url(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to issue support-safe LiveKit join grant", error);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
