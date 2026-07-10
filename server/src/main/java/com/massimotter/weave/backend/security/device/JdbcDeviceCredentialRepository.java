package com.massimotter.weave.backend.security.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcDeviceCredentialRepository implements DeviceCredentialRepository {

    private static final TypeReference<Set<String>> CAPABILITIES = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDeviceCredentialRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<DeviceCredential> findById(String credentialId) {
        return jdbcTemplate.query(
                        "select * from weave_device_credentials where credential_id = ?",
                        this::map,
                        credentialId)
                .stream()
                .findFirst();
    }

    @Override
    public List<DeviceCredential> findByDomainAndPrincipal(String domain, String principalRef) {
        return jdbcTemplate.query(
                "select * from weave_device_credentials "
                        + "where domain = ? and principal_ref = ? order by issued_at_utc",
                this::map,
                domain,
                principalRef);
    }

    @Override
    public DeviceCredential save(DeviceCredential credential) {
        int updated = jdbcTemplate.update(
                "update weave_device_credentials set revoked_at_utc = ? where credential_id = ?",
                offset(credential.revokedAt()),
                credential.credentialId());
        if (updated == 0) {
            jdbcTemplate.update(
                    "insert into weave_device_credentials "
                            + "(credential_id, domain, tenant_id, principal_ref, subject_ref, username, client_type, "
                            + "label, capabilities_json, secret_hash, issued_at_utc, expires_at_utc, revoked_at_utc) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    credential.credentialId(),
                    credential.domain(),
                    credential.tenantId(),
                    credential.principalRef(),
                    credential.subject(),
                    credential.username(),
                    credential.clientType(),
                    credential.label(),
                    capabilitiesJson(credential.capabilities()),
                    credential.secretHash(),
                    offset(credential.issuedAt()),
                    offset(credential.expiresAt()),
                    offset(credential.revokedAt()));
        }
        return credential;
    }

    private DeviceCredential map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DeviceCredential(
                resultSet.getString("credential_id"),
                resultSet.getString("domain"),
                resultSet.getString("tenant_id"),
                resultSet.getString("principal_ref"),
                resultSet.getString("subject_ref"),
                resultSet.getString("username"),
                resultSet.getString("client_type"),
                resultSet.getString("label"),
                capabilities(resultSet.getString("capabilities_json")),
                resultSet.getString("secret_hash"),
                resultSet.getObject("issued_at_utc", OffsetDateTime.class).toInstant(),
                resultSet.getObject("expires_at_utc", OffsetDateTime.class).toInstant(),
                instant(resultSet.getObject("revoked_at_utc", OffsetDateTime.class)));
    }

    private String capabilitiesJson(Set<String> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities.stream().sorted().toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize device credential capabilities.", exception);
        }
    }

    private Set<String> capabilities(String json) {
        try {
            return objectMapper.readValue(json, CAPABILITIES);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read device credential capabilities.", exception);
        }
    }

    private OffsetDateTime offset(java.time.Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
