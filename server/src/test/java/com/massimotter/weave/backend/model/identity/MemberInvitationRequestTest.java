package com.massimotter.weave.backend.model.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MemberInvitationRequestTest {

    private final JsonMapper mapper =
            JsonMapper.builder().findAndAddModules().build();

    @Test
    void acceptsOneCanonicalRoleWithoutProviderGroupInput() throws Exception {
        MemberInvitationRequest request = mapper.readValue(
                """
                {
                  "email": "member@example.invalid",
                  "displayName": "Member Example",
                  "role": "member"
                }
                """,
                MemberInvitationRequest.class);

        assertThat(request.role()).isEqualTo("member");
    }

    @Test
    void rejectsLegacyClientSuppliedOrganizationGroups() {
        assertThatThrownBy(() -> mapper.readValue(
                """
                {
                  "email": "member@example.invalid",
                  "role": "member",
                  "organizationGroups": ["/weave/owners"]
                }
                """,
                MemberInvitationRequest.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(
                        "Unknown member invitation property; send one canonical role only");
    }
}
