package com.massimotter.weave.backend.calendar.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import com.massimotter.weave.backend.calendar.api.CalendarAccessModelResponse;
import com.massimotter.weave.backend.calendar.api.CalendarAccessPolicyResponse;
import com.massimotter.weave.backend.calendar.api.CalendarClientSetupOptionResponse;
import com.massimotter.weave.backend.calendar.api.CalendarClientSetupResponse;
import com.massimotter.weave.backend.calendar.api.CalendarCredentialReadinessResponse;
import com.massimotter.weave.backend.calendar.api.CalendarExternalEndpointsResponse;
import com.massimotter.weave.backend.calendar.api.CalendarNativeSyncOptionResponse;
import com.massimotter.weave.backend.calendar.api.CalendarNativeSyncSetupResponse;
import com.massimotter.weave.backend.calendar.api.CalendarScopeResponse;
import com.massimotter.weave.backend.calendar.api.CalendarSetupCredentialListResponse;
import com.massimotter.weave.backend.calendar.api.CalendarSetupCredentialRequest;
import com.massimotter.weave.backend.calendar.api.CalendarSetupCredentialResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.calendar.application.AppleMobileConfigProfile;
import com.massimotter.weave.backend.calendar.provider.caldav.AppleMobileConfigProfileRenderer;
import com.massimotter.weave.backend.calendar.domain.CalendarPrincipal;
import com.massimotter.weave.backend.security.device.DeviceCredential;
import com.massimotter.weave.backend.security.device.DeviceCredentialException;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;

import com.massimotter.weave.backend.calendar.application.CalendarFacadeAuthorization.PrincipalContext;

/** Client setup and revocable device credentials, independent of event/DAV synchronization state. */
public final class CalendarClientSetupService {
    private final CalendarFacadeAuthorization authorization;
    private final DeviceCredentialService deviceCredentialService;
    private final AppleMobileConfigProfileRenderer appleProfileRenderer;

    public CalendarClientSetupService(CalendarFacadeAuthorization authorization,
            DeviceCredentialService deviceCredentialService, AppleMobileConfigProfileRenderer appleProfileRenderer) {
        this.authorization = Objects.requireNonNull(authorization);
        this.deviceCredentialService = Objects.requireNonNull(deviceCredentialService);
        this.appleProfileRenderer = Objects.requireNonNull(appleProfileRenderer);
    }

    public CalendarClientSetupResponse clientSetup() {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "client-setup");
        CalendarPrincipal principal = authorization.principal();
        String username = principal.userId();
        String discoveryUrl = "/caldav";
        String principalUrl = "/caldav/principals/users/" + strictPathSegment(username) + "/";

