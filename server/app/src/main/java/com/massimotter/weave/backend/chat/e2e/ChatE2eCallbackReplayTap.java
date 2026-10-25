package com.massimotter.weave.backend.chat.e2e;

import com.massimotter.weave.backend.config.ChatE2eProofProperties;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Transient isolated-stack hook for replaying one real private Application Service callback.
 * It is absent from persistent deployments and never exposes the captured provider payload over HTTP.
 */
@Component
@ConditionalOnProperty(name = "weave.chat.e2e-proof.enabled", havingValue = "true")
public final class ChatE2eCallbackReplayTap {

    private final AtomicReference<CapturedCallback> firstCallback = new AtomicReference<>();

    public ChatE2eCallbackReplayTap(ChatE2eProofProperties properties) {
        properties.requiredRunId();
    }

    public boolean captureFirst(String transactionId, byte[] originalPayload) {
        if (transactionId == null || transactionId.isBlank() || originalPayload == null || originalPayload.length == 0) {
            return false;
        }
        return firstCallback.compareAndSet(null, new CapturedCallback(transactionId.trim(), originalPayload));
    }

    public Optional<CapturedCallback> captured() {
        return Optional.ofNullable(firstCallback.get());
    }

    public record CapturedCallback(String transactionId, byte[] payload) {

        public CapturedCallback {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
