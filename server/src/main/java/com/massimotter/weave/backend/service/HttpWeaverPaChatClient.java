package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaverPaChatProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpWeaverPaChatClient implements WeaverPaChatClient {

    private final WeaverPaChatProperties properties;
    private final RestClient restClient;

    @Autowired
    public HttpWeaverPaChatClient(WeaverPaChatProperties properties) {
        this(properties, RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build());
    }

    HttpWeaverPaChatClient(WeaverPaChatProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient == null ? RestClient.builder().build() : restClient;
    }

    @Override
    public WeaverPaChatTurnResult completeTurn(WeaverPaChatTurnRequest request) {
        if (!properties.bridgeConfigured()) {
            throw new WeaverPaChatUnavailableException(
                    "PA Weaver chat bridge is not configured; refusing to synthesize LM Studio evidence.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", request.conversationId());
        payload.put("messageId", request.messageId());
        payload.put("actorRef", request.actorRef());
        payload.put("text", request.text());
        payload.put("channelId", request.channelId());
        payload.put("providerRef", request.providerRef());
        payload.put("modelRef", request.modelRef());
        payload.put("runtimeTokenRef", properties.runtimeTokenRef());
        payload.put("supportSafeContext", request.supportSafeContext());

        WeaverPaChatBridgeResponse response;
        try {
            response = restClient.post()
                    .uri(properties.bridgeUrl())
                    .body(payload)
                    .retrieve()
                    .body(WeaverPaChatBridgeResponse.class);
        } catch (RestClientException exception) {
            throw new WeaverPaChatUnavailableException("PA Weaver chat bridge call failed.", exception);
        }
        return toResult(request, response);
    }

    private WeaverPaChatTurnResult toResult(
            WeaverPaChatTurnRequest request,
            WeaverPaChatBridgeResponse response) {
        if (response == null || !response.weaverReceived() || !response.lmStudioResponseReceived()
                || !hasText(response.answer())) {
            throw new WeaverPaChatUnavailableException(
                    "PA Weaver chat bridge did not return completed Weaver and LM Studio evidence.");
        }
        String modelRef = hasText(response.modelRef()) ? response.modelRef() : request.modelRef();
        String auditRef = hasText(response.auditRef()) ? response.auditRef() : "audit://weaver/pa-chat/bridge-roundtrip";
        Map<String, Object> evidence = response.supportSafeEvidence() == null
                ? Map.of()
                : Map.copyOf(response.supportSafeEvidence());
        return new WeaverPaChatTurnResult(
                true,
                true,
                response.answer(),
                modelRef,
                hasText(response.providerRef()) ? response.providerRef() : "provider:model:lmstudio",
                auditRef,
                evidence);
    }

    private static SimpleClientHttpRequestFactory requestFactory(WeaverPaChatProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (properties != null && properties.timeout() != null) {
            requestFactory.setConnectTimeout(properties.timeout());
            requestFactory.setReadTimeout(properties.timeout());
        }
        return requestFactory;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record WeaverPaChatBridgeResponse(
            boolean weaverReceived,
            boolean lmStudioResponseReceived,
            String answer,
            String modelRef,
            String providerRef,
            String auditRef,
            Map<String, Object> supportSafeEvidence) {
    }
}
