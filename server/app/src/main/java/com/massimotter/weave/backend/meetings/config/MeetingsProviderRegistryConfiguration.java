package com.massimotter.weave.backend.meetings.config;

import com.massimotter.weave.backend.config.LiveKitSfuProviderProperties;
import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderPort;
import com.massimotter.weave.backend.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.provider.StaticProviderPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MeetingsProviderRegistryConfiguration {

    @Bean
    ProviderPort liveKitSfuProviderRegistrySeam(LiveKitSfuProviderProperties liveKit) {
        ProviderState state = liveKit.enabled()
                ? liveKit.configured() ? ProviderState.CONFIGURED : ProviderState.NOT_CONFIGURED
                : ProviderState.DISABLED;
        String readiness = liveKit.enabled()
                ? liveKit.configured() ? "configured" : "not_configured"
                : "disabled";

        return new StaticProviderPort(new ProviderStatusResponse(
                ProviderModule.MEETINGS,
                "livekit",
                state,
                readiness,
                liveKit.enabled(),
                liveKit.enabled() && liveKit.configured(),
                false,
                true,
                true,
                false,
                "LiveKit is a replaceable southbound SFU adapter for MatrixRTC Profile 0; signaling, membership, authorization, consent, and member APIs remain Weave/Matrix-owned.",
                Set.of(
                        "sfu-configuration-readiness",
                        "matrixrtc-profile-0-target",
                        "media-e2ee-readiness",
                        "turn-readiness"),
                Set.of(
                        "member-calls-rest-api",
                        "proprietary-join-grant",
                        "identity-only-sfu-token",
                        "direct-flutter-livekit-admin-api",
                        "livekit-api-key-exposure",
                        "livekit-api-secret-exposure",
                        "credential-bearing-join-url",
                        "raw-provider-errors"),
                List.of("calls-sfu-not-configured", "calls-sfu-disabled", "calls-sfu-unavailable", "rtc-authorization-required"),
                "support-safe: no LiveKit API keys, API secrets, bearer tokens, room tokens, credential-bearing URLs, or raw provider errors",
                List.of("livekit", "generic-webrtc-sfu"),
                ProviderRealityLevel.CONFIGURED,
                Map.of(
                        "activeSfuAdapter", "livekit",
                        "livekitUrlConfigured", liveKit.urlConfigured(),
                        "apiKeyConfigured", liveKit.apiKeyConfigured(),
                        "apiSecretConfigured", liveKit.apiSecretConfigured(),
                        "tokenEndpointConfigured", liveKit.tokenEndpointConfigured(),
                        "directCredentialModeConfigured", liveKit.directCredentialModeConfigured(),
                        "tokenEndpointModeConfigured", liveKit.tokenEndpointModeConfigured(),
                        "secretsReturned", false)));
    }
}
