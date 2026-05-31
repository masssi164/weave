package com.massimotter.weave.backend.office.port;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.office.OfficeCapabilitiesResponse;
import com.massimotter.weave.backend.model.office.OfficeCapabilityFlagsResponse;
import com.massimotter.weave.backend.model.office.OfficeLaunchRequest;
import com.massimotter.weave.backend.model.office.OfficeLaunchResponse;
import com.massimotter.weave.backend.model.office.OfficeLockSessionReadinessResponse;
import com.massimotter.weave.backend.model.office.OfficePermissionModelResponse;
import com.massimotter.weave.backend.model.office.OfficeProviderCandidateResponse;
import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderRedactor;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DisabledOfficeProvider implements OfficeProvider {

    private final ProviderStatusResponse status = new ProviderStatusResponse(
            ProviderModule.OFFICE,
            "onlyoffice-community",
            ProviderState.NOT_CONFIGURED,
            "not_configured",
            false,
            false,
            true,
            true,
            true,
            false,
            "ONLYOFFICE Docs Community is the default candidate, but no office provider runtime/session bridge is configured.",
            Set.of("view", "edit", "comment", "review", "form-fill", "docx", "xlsx", "pptx", "odt", "ods", "odp", "pdf-view"),
            Set.of("launch-session", "credential-bearing-url", "document-server-token-exposure", "raw-provider-errors"),
            List.of("office-provider-not-configured", "office-provider-unavailable", "office-unsupported-mode"),
            "support-safe: no document-server JWTs, signed URLs, app passwords, bearer tokens, callbacks secrets, or raw provider errors",
            List.of("onlyoffice-community", "collabora-code", "microsoft-365-office-graph", "wopi-host"),
            Map.of(
                    "defaultProvider", "onlyoffice-community",
                    "nonDefaultProvider", "collabora-code",
                    "collaboraPosture", "non-default/licensing-risk",
                    "likelyFirstPath", "nextcloud-onlyoffice-app-behind-backend-facade",
                    "providerRealityLevel", "contract_only",
                    "memberImpact", "coming_later",
                    "missingReadinessPrerequisites", List.of(
                            "document-runtime",
                            "callback-url",
                            "jwt-or-session-secret",
                            "storage-binding",
                            "permission-model",
                            "health-check")));

    @Override
    public ProviderStatusResponse status() {
        return status;
    }

    @Override
    public OfficeCapabilitiesResponse capabilities() {
        return new OfficeCapabilitiesResponse(
                "provider-neutral-office-contract",
                false,
                false,
                true,
                "unavailable",
                "onlyoffice-community",
                List.of(memberVisibleStatus()),
                List.of(
                        new OfficeProviderCandidateResponse(
                                "onlyoffice-community",
                                "ONLYOFFICE Docs Community",
                                true,
                                "default-candidate",
                                "free community runtime candidate",
                                "likely through Nextcloud ONLYOFFICE app behind backend facade",
                                List.of("No editing is promised until runtime, callback verification, session token, and permission gates are configured.")),
                        new OfficeProviderCandidateResponse(
                                "collabora-code",
                                "Collabora/CODE",
                                false,
                                "non-default",
                                "licensing/runtime fit still a risk for current requirements",
                                "adapter-neutral future candidate only",
                                List.of("Represented for capability comparison, not default enablement."))),
                new OfficeCapabilityFlagsResponse(false, false, false, false, false),
                List.of(),
                new OfficePermissionModelResponse(false, false, false, false, false, "Office provider unavailable; no document session permissions granted."),
                new OfficeLockSessionReadinessResponse("unavailable", "unavailable", "unavailable", true));
    }

    private ProviderStatusResponse memberVisibleStatus() {
        return new ProviderStatusResponse(
                status.module(),
                status.providerKey(),
                status.state(),
                status.readiness(),
                status.enabled(),
                status.configured(),
                status.readOnly(),
                status.failClosed(),
                status.supportSafe(),
                status.paidFeaturesRequired(),
                status.summary(),
                status.supportedCapabilities(),
                status.unsupportedOperations(),
                status.supportSafeErrorCodes(),
                status.redactionPolicy(),
                status.candidates(),
                Map.of());
    }

    @Override
    public OfficeLaunchResponse launch(OfficeLaunchRequest request) {
        String requestedMode = request == null ? "unknown" : request.requestedMode();
        throw new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "office-provider-not-configured",
                "Office document launch is unavailable until a backend-owned provider adapter is configured.",
                ProviderRedactor.supportSafeDetails("office", "launch", "requestedMode=" + requestedMode + "; provider=onlyoffice-community"));
    }
}
