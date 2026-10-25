package com.massimotter.weave.backend.calendar.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CalendarCalDavPropertiesTest {
    @Test
    void diagnosticOutputDoesNotExposeProviderCredentialsOrAddress() {
        var properties = new CalendarCalDavProperties("https://calendar.private.test", null,
                CalendarCalDavProperties.AuthMode.BASIC, "private-account", "private-token", 0, null);
        assertThat(properties.toString()).doesNotContain(
                "private-account", "private-token", "calendar.private.test");
        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.requestTimeoutSeconds()).isEqualTo(10);
    }
}
