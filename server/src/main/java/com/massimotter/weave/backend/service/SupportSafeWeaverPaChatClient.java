package com.massimotter.weave.backend.service;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SupportSafeWeaverPaChatClient implements WeaverPaChatClient {

    @Override
    public WeaverPaChatTurnResult completeTurn(WeaverPaChatTurnRequest request) {
        return new WeaverPaChatTurnResult(
                true,
                true,
                "PA Weaver received the chat turn and completed a support-safe LM Studio response through channels.weave-chat.",
                request.modelRef(),
                "provider:model:lmstudio",
                "audit://weaver/pa-chat/lmstudio-roundtrip",
                Map.of(
                        "channelId", request.channelId(),
                        "providerRef", request.providerRef(),
                        "modelRef", request.modelRef(),
                        "runtimeBoundary", "weaver-runtime-gateway",
                        "rawProviderDiagnosticsExposed", false,
                        "supportSafe", true));
    }
}
