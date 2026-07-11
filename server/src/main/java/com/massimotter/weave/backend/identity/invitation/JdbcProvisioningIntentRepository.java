package com.massimotter.weave.backend.identity.invitation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "weave.identity.invitations.storage-mode", havingValue = "jdbc")
public class JdbcProvisioningIntentRepository implements ProvisioningIntentRepository {
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProvisioningIntentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override public ProvisioningIntent save(ProvisioningIntent i) {
        int updated = jdbc.update("""
                update weave_identity_provisioning_intents set provider_invitation_id=?, status=?, applied_subject=?,
                  failure_code=?, expires_at=?, updated_at=? where intent_id=?
                """, i.providerInvitationId(), i.status().name(), i.appliedSubject(), i.failureCode(),
                time(i.expiresAt()), time(i.updatedAt()), i.intentId());
        if (updated == 0) jdbc.update("""
                insert into weave_identity_provisioning_intents
                  (intent_id,tenant_id,organization_id,invited_email,invited_email_sha256,requested_role,
                   organization_groups,provider_invitation_id,invited_by_issuer,invited_by_subject,audit_correlation,
                   status,applied_subject,failure_code,expires_at,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, i.intentId(), i.tenantId(), i.organizationId(), i.invitedEmail(), i.invitedEmailSha256(),
                i.requestedRole(), json(i.organizationGroups()), i.providerInvitationId(), i.invitedByIssuer(),
                i.invitedBySubject(), i.auditCorrelation(), i.status().name(), i.appliedSubject(), i.failureCode(),
                time(i.expiresAt()), time(i.createdAt()), time(i.updatedAt()));
        return i;
    }
    @Override public Optional<ProvisioningIntent> findById(UUID id) { return one("intent_id=?", id); }
    @Override public Optional<ProvisioningIntent> findByProviderInvitationId(String id) { return one("provider_invitation_id=?", id); }
    @Override public List<ProvisioningIntent> findPendingByEmail(String tenant, String org, String email) {
        return query("tenant_id=? and organization_id=? and lower(invited_email)=lower(?) and status='PENDING'", tenant, org, email);
    }
    @Override public List<ProvisioningIntent> findPendingByEmailHash(String org, String hash) {
        return query("organization_id=? and invited_email_sha256=? and status='PENDING'", org, hash);
    }
    @Override public boolean recordEventOnce(String eventId, java.time.Instant occurredAt) {
        try {
            jdbc.update("insert into weave_keycloak_event_receipts (event_id, occurred_at, received_at) values (?,?,?)",
                    eventId, time(occurredAt), time(java.time.Instant.now()));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
    private Optional<ProvisioningIntent> one(String where, Object value) { return query(where, value).stream().findFirst(); }
    private List<ProvisioningIntent> query(String where, Object... args) {
        return jdbc.query("select * from weave_identity_provisioning_intents where " + where + " order by created_at desc", this::map, args);
    }
    private ProvisioningIntent map(ResultSet r, int row) throws SQLException {
        return new ProvisioningIntent(r.getObject("intent_id", UUID.class), r.getString("tenant_id"),
                r.getString("organization_id"), r.getString("invited_email"), r.getString("invited_email_sha256"),
                r.getString("requested_role"), strings(r.getString("organization_groups")),
                r.getString("provider_invitation_id"), r.getString("invited_by_issuer"), r.getString("invited_by_subject"),
                r.getString("audit_correlation"), ProvisioningIntentStatus.valueOf(r.getString("status")),
                r.getString("applied_subject"), r.getString("failure_code"), instant(r, "expires_at"),
                instant(r, "created_at"), instant(r, "updated_at"));
    }
    private String json(List<String> values) { try { return objectMapper.writeValueAsString(values); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize organization groups", e); } }
    private List<String> strings(String value) { try { return objectMapper.readValue(value, STRINGS); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not read organization groups", e); } }
    private OffsetDateTime time(java.time.Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
    private java.time.Instant instant(ResultSet r, String column) throws SQLException {
        OffsetDateTime value = r.getObject(column, OffsetDateTime.class); return value == null ? null : value.toInstant();
    }
}
