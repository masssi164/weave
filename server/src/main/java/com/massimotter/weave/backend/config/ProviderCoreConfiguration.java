package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderPort;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.provider.StaticProviderPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderCoreConfiguration {

    @Bean
    ProviderPort identityRealmProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.IDENTITY_REALM,
                "keycloak-realm",
                "Identity realm provider seam is reserved for the Keycloak dry-run/readiness contract from backend PR #103.",
                Set.of("realm-readiness", "realm-dry-run", "client-scope-diff", "role-diff"),
                Set.of("direct-frontend-keycloak-admin", "secret-export", "live-realm-mutation-without-audit"),
                List.of("keycloak"),
                Map.of("dependency", "weave-backend#103", "compatibleSeam", true));
    }

    @Bean
    ProviderPort matrixProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.MATRIX,
                "synapse-homeserver",
                "Matrix/Synapse is the chat and room substrate; provider status stays support-safe and does not expose room keys or raw homeserver errors.",
                Set.of("workspace-room-readiness", "message-sync-readiness", "e2ee-status-readiness", "homeserver-discovery"),
                Set.of("room-key-export", "raw-homeserver-errors", "direct-flutter-admin-api", "credential-exposure"),
                List.of("synapse"),
                Map.of("substrate", "matrix", "chatE2eeBoundary", "matrix-chat-only", "mediaCallsCovered", false));
    }

    @Bean
    ProviderPort matrixAuthProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.MATRIX_AUTH,
                "matrix-authentication-service",
                "Matrix Authentication Service is the Matrix auth bridge seam; status is fail-closed and support-safe.",
                Set.of("oidc-bridge-readiness", "client-registration-readiness", "session-policy-readiness"),
                Set.of("client-secret-export", "raw-mas-errors", "direct-flutter-admin-api", "credential-exposure"),
                List.of("matrix-authentication-service"),
                Map.of("substrate", "matrix", "authBridge", true, "compatibleSeam", true));
    }

    @Bean
    ProviderPort filesProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.FILES,
                "nextcloud-files",
                "Files facade is backend-owned and backed by Nextcloud WebDAV/OCS when configured.",
                Set.of("list", "upload", "download", "create-folder", "delete", "quota-status"),
                Set.of("direct-flutter-webdav", "html-scraping", "credential-exposure", "public-links-by-default"),
                List.of("nextcloud"),
                Map.of("facade", "/api/files", "rawProviderUiIsProductSurface", false));
    }

    @Bean
    ProviderPort calendarProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.CALENDAR,
                "nextcloud-caldav",
                "Calendar facade is backend-owned and maps workspace/team/channel scopes to CalDAV when configured.",
                Set.of("list-events", "read-event", "create-event", "update-event", "delete-event", "client-setup-metadata"),
                Set.of("private-user-calendar-ingestion", "credential-export", "direct-flutter-caldav"),
                List.of("nextcloud-caldav"),
                Map.of("facade", "/api/calendar", "scopeModel", "workspace/team/channel"));
    }

    @Bean
    ProviderPort contactsProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.CONTACTS,
                "nextcloud-carddav",
                "Contacts provider seam is reserved for the Nextcloud CardDAV contract from backend PR #104.",
                Set.of("address-book-list", "contact-search", "contact-read"),
                Set.of("credential-exposure", "direct-flutter-carddav", "raw-vcard-errors"),
                List.of("nextcloud-carddav"),
                Map.of("dependency", "weave-backend#104", "compatibleSeam", true));
    }

    @Bean
    ProviderPort formsProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.FORMS,
                "nextcloud-forms",
                "Forms provider seam is reserved for the Nextcloud Forms contract from backend PR #104.",
                Set.of("form-list", "form-read", "submission-summary", "submission-export"),
                Set.of("raw-provider-errors", "credential-exposure", "direct-flutter-forms-api"),
                List.of("nextcloud-forms"),
                Map.of("dependency", "weave-backend#104", "compatibleSeam", true));
    }

    @Bean
    ProviderPort boardsProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.BOARDS,
                "openproject-primary",
                "Boards/PM facade remains provider-neutral; OpenProject is the primary read-sync provider, Deck stays optional.",
                Set.of("project-list", "board-list", "task-list", "task-create-local-preview", "task-move-local-preview", "task-complete-local-preview", "read-sync"),
                Set.of("raw-openproject-ui-as-product", "provider-writes-without-audit", "direct-flutter-provider-api"),
                List.of("openproject", "nextcloud-deck", "vikunja-comparison"),
                Map.of("primaryProvider", "openproject", "optionalProvider", "nextcloud-deck", "facade", "/api/boards/workspace"));
    }

    @Bean
    ProviderPort liveKitMeetingsProviderRegistrySeam(LiveKitMeetingsProviderProperties liveKit) {
        ProviderState state = liveKit.enabled()
                ? liveKit.configured() ? ProviderState.CONFIGURED : ProviderState.NOT_CONFIGURED
                : ProviderState.DISABLED;
        String readiness = liveKit.enabled()
                ? liveKit.configured() ? "configured" : "not_configured"
                : "disabled";

        return new StaticProviderPort(new ProviderStatusResponse(
                ProviderModule.MEETINGS,
                "livekit",
                state,
                readiness,
                liveKit.enabled(),
                liveKit.enabled() && liveKit.configured(),
                false,
                true,
                true,
                false,
                "LiveKit is the active meetings/video-call provider contract; room/session access stays behind a backend-owned token facade and fails closed until configured.",
                Set.of(
                        "room-readiness",
                        "join-token-broker",
                        "server-url-discovery",
                        "calendar-thread-binding",
                        "recording-policy-readiness"),
                Set.of(
                        "non-livekit-meetings-provider",
                        "direct-flutter-livekit-admin-api",
                        "livekit-api-key-exposure",
                        "livekit-api-secret-exposure",
                        "credential-bearing-join-url",
                        "raw-provider-errors"),
                List.of("meetings-provider-not-configured", "meetings-provider-disabled", "meetings-provider-unavailable", "meetings-token-unavailable"),
                "support-safe: no LiveKit API keys, API secrets, bearer tokens, room tokens, credential-bearing URLs, or raw provider errors",
                List.of("livekit"),
                Map.of(
                        "activeProvider", "livekit",
                        "livekitUrlConfigured", liveKit.urlConfigured(),
                        "apiKeyConfigured", liveKit.apiKeyConfigured(),
                        "apiSecretConfigured", liveKit.apiSecretConfigured(),
                        "tokenEndpointConfigured", liveKit.tokenEndpointConfigured(),
                        "directCredentialModeConfigured", liveKit.directCredentialModeConfigured(),
                        "tokenEndpointModeConfigured", liveKit.tokenEndpointModeConfigured(),
                        "secretsReturned", false)));
    }
}
