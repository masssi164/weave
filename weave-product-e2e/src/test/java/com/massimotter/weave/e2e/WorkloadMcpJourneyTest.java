package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class WorkloadMcpJourneyTest {

  @Test
  void requiresTheRfc9068AccessTokenHeaderType() {
    var mapper = JsonMapper.builder().build();

    assertThat(
            WorkloadMcpJourney.hasRfc9068TokenType(
                mapper.createObjectNode().put("typ", "at+jwt")))
        .isTrue();
    assertThat(
            WorkloadMcpJourney.hasRfc9068TokenType(
                mapper.createObjectNode().put("typ", "JWT")))
        .isFalse();
  }

  @Test
  void reportsOnlyInvalidIdentityFieldNames() {
    var claims =
        JsonMapper.builder()
            .build()
            .createObjectNode()
            .put("iss", "https://wrong.example/realms/weave")
            .put("client_id", "weaver-cell-other")
            .put("azp", "weaver-cell-test")
            .put("sub", "")
            .put("jti", "")
            .put("iat", 1)
            .put("exp", 99);

    assertThat(
            WorkloadMcpJourney.invalidIdentityClaims(
                claims,
                "weaver-cell-test",
                "https://auth.weave.test/realms/weave",
                100))
        .containsExactlyInAnyOrder(
            "issuer", "client-id", "subject", "token-id", "expiry", "token-ttl")
        .doesNotContain(
            "https://wrong.example/realms/weave",
            "weaver-cell-other",
            "weaver-cell-test");
  }

  @Test
  void acceptsTheExactWorkloadIdentityShape() {
    var claims =
        JsonMapper.builder()
            .build()
            .createObjectNode()
            .put("iss", "https://auth.weave.test/realms/weave")
            .put("client_id", "weaver-cell-test")
            .put("azp", "weaver-cell-test")
            .put("sub", "service-account-subject")
            .put("jti", "token-identifier")
            .put("iat", 100)
            .put("exp", 101);

    assertThat(
            WorkloadMcpJourney.invalidIdentityClaims(
                claims,
                "weaver-cell-test",
                "https://auth.weave.test/realms/weave",
                100))
        .isEmpty();
  }

  @Test
  void keepsTheExternalWorkloadTokenLifetimeBoundaryAtSixtySeconds() {
    var mapper = JsonMapper.builder().build();
    var exactBoundary =
        mapper
            .createObjectNode()
            .put("iss", "https://auth.weave.test/realms/weave")
            .put("client_id", "weaver-cell-test")
            .put("azp", "weaver-cell-test")
            .put("sub", "service-account-subject")
            .put("jti", "token-identifier")
            .put("iat", 100)
            .put("exp", 160);
    var overBoundary = exactBoundary.deepCopy().put("exp", 161);

    assertThat(
            WorkloadMcpJourney.invalidIdentityClaims(
                exactBoundary,
                "weaver-cell-test",
                "https://auth.weave.test/realms/weave",
                100))
        .isEmpty();
    assertThat(
            WorkloadMcpJourney.invalidIdentityClaims(
                overBoundary,
                "weaver-cell-test",
                "https://auth.weave.test/realms/weave",
                100))
        .containsExactly("token-ttl");
  }

  @Test
  void extractsJsonFromAStreamableHttpEventWithMetadataLines() {
    String payload =
        """
        id: 7
        event: message
        data: {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25"}}

        """;

    assertThat(WorkloadMcpJourney.protocolPayload(payload))
        .isEqualTo(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-11-25\"}}");
  }

  @Test
  void classifiesOnlyAllowlistedFilesErrorCodes() throws Exception {
    var mapper = JsonMapper.builder().build();
    var response =
        mapper.readTree(
            """
            {"jsonrpc":"2.0","result":{"isError":true,"content":[
              {"type":"text","text":"Files facade rejected request: mcp-workload-files-forbidden"}
            ]}}
            """);
    var unsafe =
        mapper.readTree(
            """
            {"jsonrpc":"2.0","result":{"isError":true,"content":[
              {"type":"text","text":"secret bearer value"}
            ]}}
            """);

    assertThat(WorkloadMcpJourney.supportSafeErrorClass(response))
        .isEqualTo("mcp-workload-files-forbidden");
    assertThat(WorkloadMcpJourney.supportSafeErrorClass(unsafe))
        .isEqualTo("redacted-tool-error");
  }
}
