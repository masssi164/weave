package com.massimotter.weave.backend.identity.realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class KeycloakRealmDryRunProvider implements IdentityRealmProvider {

    private static final String PROVIDER_KEY = "keycloak-realm";
    private static final Pattern SECRET_LIKE = Pattern.compile(
            "(?i)(password|passwd|secret|token|bearer|api[_-]?key|x-access-token|client_secret)");

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public IdentityRealmDryRunReport dryRun(IdentityRealmDesiredState desiredState) {
        IdentityRealmDesiredState desired = desiredState == null
                ? new IdentityRealmDesiredState(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of("realm desired state is required"))
                : desiredState;
        List<String> blockers = new ArrayList<>(safeList(desired.blockers()));
        if (blank(desired.realmId())) {
            blockers.add("realm id is required before import planning");
        }
        if (desired.clients().isEmpty()) {
            blockers.add("at least one OIDC client must be declared");
        }
        List<String> warnings = new ArrayList<>(safeList(desired.providerWarnings()));
        if (desired.roles().stream().noneMatch(role -> "owner".equals(role) || "admin".equals(role))) {
            warnings.add("owner/admin role baseline is not present in desired realm state");
        }
        List<String> diff = new ArrayList<>();
        diff.add("plan realm " + safe(desired.realmId()));
        diff.add("plan clients=" + desired.clients().size());
        diff.add("plan roles=" + desired.roles().size());
        diff.add("plan scopes=" + desired.scopes().size());
        diff.add("plan claimMappers=" + desired.claimMappers().size());
        desired.clients().stream()
                .map(client -> "client " + safe(client.clientId()) + " redirects=" + safeList(client.redirectOrigins()))
                .forEach(diff::add);
        return new IdentityRealmDryRunReport(
                providerKey(),
                safe(desired.realmId()),
                "dry-run",
                blockers.isEmpty() ? "ready-for-admin-review" : "blocked-until-realm-contract-is-complete",
                destructiveApplyAvailable(),
                true,
                false,
                diff,
                warnings,
                blockers,
                List.of(
                        "Review desired realm diff in the Admin Console boundary.",
                        "Store provider credentials only as SecretRef values before any future gated apply.",
                        "Keep destructive realm apply disabled until audit, rollback, and last-admin guards are proven."));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::safe).toList();
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "missing";
        }
        String trimmed = value.trim();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if (SECRET_LIKE.matcher(lowered).find() || lowered.contains("@github.com/") || lowered.contains("x-access-token:")) {
            return "redacted-secret-like-value";
        }
        return trimmed;
    }
}
