package com.massimotter.weave.backend.service;

import java.util.Map;

public record WeaverPaChatTurnRequest(
        String conversationId,
        String messageId,
        String actorRef,
        String text,
        String channelId,
        String providerRef,
        String modelRef,
        Map<String, Object> supportSafeContext) {
}
