package com.massimotter.weave.backend.identity.invitation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProvisioningIntentRepository implements ProvisioningIntentRepository {
    private final Map<UUID, ProvisioningIntent> intents = new ConcurrentHashMap<>();

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
    @Override public List<ProvisioningIntent> findPendingByActor(
            String tenant, String organization, String invitedByIssuer, String invitedBySubject) {
        return intents.values().stream()
                .filter(i -> i.tenantId().equals(tenant) && i.organizationId().equals(organization))
                .filter(i -> i.invitedByIssuer().equals(invitedByIssuer))
                .filter(i -> i.invitedBySubject().equals(invitedBySubject))
                .filter(i -> i.status() == ProvisioningIntentStatus.PENDING)
                .sorted(Comparator.comparing(ProvisioningIntent::createdAt).reversed()).toList();
    }
}
