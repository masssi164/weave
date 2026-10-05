package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NativeOrganizationClaimsTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void acceptsOnlyTheExactIsolatedNativeCoordinate() {
    assertThat(violation("{\"weave-e2e\":{\"id\":\"8f771be4-f526-5bef-97dc-00c8e2fa383d\"}}"))
        .isNull();
    assertThat(violation("{\"other-private-alias\":{\"id\":\"8f771be4-f526-5bef-97dc-00c8e2fa383d\"}}"))
        .isEqualTo("native-organization-alias");
    assertThat(violation("{\"weave-e2e\":{\"id\":\"wrong-private-id\"}}"))
        .isEqualTo("native-organization-id");
    assertThat(violation("{\"weave-e2e\":{}}"))
        .isEqualTo("native-organization-id");
  }

  @Test
  void distinguishesAbsentFromMalformedAndEmptyWithoutExposingClaimValues() {
    assertThat(NativeOrganizationClaims.violation(mapper.readTree("{}")))
        .isEqualTo("native-organization-absent");
    for (String value : new String[] {"null", "[]", "\"private-payload\"", "false"}) {
      assertThat(violation(value)).isEqualTo("native-organization-malformed");
    }
    assertThat(violation("{}")).isEqualTo("native-organization-empty");
    assertThat(violation("{\"weave-e2e\":{},\"private-second-alias\":{}}"))
        .isEqualTo("native-organization-multiple");
  }

  private String violation(String organization) {
    return NativeOrganizationClaims.violation(
        mapper.readTree("{\"organization\":" + organization + "}"));
  }
}
