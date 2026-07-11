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
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "weave.identity.invitations.storage-mode", havingValue = "jdbc")
public class JdbcMemberInvitationRepository implements MemberInvitationRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcMemberInvitationRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public MemberInvitation save(MemberInvitation invitation) {
        int updated = jdbc.update("""
                update weave_member_invitations set
                  status = ?, provider_invitation_id = ?, expires_at = ?, updated_at = ?
                 where invitation_id = ?
                """, invitation.status().name(), invitation.providerInvitationId(), timestamp(invitation.expiresAt()),
                timestamp(invitation.updatedAt()), invitation.invitationId());
        if (updated == 0) {
            jdbc.update("""
                insert into weave_member_invitations (
                  invitation_id, tenant_id, organization_id, invited_email, display_name,
                  requested_role, workspace_ids, status, provider_invitation_id,
                  invited_by_subject, expires_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                invitation.invitationId(), invitation.tenantId(), invitation.organizationId(), invitation.invitedEmail(),
                invitation.displayName(), invitation.requestedRole(), json(invitation.workspaceIds()), invitation.status().name(),
                invitation.providerInvitationId(), invitation.invitedBySubject(), timestamp(invitation.expiresAt()),
                timestamp(invitation.createdAt()), timestamp(invitation.updatedAt()));
        }
        return invitation;
    }

    @Override
    public Optional<MemberInvitation> findById(UUID invitationId) {
        return jdbc.query("select * from weave_member_invitations where invitation_id = ?", this::map, invitationId)
                .stream().findFirst();
    }

    @Override
    public Optional<MemberInvitation> findPendingByEmail(String tenantId, String organizationId, String email) {
        return jdbc.query("""
                select * from weave_member_invitations
                 where tenant_id = ? and organization_id = ? and lower(invited_email) = lower(?)
                   and status in ('PENDING', 'SENT')
                 order by created_at desc limit 1
                """, this::map, tenantId, organizationId, email).stream().findFirst();
    }

    @Override
    public List<MemberInvitation> findByOrganization(String tenantId, String organizationId) {
        return jdbc.query("""
                select * from weave_member_invitations
                 where tenant_id = ? and organization_id = ? order by created_at desc
                """, this::map, tenantId, organizationId);
    }

    @Override
    public void markApplied(MemberInvitation invitation) {
        save(invitation);
    }

    private MemberInvitation map(ResultSet result, int row) throws SQLException {
        return new MemberInvitation(
                result.getObject("invitation_id", UUID.class),
                result.getString("tenant_id"), result.getString("organization_id"),
                result.getString("invited_email"), result.getString("display_name"),
                result.getString("requested_role"), strings(result.getString("workspace_ids")),
                MemberInvitationStatus.valueOf(result.getString("status")),
                result.getString("provider_invitation_id"), result.getString("invited_by_subject"),
                result.getObject("expires_at", OffsetDateTime.class).toInstant(),
                result.getObject("created_at", OffsetDateTime.class).toInstant(),
                result.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize invitation workspace assignments", exception);
        }
    }

    private List<String> strings(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read invitation workspace assignments", exception);
        }
    }

    private OffsetDateTime timestamp(java.time.Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
