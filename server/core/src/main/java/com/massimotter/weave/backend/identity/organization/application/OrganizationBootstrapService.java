package com.massimotter.weave.backend.identity.organization.application;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.identity.OrganizationIdentityContext;
import com.massimotter.weave.backend.identity.organization.port.OrganizationBootstrapRepository;
import com.massimotter.weave.backend.identity.organization.domain.OrganizationBootstrapRecord;
import com.massimotter.weave.backend.identity.organization.api.OrganizationBootstrapRequest;
import com.massimotter.weave.backend.identity.organization.api.OrganizationBootstrapResponse;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.api.error.ApiErrorException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import static com.massimotter.weave.backend.identity.IdentityKeyFormat.MAX_PRIMARY_IDENTITY_KEY_LENGTH;
import static com.massimotter.weave.backend.identity.IdentityKeyFormat.PRIMARY_IDENTITY_KEY_PATTERN;

/** Shared organization bootstrap authority, independent of optional product domains. */
public class OrganizationBootstrapService {
    private static final int MAX_BOOTSTRAP_ADMIN_KEYS = 25;
    private static final int MAX_BOOTSTRAP_ADMIN_KEY_LENGTH = MAX_PRIMARY_IDENTITY_KEY_LENGTH;
    private static final Pattern PRIMARY_IDENTITY_KEY_REGEX = Pattern.compile(PRIMARY_IDENTITY_KEY_PATTERN);
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final OrganizationIdentityContextResolver identityContexts;
    private final OrganizationBootstrapRepository organizationBootstrapRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    @Autowired
    public OrganizationBootstrapService(WorkspaceCapabilityService capabilities,
            OrganizationIdentityContextResolver identityContexts, OrganizationBootstrapRepository repository,
            AuditEventPublisher audit) {
        this(capabilities, identityContexts, repository, audit, Clock.systemUTC());
    }

    OrganizationBootstrapService(WorkspaceCapabilityService capabilities,
            OrganizationIdentityContextResolver identityContexts, OrganizationBootstrapRepository repository,
            AuditEventPublisher audit, Clock clock) {
        this.workspaceCapabilityService = java.util.Objects.requireNonNull(capabilities);
        this.identityContexts = java.util.Objects.requireNonNull(identityContexts);
        this.organizationBootstrapRepository = java.util.Objects.requireNonNull(repository);
        this.auditEventPublisher = java.util.Objects.requireNonNull(audit);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public OrganizationBootstrapResponse bootstrapOrganization(OrganizationBootstrapRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.policy.edit", "admin-control-plane", "bootstrap-organization");
        OrganizationIdentityContext identity = identityContexts.resolve(jwt);
        if (request == null || request.organizationId() == null || request.organizationId().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "organization-bootstrap-invalid",
                    "Organization bootstrap requires an organization id.",
                    Map.of("reason", "organizationId is required"));
        }
        String organizationId = safeOrganizationId(request.organizationId());
        String bootstrapMode = bootstrapMode(request.bootstrapMode());
        List<String> retainedAdmins = retainedAdminPrimaryIdentityKeys(request.adminPrimaryIdentityKeys(), identity.primaryIdentityKey());
        Instant bootstrappedAt = Instant.now(clock);
        OrganizationBootstrapRecord record = organizationBootstrapRepository.save(new OrganizationBootstrapRecord(
                organizationId,
                bootstrapMode,
                identity.primaryIdentityKey(),
                retainedAdmins,
                bootstrappedAt));
        String auditRef = "organization-bootstrap-" + record.organizationId() + "-" + bootstrappedAt.toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                record.organizationId(),
                "admin-control-plane",
                actorRef(jwt),
                "organization-bootstrap",
                AuditAction.ADMIN_POLICY_UPDATED,
                bootstrappedAt,
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "bootstrapMode", record.bootstrapMode(),
                        "actorPrimaryIdentityKey", record.actorPrimaryIdentityKey(),
                        "retainedAdminPrimaryIdentityKeyCount", record.retainedAdminPrimaryIdentityKeys().size(),
                        "lastAdminGuardPassed", true,
                        "supportSafe", true,
                        "emailPrimaryKey", false,
                        "reason", safeText(request.reason()))));
        return new OrganizationBootstrapResponse(
                record.organizationId(),
                record.bootstrapMode(),
                record.actorPrimaryIdentityKey(),
                record.retainedAdminPrimaryIdentityKeys(),
                true,
                true,
                record.bootstrappedAt(),
                List.of(auditRef));
    }

    private String safeOrganizationId(String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "organization-id-invalid",
                    "Organization id must be a support-safe slug.",
                    Map.of("organizationId", "invalid-organization-id-redacted"));
        }
        return trimmed;
    }

    private String bootstrapMode(String value) {
        if (value == null || value.isBlank()) {
            return "existing_org";
        }
        String trimmed = value.trim();
        if (trimmed.equals("existing_org") || trimmed.equals("new_org")) {
            return trimmed;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "organization-bootstrap-mode-invalid",
                "Organization bootstrap mode must be existing_org or new_org.",
                Map.of("bootstrapMode", "invalid-bootstrap-mode-redacted"));
    }

    private List<String> retainedAdminPrimaryIdentityKeys(List<String> suppliedKeys, String actorPrimaryIdentityKey) {
        List<String> supplied = suppliedKeys == null ? List.of() : suppliedKeys;
        List<String> retained = Stream.concat(supplied.stream(), Stream.of(actorPrimaryIdentityKey))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (supplied.size() > MAX_BOOTSTRAP_ADMIN_KEYS || retained.stream().anyMatch(this::unsafeBootstrapAdminKey)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "organization-bootstrap-admin-key-invalid",
                    "Organization bootstrap admin keys must be support-safe immutable issuer+subject keys.",
                    Map.of(
                            "requiredFormat", "issuer+subject:<issuer>#<subject>",
                            "maxSuppliedCount", MAX_BOOTSTRAP_ADMIN_KEYS,
                            "maxLength", MAX_BOOTSTRAP_ADMIN_KEY_LENGTH,
                            "supportSafe", true));
        }
        if (!retained.contains(actorPrimaryIdentityKey)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "last-admin-guard",
                    "Organization bootstrap must retain the current owner/admin as a recovery administrator.",
                    Map.of("supportSafe", true));
        }
        return retained;
    }

    private boolean unsafeBootstrapAdminKey(String value) {
        return value.length() > MAX_BOOTSTRAP_ADMIN_KEY_LENGTH || !PRIMARY_IDENTITY_KEY_REGEX.matcher(value).matches();
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        return value.trim()
                .replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer [redacted]")
                .replaceAll("(?i)xox[baprs]-[A-Za-z0-9-]+", "slack-token-[redacted]")
                .replaceAll("(?i)https?://[^\\s]+", "url-[redacted]")
                .replaceAll("(?i)secret(ref)?://[^\\s]+", "secret-ref-[redacted]");
    }

    private String actorRef(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return "actor:system";
        }
        return "user:" + jwt.getSubject();
    }
}