        return new CalendarClientSetupResponse(
                CalendarScopeResponse.workspace(),
                accessModel(),
                credentialReadiness(),
                username,
                new CalendarExternalEndpointsResponse("/caldav", discoveryUrl, principalUrl),
                "The backend returns a Weave-issued device secret only in the create response. "
                        + "Stored/listed credentials expose no secret, and provider credentials or endpoints are never returned.",
                List.of(
                        new CalendarClientSetupOptionResponse(
                                "apple",
                                "mobileconfig",
                                false,
                                null,
                                "Signed .mobileconfig download remains fail-closed until profile signing is implemented.",
                                List.of(
                                        "iOS, iPadOS, and macOS can install a CalDAV configuration profile with host, port, SSL, principal URL, and username.",
                                        "Profile generation must not embed a permanent password or backend service credential in the profile.",
                                        "The backend route is reserved for a signed no-secret profile and currently returns 503 rather than serving an unsigned artifact.")),
                        new CalendarClientSetupOptionResponse(
                                "android",
                                "sync-adapter",
                                false,
                                null,
                                "Android Calendar setup waits for the Weave Account/SyncAdapter implementation.",
                                List.of(
                                        "Android has no universal native CalDAV account profile equivalent.",
                                        "The target path is a Weave account plus SyncAdapter that writes through the Weave calendar facade.",
                                        "Webcal/ICS subscriptions are read-only and should remain a separate fallback once scoped feed tokens exist.")),
                        new CalendarClientSetupOptionResponse(
                                "desktop",
                                "caldav-manual",
                                true,
                                "/api/calendar/client-setup/credentials",
                                null,
                                List.of(
                                        "Create a scoped credential, then use the Weave CalDAV discovery path in Thunderbird, Apple Calendar, GNOME, or KDE calendar clients.",
                                        "Microsoft Outlook generally needs an add-in for CalDAV; read-only ICS/webcal can be offered later where acceptable.",
                                        "Use the returned credential ID as the username and its one-time Weave secret as the password.")),
                        new CalendarClientSetupOptionResponse(
                                "subscription",
                                "webcal-ics",
                                false,
                                null,
                                "A revocable read-only ICS/webcal feed token is not implemented yet.",
                                List.of(
                                        "ICS/webcal is one-way subscription/download, not full two-way CalDAV sync.",
                        "It is useful for clients without CalDAV support once scoped feed tokens and revocation are available."))));
    }

    public CalendarNativeSyncSetupResponse nativeSyncSetup(WorkspaceCapabilityStatusResponse readiness) {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "native-sync-setup");
        return new CalendarNativeSyncSetupResponse(
                readiness,
                true,
                false,
                false,
                "/caldav",
                "/api/calendar/client-setup/credentials",
                "/api/calendar/client-setup/apple.mobileconfig",
                "/caldav/{scopePath}/{eventUid}.ics",
                List.of(
                        new CalendarNativeSyncOptionResponse(
                                "ios",
                                "CalDAVConfigurationProfile",
                                "pigeon-or-platform-channel",
                                false,
                                "profile_contract_ready_signing_blocked",
                                "open-weave-calendar-native-setup",
                                List.of(
                                        "ios-signed-mobileconfig",
                                        "weave-caldav-compatible-facade",
                                        "scoped-revocable-device-credential",
                                        "physical-ios-calendar-sync-evidence"),
                                List.of(
                                        "iOS setup uses a Weave-hosted profile flow and system Calendar account semantics.",
                                        "The profile route remains fail-closed until signing and scoped credential issuance are configured.",
                                        "Event sync must target the Weave calendar facade, not a storage provider account.")),
                        new CalendarNativeSyncOptionResponse(
                                "android",
                                "CalendarContractAccountSyncAdapter",
                                "pigeon-or-platform-channel",
                                false,
                                "sync_adapter_contract_ready_implementation_blocked",
                                "open-weave-calendar-native-setup",
                                List.of(
                                        "android-account-authenticator",
                                        "android-sync-adapter",
                                        "calendar-provider-sync-identity",
                                        "scoped-revocable-device-credential",
                                        "android-instrumentation-sync-evidence"),
                                List.of(
                                        "Android setup uses a Weave account plus SyncAdapter boundary for Calendar Provider writes.",
                                        "Flutter may start setup, show status, and revoke only.",
                                        "Calendar rows and sync state stay in the native provider layer and Weave facade."))),
                List.of(
                        "OPTIONS /caldav",
                        "PROPFIND /caldav",
                        "REPORT /caldav calendar-query",
                        "REPORT /caldav free-busy-query",
                        "GET /caldav/{scopePath}/{eventUid}.ics",
                        "PUT /caldav/{scopePath}/{eventUid}.ics",
                        "DELETE /caldav/{scopePath}/{eventUid}.ics",
                        "POST /api/calendar/client-setup/credentials",
                        "DELETE /api/calendar/client-setup/credentials/{credentialId}",
                        "GET /api/calendar/client-setup/apple.mobileconfig"),
                List.of(
                        "Signed Apple profile delivery is not configured yet.",
                        "Android Account/SyncAdapter implementation is not wired yet.",
                        "Scoped native device credential issuance currently returns no secret material.",
                        "Physical-device sync and revoke evidence is still required."));
    }


    public AppleMobileConfigProfile appleMobileConfigProfile() {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "download-apple-mobileconfig");
        // Keep the download route present but unavailable until a real signing path is wired.
        // The unsigned renderer is covered by tests so the future signer has a no-secret input artifact.
        appleProfileRenderer.renderUnsignedNoSecretProfile(authorization.principal());
        throw new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "calendar-apple-profile-unavailable",
                "Apple Calendar profile download is not available until signed no-secret profile generation is configured.",
                Map.of(
                        "module", "calendar",
                        "operation", "download-apple-mobileconfig",
                        "requiresSignedProfile", true,
                        "passwordIncluded", false,
                        "backendActorCredentialsExposed", false,
                        "blockers", List.of(
                                "Profile signing is not configured or implemented in this backend slice.",
                                "Revocable per-client CalDAV credential issuance remains a prerequisite for password-bearing profiles.",
                                "Unsigned profiles are deliberately not downloadable from the authenticated API.")));
    }

    public CalendarAccessPolicyResponse accessPolicy() {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "access-policy");
        return new CalendarAccessPolicyResponse(
                accessModel(),
                List.of("workspace-calendar.read", "workspace-calendar.write", "client-setup.metadata"),
                List.of("private-personal-calendar.read", "backend-actor-private-personal-calendar.read"),
                List.of(
                        "Choose and document a private calendar access model: user sharing/provisioning, Nextcloud Login Flow/app password, delegated token exchange, or a Weave token bridge.",
                        "Add operator diagnostics proving private calendar templates are explicitly authorized.",
                        "Add revocation and audit tests before any private personal CalDAV endpoint is enabled."),
                false);
    }

    public CalendarSetupCredentialListResponse setupCredentials() {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "list-setup-credentials");
        PrincipalContext principal = authorization.currentPrincipalContext();
        return new CalendarSetupCredentialListResponse(deviceCredentialService
                .list("calendar", principal.principalRef()).stream()
                .map(credential -> calendarCredentialResponse(credential, null))
                .toList());
    }

    public CalendarSetupCredentialResponse createSetupCredential(CalendarSetupCredentialRequest request) {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.EDIT, "create-setup-credential");
        CalendarPrincipal principal = authorization.principal();
        PrincipalContext context = authorization.currentPrincipalContext();
        var issued = deviceCredentialService.issue(
                "calendar",
                context.tenantId(),
                context.principalRef(),
                principal.subject(),
                principal.userId(),
                request.clientType(),
                request.label(),
                Set.of("calendar.read", "calendar.manage_events"));
        return calendarCredentialResponse(issued.credential(), issued.secret());
    }

    public CalendarSetupCredentialResponse revokeSetupCredential(String credentialId) {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.EDIT, "revoke-setup-credential");
        PrincipalContext principal = authorization.currentPrincipalContext();
        DeviceCredential revoked;
        try {
            revoked = deviceCredentialService.revoke("calendar", credentialId, principal.principalRef());
        } catch (DeviceCredentialException exception) {
            throw new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "calendar-setup-credential-not-found",
                    "Calendar setup credential was not found.",
                    Map.of("module", "calendar", "operation", "revoke-setup-credential"));
        }
        return calendarCredentialResponse(revoked, null);
    }

    private CalendarAccessModelResponse accessModel() {
        return new CalendarAccessModelResponse(
                "workspace-team-channel-calendar",
                "workspace-team-channel",
                false,
                "Private per-user CalDAV calendars are not exposed until provisioning, sharing, or delegated-token access is specified and tested.",
                "external clients use user-owned revocable per-client credentials; backend actor credentials are never issued to clients",
                List.of(
                        "The product calendar facade exposes workspace, team, and channel scope metadata.",
                        "Backend CalDAV configuration that targets arbitrary private personal calendars must stay fail-closed.",
                        "External clients may use Weave CalDAV discovery paths only after scoped credentials and revoke evidence exist."));
    }

    private CalendarCredentialReadinessResponse credentialReadiness() {
        return new CalendarCredentialReadinessResponse(
                "revocable_credentials_ready",
                false,
                false,
                true,
                false,
                false,
                List.of(
                        "Signed Apple .mobileconfig generation is not implemented yet.",
                        "Read-only ICS/webcal feed tokens are not implemented yet."));
    }

    private CalendarSetupCredentialResponse calendarCredentialResponse(
            DeviceCredential credential,
            String secret) {
        boolean active = credential.activeAt(Instant.now());
        String state = credential.revokedAt() != null ? "revoked" : active ? "active" : "expired";
        return new CalendarSetupCredentialResponse(
                credential.credentialId(),
                state,
                credential.credentialId(),
                credential.principalRef(),
                credential.clientType(),
                credential.label(),
                OffsetDateTime.ofInstant(credential.issuedAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(credential.expiresAt(), ZoneOffset.UTC),
                credential.revokedAt() == null
                        ? null
                        : OffsetDateTime.ofInstant(credential.revokedAt(), ZoneOffset.UTC),
                secret != null,
                secret,
                false,
                active ? List.of("DELETE /api/calendar/client-setup/credentials/" + credential.credentialId()) : List.of());
    }

    private static String strictPathSegment(String value) {
        StringBuilder encoded = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (isUnreservedPathCharacter(codePoint)) {
                encoded.appendCodePoint(codePoint);
                return;
            }
            byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
            for (byte current : bytes) {
                encoded.append(String.format("%%%02X", current & 0xFF));
            }
        });
        return encoded.toString();
    }

    private static boolean isUnreservedPathCharacter(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= '0' && codePoint <= '9')
                || codePoint == '-'
                || codePoint == '.'
                || codePoint == '_'
                || codePoint == '~';
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

}
