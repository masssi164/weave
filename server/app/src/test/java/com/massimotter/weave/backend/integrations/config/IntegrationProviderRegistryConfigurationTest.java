package com.massimotter.weave.backend.integrations.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderState;
import org.junit.jupiter.api.Test;

class IntegrationProviderRegistryConfigurationTest {
    @Test
    void reservedContactsAndFormsRemainUnconfiguredSupportSafeSeams() {
        var configuration = new IntegrationProviderRegistryConfiguration();
        var contacts = configuration.contactsProviderRegistrySeam().status();
        var forms = configuration.formsProviderRegistrySeam().status();
        assertThat(contacts.module()).isEqualTo(ProviderModule.CONTACTS);
        assertThat(forms.module()).isEqualTo(ProviderModule.FORMS);
        for (var status : java.util.List.of(contacts, forms)) {
            assertThat(status.state()).isEqualTo(ProviderState.NOT_CONFIGURED);
            assertThat(status.configured()).isFalse();
            assertThat(status.failClosed()).isTrue();
            assertThat(status.supportSafe()).isTrue();
        }
    }
}
