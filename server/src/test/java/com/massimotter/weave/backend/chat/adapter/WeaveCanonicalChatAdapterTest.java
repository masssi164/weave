package com.massimotter.weave.backend.chat.adapter;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatCursor;
import com.massimotter.weave.backend.chat.domain.ChatTransactionId;
import com.massimotter.weave.backend.chat.domain.ConversationId;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        ProviderConformanceProfile profile = new WeaveCanonicalChatAdapter().conformanceProfile();

        assertThat(profile.domain()).isEqualTo("chat");
        assertThat(profile.supportedOperations())
                .contains("joined-conversations", "timeline", "send", "changes", "idempotent-send");
        assertThat(profile.fieldMappings())
                .containsEntry("conversation", ProviderConformanceProfile.MappingClass.PORTABLE)
                .containsEntry("attachment", ProviderConformanceProfile.MappingClass.ARCHIVE_ONLY)
                .containsEntry("encrypted-history", ProviderConformanceProfile.MappingClass.UNSUPPORTED);
        assertThat(profile.atomicWrites()).isTrue();
        assertThat(profile.stableVersionTokens()).isTrue();
        assertThat(profile.supportSafe()).isTrue();
    }

    @Test
    void northboundIdentifiersRemainCanonicalAndProviderNeutral() {
        WeaveCanonicalChatAdapter adapter = new WeaveCanonicalChatAdapter();

        var joined = adapter.joinedConversations(actor).conversations().getFirst();

        assertThat(joined.conversationId()).isEqualTo("channel-general");
        assertThat(joined.encryptionState().mode()).isEqualTo("unencrypted");
        assertThat(joined.toString())
                .doesNotContain("Synapse", "Slack", "Teams", "access_token", "homeserver");
    }
}
