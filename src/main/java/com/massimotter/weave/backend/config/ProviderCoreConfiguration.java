package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderPort;
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
                "keycloak-oidc",
                "Identity/OIDC provider seam is reserved for Keycloak realm, client, scope, and JWT validation readiness.",
                Set.of("oidc-issuer-readiness", "backend-jwt-validation", "pkce-client-readiness", "realm-readiness", "realm-dry-run", "client-scope-diff", "role-diff"),
                Set.of("direct-frontend-keycloak-admin", "secret-export", "live-realm-mutation-without-audit", "provider-token-reuse-as-module-login"),
                List.of("keycloak"),
                Map.of("dependency", "weave-backend#103", "compatibleSeam", true));
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
                "Contacts/CardDAV provider seam is reserved for the Nextcloud Contacts/CardDAV contract from backend PR #104.",
                Set.of("carddav-address-book-list", "carddav-contact-search", "carddav-contact-read"),
                Set.of("credential-exposure", "direct-flutter-carddav", "raw-vcard-errors"),
                List.of("nextcloud-carddav", "nextcloud-contacts"),
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
    ProviderPort matrixProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.MATRIX,
                "synapse-homeserver",
                "Matrix chat readiness is exposed as backend-owned status/policy while Flutter uses the Matrix client protocol directly.",
                Set.of("homeserver-discovery", "workspace-room-provisioning-status", "e2ee-status", "support-safe-room-metadata"),
                Set.of("server-readable-e2ee-message-content", "direct-keycloak-token-reuse-as-matrix-login", "federation-by-default", "agent-room-participation-without-consent"),
                List.of("synapse"),
                Map.of("facade", "/api/platform/status", "directClientProtocolException", true, "messageBodiesServerReadable", false));
    }

    @Bean
    ProviderPort matrixAuthProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.MATRIX_AUTH,
                "matrix-authentication-service",
                "MAS readiness is exposed as a fail-closed Matrix auth seam with Keycloak as the upstream OIDC provider.",
                Set.of("mas-oidc-discovery", "keycloak-upstream-oidc", "matrix-client-sso-readiness"),
                Set.of("direct-backend-token-login", "separate-matrix-passwords", "mas-admin-secret-export", "raw-oidc-error-exposure"),
                List.of("matrix-authentication-service", "keycloak"),
                Map.of("facade", "/api/platform/status", "upstreamIdentityProvider", "keycloak", "compatibleSeam", true));
    }

    @Bean
    ProviderPort meetingsProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.MEETINGS,
                "matrix-meetings",
                "Video-call/meeting provider support is deferred for MVP and fails closed; calendar may expose secret-free meeting context metadata only.",
                Set.of("calendar-meeting-context-metadata", "meeting-readiness-status"),
                Set.of("video-calls-mvp", "direct-flutter-call-provider-api", "meeting-credentials-in-calendar-response", "backend-access-to-e2ee-call-content"),
                List.of("matrix-native-calls", "element-call", "jitsi", "livekit"),
                Map.of("mvpScope", "deferred", "failClosed", true, "facade", "/api/platform/status"));
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
                Map.of("primaryProvider", "openproject", "optionalProvider", "nextcloud-deck", "facade", "/api/boards/preview"));
    }
}
