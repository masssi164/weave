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
                "keycloak-realm",
                "Identity realm provider seam is reserved for the Keycloak dry-run/readiness contract from backend PR #103.",
                Set.of("realm-readiness", "realm-dry-run", "client-scope-diff", "role-diff"),
                Set.of("direct-frontend-keycloak-admin", "secret-export", "live-realm-mutation-without-audit"),
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
                "nextcloud-contacts",
                "Contacts provider seam is reserved for the Nextcloud Contacts contract from backend PR #104.",
                Set.of("address-book-list", "contact-search", "contact-read"),
                Set.of("credential-exposure", "direct-flutter-carddav", "raw-vcard-errors"),
                List.of("nextcloud-contacts"),
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
                Map.of("primaryProvider", "openproject", "optionalProvider", "nextcloud-deck", "facade", "/api/boards/preview"));
    }
}
