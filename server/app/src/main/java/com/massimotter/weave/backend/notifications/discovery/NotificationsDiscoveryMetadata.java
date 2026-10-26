package com.massimotter.weave.backend.notifications.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import java.util.List;
import java.util.Map;

/** Existing unimplemented notification contract metadata only; no delivery service or readiness is implied. */
public final class NotificationsDiscoveryMetadata {
    private NotificationsDiscoveryMetadata() {}

    public static CanonicalDomainContribution notificationsDomain() {
        return new CanonicalDomainContribution("notifications", 9, CanonicalDomainRegistry.entry(
                "notifications",
                "Notifications",
                "Provider-switch-safe notifications, action requests, approval requests, digests, and read state.",
                List.of("Notification", "Preference", "Channel", "Digest", "DeliveryAttempt", "Subscription"),
                List.of(),
                CanonicalDomainRegistry.standardCapabilities("notifications"),
                Map.of(
                    "weave-notifications", "contract_only",
                    "email-smtp", "contract_only",
                    "push", "contract_only",
                    "matrix-notifications", "contract_only",
                    "slack-notifications", "contract_only",
                    "teams-notifications", "contract_only",
                    "webhook", "contract_only")));
    }
}
