package com.massimotter.weave.backend.chat.provider.weave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeChatProviderAdapterTest {

    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-08-08T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void requiresDurableCanonicalJpaAuthority() {
        CanonicalChatStore store = mock(CanonicalChatStore.class);
        when(store.persistencePosture()).thenReturn("in-memory-test");

        assertThatThrownBy(() -> new NativeChatProviderAdapter(store, FIXED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durable canonical JPA");
    }

    @Test
    void createsConversationThroughProviderFreeCommitOnly() {
        CanonicalChatStore store = durableStore();
        ChatRequestContext context = context();
        ChatTransactionId transactionId = new ChatTransactionId("native-create");
        ConversationId conversationId = new ConversationId("room-native");
        CanonicalChatStore.PreparedConversation prepared = new CanonicalChatStore.PreparedConversation(
                "operation-native",
                conversationId,
                NativeChatProviderAdapter.PROVIDER_KEY,
                "internal-operation-token",
                null,
                List.of(),
                "unencrypted",
                false);
        ChatConversation committed = mock(ChatConversation.class);
        when(store.prepareConversation(
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                anyString(),
                isNull(),
                any())).thenReturn(prepared);
        when(store.commitConversation(context, prepared)).thenReturn(committed);

        NativeChatProviderAdapter adapter = new NativeChatProviderAdapter(store, FIXED);
        assertThat(adapter.createConversation(
                context,
                transactionId,
                "Native",
                "channel",
                List.of(),
                ChatEncryptionState.unencrypted())).isSameAs(committed);

        verify(store).commitConversation(context, prepared);
        verify(store, never()).acknowledgeConversation(
                any(), any(), anyString(), anyString(), anyString());
        assertThat(adapter.providerKey()).isEqualTo("weave-native");
    }

    @Test
    void advertisesOnlyCanonicalNativeCapabilities() {
        NativeChatProviderAdapter adapter = new NativeChatProviderAdapter(durableStore(), FIXED);

        assertThat(adapter.readiness().available()).isTrue();
        assertThat(adapter.conformanceProfile().adapterKey()).isEqualTo("weave-native");
        assertThat(adapter.conformanceProfile().supportedOperations())
                .contains("idempotent-send", "opaque-encrypted-events")
                .doesNotContain("callback-deduplication");
        assertThat(adapter.conformanceProfile().fieldMappings())
                .containsEntry("encrypted-history", ProviderConformanceProfile.MappingClass.PORTABLE)
                .containsEntry("attachment", ProviderConformanceProfile.MappingClass.ARCHIVE_ONLY);
    }

    private CanonicalChatStore durableStore() {
        CanonicalChatStore store = mock(CanonicalChatStore.class);
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-code-first");
        return store;
    }

    private ChatRequestContext context() {
        return new ChatRequestContext(
                "tenant-native",
                "workspace-native",
                "https://auth.weave.test/realms/weave",
                new ChatActorRef("user:native-author"),
                "native-author");
    }
}
