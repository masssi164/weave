package com.massimotter.weave.backend.model.agentruntime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;

public record FlattenedRuntimeProfileJwsResponse(
        @JsonProperty("protected") String protectedValue,
        String payload,
        String signature) {

    public static FlattenedRuntimeProfileJwsResponse from(SignedRuntimeProfile profile) {
        return new FlattenedRuntimeProfileJwsResponse(
                profile.protectedHeader(), profile.payload(), profile.signature());
    }
}
