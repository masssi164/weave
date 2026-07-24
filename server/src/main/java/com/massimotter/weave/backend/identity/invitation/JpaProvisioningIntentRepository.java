package com.massimotter.weave.backend.identity.invitation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/**
 * JPA adapter for invite-first provisioning work state.
 *
 * <p>The intent is versioned in place; the event receipt is an immutable deduplication ledger.
 * Neither is an organization-membership authorization source.
 */
@Repository
@Transactional(readOnly = true)
public class JpaProvisioningIntentRepository implements ProvisioningIntentRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final ProvisioningIntentJpaRepository intents;
    private final ObjectMapper objectMapper;

    public JpaProvisioningIntentRepository(
            ProvisioningIntentJpaRepository intents,
            ObjectMapper objectMapper) {
        this.intents = requireNonNull(intents, "intents");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @Transactional
    public ProvisioningIntent save(ProvisioningIntent intent) {
        ProvisioningIntent requested = requireNonNull(intent, "intent");
        String groupsJson = groupsJson(requested.organizationGroups());
        ProvisioningIntentJpaEntity entity = intents.findById(requested.intentId())
                .orElseGet(() -> ProvisioningIntentJpaEntity.create(requested, groupsJson));
        entity.apply(requested, groupsJson);
        return intents.saveAndFlush(entity).toDomain(this::groups);
    }

    @Override
    public Optional<ProvisioningIntent> findById(UUID id) {
        return intents.findById(requireNonNull(id, "id"))
                .map(entity -> entity.toDomain(this::groups));
    }

    @Override
    public Optional<ProvisioningIntent> findByProviderInvitationId(String id) {
        return intents.findByProviderInvitationId(requireNonNull(id, "id"))
                .map(entity -> entity.toDomain(this::groups));
    }

    @Override
    public List<ProvisioningIntent> findPendingByEmail(
            String tenant,
            String organization,
            String email) {
        return intents
                .findByTenantIdAndOrganizationIdAndInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        tenant,
                        organization,
                        email,
                        ProvisioningIntentStatus.PENDING)
                .stream()
                .map(entity -> entity.toDomain(this::groups))
                .toList();
    }

    private String groupsJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize organization groups", exception);
        }
    }

    private List<String> groups(String value) {
        try {
            return objectMapper.readValue(value, STRINGS);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not read organization groups", exception);
        }
    }
}
