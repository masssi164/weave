package com.massimotter.weave.backend.model.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

class MemberInvitationRequestTest {

    private final JsonMapper mapper =
            JsonMapper.builder()
                    .findAndAddModules()
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

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
                  "organizationGroups": ["/owners"]
                }
                """,
                MemberInvitationRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
