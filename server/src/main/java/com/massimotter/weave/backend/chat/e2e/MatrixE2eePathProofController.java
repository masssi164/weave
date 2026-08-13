package com.massimotter.weave.backend.chat.e2e;

import com.massimotter.weave.backend.config.ChatE2eProofSecurityConfiguration;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.matrix.MatrixE2eeStateService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes aggregate evidence for the isolated northbound Matrix to-device
 * path. The run-scoped proof security chain authenticates this endpoint; the
 * response contains counts only and is never enabled in persistent dogfood.
 */
@RestController
@Hidden
@ConditionalOnProperty(name = "weave.chat.e2e-proof.enabled", havingValue = "true")
@ConditionalOnProperty(name = "weave.chat.provider", havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
public final class MatrixE2eePathProofController {

    public static final String PATH = ChatE2eProofSecurityConfiguration.PATH + "/matrix-e2ee";

    private final MatrixE2eeStateService matrixE2eeStateService;

    public MatrixE2eePathProofController(MatrixE2eeStateService matrixE2eeStateService) {
        this.matrixE2eeStateService = matrixE2eeStateService;
    }

    @GetMapping(PATH)
    public MatrixE2eeStateService.SupportSafeToDeviceEvidence proof() {
        return matrixE2eeStateService.supportSafeToDeviceEvidence();
    }
}
