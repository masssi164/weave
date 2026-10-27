package com.massimotter.weave.backend.chat.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategorySelectionPolicy;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by chat; shared policy contains no feature catalog. */
public final class ChatDiscoveryMetadata {
    private ChatDiscoveryMetadata() {}

    public static ProviderCategoryFragment chatCategory() {
        return new ProviderCategoryFragment("chat",
                0,
                "chat",
                "chat",
                "chat",
                true,
                null,
                ProviderRealityLevel.LIVE_WRITE,
                Set.of(ProviderModule.MATRIX),
                ProviderCategorySelectionPolicy.boundMetadataOnly(),
                new ProviderCategoryContract(
                    List.of("chat.read", "chat.send", "chat.channels"),
                    List.of("weave-native"),
                    List.of("matrix-synapse", "synapse-homeserver", "microsoft-teams", "slack", "nextcloud-talk"),
                    List.of("WeaveSpace", "WeaveConversation", "WeaveMessage", "WeaveThread", "WeaveReaction", "WeaveAttachment", "WeaveMembership", "WeaveHistoryPolicy", "ProviderRef", "MigrationReceipt", "RollbackReceipt", "LossyFieldReport"),
                    "Weave owns canonical Chat history; optional southbound providers remain private adapters and require their own conformance evidence",
                    List.of("Slack broadcast/thread semantics", "Teams channel permissions", "Matrix E2EE recovery", "rich cards/adaptive blocks", "attachment retention"),
                    "export conversation/message/attachment provenance or document provider export boundary; delete/deprovision follows provider and retention policy",
                    "chat replacement requires preflight, dry-run, membership/history/attachment loss report, and rollback/retention note"));
    }

    public static CanonicalDomainContribution chatDomain() {
        return new CanonicalDomainContribution("chat", 2, CanonicalDomainRegistry.entry(
                "chat",
                "Chat",
                "Conversation, message, membership, history, attachment, and presence facade independent from the selected chat adapter.",
                List.of("WeaveSpace", "WeaveConversation", "WeaveMessage", "WeaveThread", "WeaveReaction", "WeaveAttachment", "WeaveMembership", "WeaveHistoryPolicy", "ProviderRef", "MigrationReceipt", "RollbackReceipt", "LossyFieldReport"),
                List.of(),
                CanonicalDomainRegistry.standardCapabilities("chat"),
                Map.of(
                    "weave-native", "live_write",
                    "matrix-synapse", "release_ready",
                    "synapse-homeserver", "release_ready",
                    "microsoft-teams", "contract_only",
                    "slack", "contract_only",
                    "nextcloud-talk", "contract_only")));
    }
}
