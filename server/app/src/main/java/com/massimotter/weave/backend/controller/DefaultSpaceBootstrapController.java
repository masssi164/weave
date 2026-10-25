package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.identity.bootstrap.DefaultSpaceBootstrapCredential;
import com.massimotter.weave.backend.spaces.api.DefaultSpaceBootstrapResponse;
import com.massimotter.weave.backend.service.spaces.DefaultSpaceBootstrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "weave.spaces.bootstrap.enabled", havingValue = "true")
public class DefaultSpaceBootstrapController {
    public static final String PATH = "/api/bootstrap/default-space";
    public static final String CREDENTIAL_HEADER = "X-Weave-Space-Bootstrap-Token";
    private final DefaultSpaceBootstrapCredential credential;
    private final DefaultSpaceBootstrapService spaces;

    public DefaultSpaceBootstrapController(DefaultSpaceBootstrapCredential credential,
            DefaultSpaceBootstrapService spaces) {
        this.credential = credential;
        this.spaces = spaces;
    }

    @PostMapping(PATH)
    @Operation(operationId = "bootstrapDefaultSpace", summary = "Explicit development default-Space preparation",
            security = @SecurityRequirement(name = "space-bootstrap-token"))
    public synchronized DefaultSpaceBootstrapResponse prepare(@RequestHeader(CREDENTIAL_HEADER) String supplied) {
        try {
            if (!credential.matches(supplied)) {
                throw new ApiErrorException(HttpStatus.UNAUTHORIZED, "default-space-bootstrap-unauthorized",
                        "The protected Space bootstrap credential is invalid.", Map.of());
            }
        } catch (IllegalStateException unavailable) {
            throw new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "default-space-bootstrap-unavailable",
                    "The protected Space bootstrap operation is unavailable.", Map.of());
        }
        var result = spaces.prepare();
        credential.consumeAfterSuccess();
        return result;
    }
}
