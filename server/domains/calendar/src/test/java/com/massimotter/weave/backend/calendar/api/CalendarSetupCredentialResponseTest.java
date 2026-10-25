package com.massimotter.weave.backend.calendar.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class CalendarSetupCredentialResponseTest {
    @Test
    void diagnosticsRedactCredentialAndIdentityWhileWireValuesStayIntact() {
        var response = new CalendarSetupCredentialResponse("private-id", "ACTIVE", "private-user", "private-principal", "dav", "private-label",
                null, null, null, true, "private-secret", true, java.util.List.of());
        assertThat(response.toString()).doesNotContain(
                "private-id", "private-user", "private-principal", "private-label", "private-secret");
        var json = new ObjectMapper().valueToTree(response);
        assertThat(json.get("secret").asString()).isEqualTo("private-secret");
        assertThat(json.get("username").asString()).isEqualTo("private-user");
        assertThat(json.get("label").asString()).isEqualTo("private-label");
        assertThat(response.secret()).isEqualTo("private-secret");
    }
}
