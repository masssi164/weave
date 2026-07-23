package com.massimotter.weave.backend.identity.invitation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProvisioningIntentRepository implements ProvisioningIntentRepository {
    private final Map<UUID, ProvisioningIntent> intents = new ConcurrentHashMap<>();
    private final Map<String, Instant> receivedEvents = new ConcurrentHashMap<>();

    @Override public ProvisioningIntent save(ProvisioningIntent intent) { intents.put(intent.intentId(), intent); return intent; }
    @Override public Optional<ProvisioningIntent> findById(UUID id) { return Optional.ofNullable(intents.get(id)); }
    @Override public Optional<ProvisioningIntent> findByProviderInvitationId(String id) {
        return intents.values().stream().filter(i -> id.equals(i.providerInvitationId())).findFirst();
    }
    @Override public List<ProvisioningIntent> findPendingByEmail(String tenant, String organization, String email) {
        return intents.values().stream()
                .filter(i -> i.tenantId().equals(tenant) && i.organizationId().equals(organization))
                .filter(i -> i.invitedEmail().equalsIgnoreCase(email) && i.status() == ProvisioningIntentStatus.PENDING)
                .sorted(Comparator.comparing(ProvisioningIntent::createdAt).reversed()).toList();
    }
    @Override public List<ProvisioningIntent> findPendingByEmailHash(String organization, String hash) {
        return intents.values().stream()
                .filter(i -> i.organizationId().equals(organization) && i.invitedEmailSha256().equals(hash))
                .filter(i -> i.status() == ProvisioningIntentStatus.PENDING)
                .sorted(Comparator.comparing(ProvisioningIntent::createdAt).reversed()).toList();
    }
    @Override public boolean recordEventOnce(String eventId, Instant occurredAt) {
        return receivedEvents.putIfAbsent(eventId, occurredAt) == null;
    }
}
