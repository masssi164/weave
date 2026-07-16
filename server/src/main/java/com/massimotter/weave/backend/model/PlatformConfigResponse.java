package com.massimotter.weave.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Provider-neutral organization manifest consumed by Weave clients.")
public record PlatformConfigResponse(
        int schemaVersion,
        String organizationOrigin,
        String controlPlaneBaseUrl,
        Oidc oidc,
        Protocols protocols,
        String releasePosture,
        List<DomainCapability> domains,
        List<RecoveryAction> recoveryActions) {

    public record Oidc(String issuer, String clientId) {
    }

    public record Protocols(
            String matrixClientServerBaseUrl,
            String filesWebDavBaseUrl,
            String calendarCalDavBaseUrl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DomainCapability(
            String domain,
            String state,
            List<String> capabilities,
            String supportReference) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecoveryAction(String code, String label, String supportReference) {
    }
}
