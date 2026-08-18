package com.massimotter.weave.backend.chat.store;

import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseChatSouthboundAdapter;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Runs bounded private recovery after a versioned callback classifier changes. */
@Service
@ConditionalOnProperty(name = "weave.chat.provider", havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
public final class ChatQuarantineReconciliationService {

    static final int BATCH_LIMIT = 25;

    private final CanonicalChatStore store;
    private final MatrixSynapseChatSouthboundAdapter provider;

    public ChatQuarantineReconciliationService(
            CanonicalChatStore store,
            MatrixSynapseChatSouthboundAdapter provider) {
        this.store = store;
        this.provider = provider;
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 60_000)
    public void reconcilePending() {
        store.reconcilePendingQuarantines(provider.providerKey(), BATCH_LIMIT);
    }
}
