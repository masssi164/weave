package com.massimotter.weave.backend.chat.adapter;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeaveCanonicalChatAdapterTest {

    private final ChatActorRef actor = new ChatActorRef("user:subject-1");
    private final ConversationId conversation = new ConversationId("channel-general");

    @Test
    void repeatedTransactionReturnsSameCanonicalMessageAndSingleChange() {
        WeaveCanonicalChatAdapter adapter = new WeaveCanonicalChatAdapter();
        ChatCursor before = adapter.currentCursor(actor);

        var first = adapter.send(
                actor,
                conversation,
                new ChatTransactionId("txn-1"),
                "Hello through the canonical port");
        var replay = adapter.send(
                actor,
                conversation,
                new ChatTransactionId("txn-1"),
                "Hello through the canonical port");

        assertThat(replay).isEqualTo(first);
        assertThat(adapter.timeline(actor, conversation, null, 100).messages())
                .filteredOn(message -> message.messageId().equals(first.messageId()))
                .hasSize(1);
        assertThat(adapter.changes(actor, before, 100).changes())
                .extracting(change -> change.messageId())
                .containsExactly(first.messageId());
    }

    @Test
    void conformanceAccountsForPortableLossyAndUnsupportedChatSemantics() {
        // MATRIX_E2EE_PROVIDER_SWITCH_BLOCKED
        ProviderConformanceProfile profile = new WeaveCanonicalChatAdapter().conformanceProfile();

        assertThat(profile.domain()).isEqualTo("chat");
        assertThat(profile.supportedOperations())
                .contains(
                        "joined-conversations",
                        "timeline",
                        "send",
                        "reactions",
                        "read-receipts",
                        "typing",
                        "changes",
                        "idempotent-send");
        assertThat(profile.fieldMappings())
                .containsEntry("conversation", ProviderConformanceProfile.MappingClass.PORTABLE)
                .containsEntry("reaction", ProviderConformanceProfile.MappingClass.PORTABLE)
                .containsEntry("read-receipt", ProviderConformanceProfile.MappingClass.PORTABLE)
                .containsEntry("attachment", ProviderConformanceProfile.MappingClass.ARCHIVE_ONLY)
                .containsEntry("encrypted-history", ProviderConformanceProfile.MappingClass.UNSUPPORTED);
        assertThat(profile.atomicWrites()).isTrue();
        assertThat(profile.stableVersionTokens()).isTrue();
        assertThat(profile.supportSafe()).isTrue();
    }

    @Test
    void encryptedRoomRejectsPlaintextAndPreservesOnlyOpaqueEnvelope() {
        WeaveCanonicalChatAdapter adapter = new WeaveCanonicalChatAdapter();
        adapter.enableEncryption(actor, conversation, ChatEncryptedEnvelope.MEGOLM_V1);
        Map<String, Object> envelope = Map.of(
                "algorithm", ChatEncryptedEnvelope.MEGOLM_V1,
                "ciphertext", "opaque-ciphertext",
                "sender_key", "curve25519:alice",
                "session_id", "megolm-session-1",
                "device_id", "WEAVEDEVICEALICE");

        var encrypted = adapter.sendEvent(
                actor,
                conversation,
                new ChatTransactionId("txn-encrypted"),
                ChatEventContent.encrypted(envelope));

        assertThat(encrypted.content().body()).isNull();
        assertThat(encrypted.content().encryptedEnvelope().content()).isEqualTo(envelope);
        assertThat(adapter.conversation(actor, conversation).encryptionState().serverMayReadContent()).isFalse();
        assertThatThrownBy(() -> adapter.send(
                actor,
                conversation,
                new ChatTransactionId("txn-plaintext"),
                "plaintext must fail"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaintext Chat events are forbidden");
    }

    @Test
    void roomEncryptionCannotBeDisabledOrChanged() {
        WeaveCanonicalChatAdapter adapter = new WeaveCanonicalChatAdapter();
        adapter.enableEncryption(actor, conversation, ChatEncryptedEnvelope.MEGOLM_V1);

        assertThatThrownBy(() -> adapter.enableEncryption(actor, conversation, "m.weave.unsupported"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
        assertThat(adapter.conversation(actor, conversation).encryptionState().encrypted()).isTrue();
    }

    @Test
    void northboundIdentifiersRemainCanonicalAndProviderNeutral() {
        WeaveCanonicalChatAdapter adapter = new WeaveCanonicalChatAdapter();

        var joined = adapter.joinedConversations(actor).conversations().getFirst();

        assertThat(joined.conversationId()).isEqualTo("channel-general");
        assertThat(joined.encryptionState().mode()).isEqualTo("unencrypted");
        assertThat(joined.toString())
                .doesNotContain("Synapse", "providerTenant", "providerChannelId", "access_token", "homeserver");
    }

    @Test
    void readReceiptsAndTypingRemainCanonicalUserState() {
        WeaveCanonicalChatAdapter adapter = new WeaveCanonicalChatAdapter();
        var event = adapter.send(
                actor,
                conversation,
                new ChatTransactionId("txn-read-state"),
                "Read me");

        var receipt = adapter.markRead(actor, conversation, event.messageId());
        var typing = adapter.setTyping(actor, conversation, true, 15_000);
        var stopped = adapter.setTyping(actor, conversation, false, 0);

        assertThat(receipt.conversationId()).isEqualTo("channel-general");
        assertThat(receipt.actorRef()).isEqualTo("user:subject-1");
        assertThat(receipt.eventId()).isEqualTo(event.messageId());
        assertThat(typing.typing()).isTrue();
        assertThat(typing.expiresAt()).isAfter(Instant.now());
        assertThat(stopped.typing()).isFalse();
    }
}
