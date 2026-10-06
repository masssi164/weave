package com.massimotter.weave.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Provider-neutral organization manifest consumed by Weave clients.")
public record PlatformConfigResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int schemaVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri") String organizationOrigin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri") String userApiBaseUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Oidc oidc,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Protocols protocols,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String releasePosture,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DomainCapability> domains,
        List<RecoveryAction> recoveryActions) {

    public record Oidc(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri") String issuer,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String clientId) {
    }

    public record Protocols(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri") String matrixClientServerBaseUrl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DomainCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String domain,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String state,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> capabilities,
            String supportReference) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecoveryAction(String code, String label, String supportReference) {
    }
}
