package com.massimotter.weave.backend.service.guest;

import com.massimotter.weave.backend.config.GuestAccessProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.guest.GuestAccessContractResponse;
import com.massimotter.weave.backend.model.guest.GuestInvitationRequest;
import com.massimotter.weave.backend.model.guest.GuestInvitationResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GuestAccessService {

    private static final List<String> ALLOWED_CAPABILITIES = List.of(
            "room.read", "room.message.write", "file.read", "calendar.free_busy.read");

    private final GuestAccessProperties properties;

    public GuestAccessService(GuestAccessProperties properties) {
        this.properties = properties;
    }

    public GuestAccessContractResponse contract() {
        return new GuestAccessContractResponse(
                properties.enabled(),
                "guest",
                List.of("pending", "active", "disabled", "expired"),
                List.of("files", "calendar", "chat"),
                ALLOWED_CAPABILITIES,
                List.of("invite", "accept", "role_change", "disable", "external_identity_link"),
                false,
                true);
    }

    public GuestInvitationResponse invite(GuestInvitationRequest request) {
        if (!properties.enabled()) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "guest-access-disabled",
                    "Guest access is disabled until explicit policy and operator review are enabled.",
                    Map.of("module", "guest", "operation", "invite", "defaultAccess", "deny"));
        }
        List<String> granted = request.capabilities() == null
                ? List.of()
                : request.capabilities().stream()
                        .filter(ALLOWED_CAPABILITIES::contains)
                        .distinct()
                        .sorted()
                        .toList();
        return new GuestInvitationResponse(
                "guest_" + digest(request.email()),
                "pending",
                "guest",
                granted,
                true,
                false);
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.toLowerCase().getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
