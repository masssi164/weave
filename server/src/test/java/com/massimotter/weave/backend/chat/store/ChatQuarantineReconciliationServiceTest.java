package com.massimotter.weave.backend.chat.store;

import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseChatSouthboundAdapter;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatQuarantineReconciliationServiceTest {

    @Test
    void scheduledPassUsesOneBoundedProviderScopedBatch() {
        CanonicalChatStore store = mock(CanonicalChatStore.class);
        MatrixSynapseChatSouthboundAdapter provider = mock(MatrixSynapseChatSouthboundAdapter.class);
        when(provider.providerKey()).thenReturn("matrix-synapse");

        new ChatQuarantineReconciliationService(store, provider).reconcilePending();

        verify(store).reconcilePendingQuarantines(
                "matrix-synapse", ChatQuarantineReconciliationService.BATCH_LIMIT);
    }
}
