package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.AuthenticatedUserResponse;
import com.massimotter.weave.backend.model.ModuleSyncStatusResponse;
import com.massimotter.weave.backend.model.ProfileReadinessResponse;
import com.massimotter.weave.backend.model.ProductProfileResponse;
import com.massimotter.weave.backend.model.UpdateProductProfileRequest;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class ProductProfileService {

    private static final ModuleSyncStatusResponse NOT_CONFIGURED_SYNC =
            new ModuleSyncStatusResponse("not_configured", "not_configured");

    private final ProductProfileOverrideRepository profileRepository;

    public ProductProfileService(ProductProfileOverrideRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public AuthenticatedUserResponse authenticatedUser(Jwt jwt) {
        ProductProfileSnapshot snapshot = snapshot(jwt);
        OrganizationIdentityContext identity = snapshot.identity();
        return new AuthenticatedUserResponse(
                identity.accountId(),
                snapshot.username(),
                snapshot.email(),
                snapshot.emailVerified(),
                snapshot.displayName(),
                snapshot.locale(),
                snapshot.timezone(),
                identity.roles(),
                identity.groups(),
                issuedFor(jwt),
                jwt.getAudience(),
                identity.organizationId(),
                identity.issuer(),
                identity.subject(),
                identity.primaryIdentityKey(),
                identity.accountId(),
                identity.contextRoles(),
                identity.providerRoleMappings(),
                false,
                snapshot.moduleSyncStatus());
    }

    public ProductProfileResponse profile(Jwt jwt) {
        return snapshot(jwt).toResponse();
    }

    public ProfileReadinessResponse readiness(Jwt jwt) {
        requireSubject(jwt);
        return new ProfileReadinessResponse(
                "CEFACADE",
                "/profile/readiness",
                true,
                false,
                true,
                "ready",
                List.of(
                        "direct-frontend-keycloak-admin",
                        "direct-flutter-nextcloud-profile-api",
                        "credential-export",
                        "raw-provider-errors"));
    }

    public ProductProfileResponse update(Jwt jwt, UpdateProductProfileRequest request) {
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(jwt);
        validateRequest(request);
        try {
            profileRepository.save(identity.primaryIdentityKey(), merge(profileRepository.findBySubject(identity.primaryIdentityKey()), request));
        } catch (ProductProfileStoreException exception) {
            throw new ApiErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "profile-persistence-error",
                    "Product profile update could not be persisted.",
                    Map.of("store", "profile-overrides"));
        }
        return profile(jwt);
    }

    public ModuleSyncStatusResponse syncStatus(Jwt jwt) {
        requireSubject(jwt);
        return NOT_CONFIGURED_SYNC;
    }

    private ProductProfileSnapshot snapshot(Jwt jwt) {
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(jwt);
        String username = firstText(jwt.getClaimAsString("preferred_username"), identity.subject());
        ProductProfileOverride stored = profileRepository.findBySubject(identity.primaryIdentityKey());
        String displayName = stored != null && hasText(stored.displayName())
                ? stored.displayName()
                : firstText(jwt.getClaimAsString("name"), username);
        String locale = stored != null && hasText(stored.locale())
                ? stored.locale()
                : firstText(jwt.getClaimAsString("locale"), "en");
        String timezone = stored != null && hasText(stored.timezone())
                ? stored.timezone()
                : firstText(jwt.getClaimAsString("timezone"), "UTC");
        String avatar = stored != null && hasText(stored.avatar())
                ? stored.avatar()
                : firstText(jwt.getClaimAsString("picture"), null);
        Map<String, String> accessibilityPreferences = stored == null
                ? Map.of()
                : stored.accessibilityPreferences();
        String profileVisibility = stored != null && hasText(stored.profileVisibility())
                ? stored.profileVisibility()
                : "workspace";

        return new ProductProfileSnapshot(
                identity,
                username,
                jwt.getClaimAsString("email"),
                emailVerified(jwt),
                displayName,
                avatar,
                locale,
                timezone,
                accessibilityPreferences,
                profileVisibility,
                NOT_CONFIGURED_SYNC);
    }

    private ProductProfileOverride merge(ProductProfileOverride existing, UpdateProductProfileRequest request) {
        return new ProductProfileOverride(
                valueOrExisting(request.displayName(), existing == null ? null : existing.displayName()),
                valueOrExisting(request.avatar(), existing == null ? null : existing.avatar()),
                valueOrExisting(request.locale(), existing == null ? null : existing.locale()),
                valueOrExisting(request.timezone(), existing == null ? null : existing.timezone()),
                request.accessibilityPreferences() == null
                        ? existing == null ? Map.of() : existing.accessibilityPreferences()
                        : sanitizedMap(request.accessibilityPreferences()),
                valueOrExisting(request.profileVisibility(), existing == null ? "workspace" : existing.profileVisibility()));
    }

    private void validateRequest(UpdateProductProfileRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (request.displayName() != null && !hasText(request.displayName())) {
            fields.put("displayName", "must not be blank when supplied");
        }
        if (request.avatar() != null && !hasText(request.avatar())) {
            fields.put("avatar", "must not be blank when supplied");
        }
        if (request.locale() != null && !hasText(request.locale())) {
            fields.put("locale", "must not be blank when supplied");
        }
        if (request.timezone() != null) {
            if (!hasText(request.timezone())) {
                fields.put("timezone", "must not be blank when supplied");
            } else if (!validTimezone(request.timezone())) {
                fields.put("timezone", "must be a valid IANA timezone");
            }
        }
        if (request.accessibilityPreferences() != null) {
            request.accessibilityPreferences().forEach((key, value) -> {
                if (!hasText(key)) {
                    fields.put("accessibilityPreferences", "keys must not be blank");
                }
                if (value == null) {
                    fields.put("accessibilityPreferences." + key, "values must not be null");
                }
            });
        }

        if (!fields.isEmpty()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "validation-error",
                    "Request validation failed.",
                    Map.of("fields", fields));
        }
    }

    private boolean validTimezone(String timezone) {
        try {
            ZoneId.of(timezone.trim());
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private Map<String, String> sanitizedMap(Map<String, String> values) {
        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sanitized.put(entry.getKey().trim(), entry.getValue().trim()));
        return Map.copyOf(sanitized);
    }

    private String requireSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (!hasText(subject)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "missing-subject",
                    "Authenticated token is missing a stable subject.",
                    Map.of("claim", "sub"));
        }
        return subject;
    }

    private String issuedFor(Jwt jwt) {
        return Stream.of(jwt.getClaimAsString("azp"), jwt.getClaimAsString("client_id"))
                .filter(this::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private boolean emailVerified(Jwt jwt) {
        Object value = jwt.getClaims().get("email_verified");
        return value instanceof Boolean verified && verified;
    }

    private String valueOrExisting(String supplied, String existing) {
        return supplied == null ? existing : supplied.trim();
    }

    private String firstText(String primary, String fallback) {
        if (hasText(primary)) {
            return primary.trim();
        }
        return fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ProductProfileSnapshot(
            OrganizationIdentityContext identity,
            String username,
            String email,
            boolean emailVerified,
            String displayName,
            String avatar,
            String locale,
            String timezone,
            Map<String, String> accessibilityPreferences,
            String profileVisibility,
            ModuleSyncStatusResponse moduleSyncStatus) {

        ProductProfileResponse toResponse() {
            return new ProductProfileResponse(
                    identity.accountId(),
                    username,
                    email,
                    emailVerified,
                    displayName,
                    avatar,
                    locale,
                    timezone,
                    accessibilityPreferences,
                    profileVisibility,
                    moduleSyncStatus);
        }
    }
}
