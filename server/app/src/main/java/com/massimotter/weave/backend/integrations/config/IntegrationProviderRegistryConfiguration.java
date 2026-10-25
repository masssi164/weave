package com.massimotter.weave.backend.integrations.config;

import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderPort;
import com.massimotter.weave.backend.provider.StaticProviderPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntegrationProviderRegistryConfiguration {

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
}
