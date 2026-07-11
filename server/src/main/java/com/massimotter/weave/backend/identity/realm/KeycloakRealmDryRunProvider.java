package com.massimotter.weave.backend.identity.realm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KeycloakRealmDryRunProvider implements IdentityRealmProvider {

    private static final String PROVIDER_KEY = "keycloak-realm";
    // Realm-operation service accounts may still use the operator role. It is not a weave-app member role.
    private static final Set<String> KNOWN_ROLES = Set.of("owner", "admin", "operator", "member", "guest");
    private static final Set<String> KNOWN_SCOPES = Set.of("openid", "profile", "email", "weave:workspace", "offline_access");
    private static final Set<String> KNOWN_GROUPS = Set.of(
            "weaver-group",
            "weave-calendar-editors",
            "weave-board-editors",
            "weave-meeting-hosts",
            "weave-document-editors",
            "weave-decision-recorders",
            "weave-weaver-pilot");
    private static final Set<String> KNOWN_FEATURES = Set.of(
            "chat", "files", "calendar", "boards", "meetings", "documents", "decisions", "manuals", "release-evidence", "weaver");
    private static final Pattern SECRET_LIKE = Pattern.compile(
            "(?i)(password|passwd|secret|token|bearer|api[_-]?key|x-access-token|client_secret|credential|private[_-]?key)");

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public IdentityRealmDryRunReport dryRun(IdentityRealmDryRunRequest request) {
        IdentityRealmDesiredState desired = request == null ? null : request.desiredState();
        IdentityRealmDesiredState current = request == null ? null : request.currentState();
        if (desired == null) {
            desired = new IdentityRealmDesiredState(
                    null, null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), "sub",
                    List.of(), List.of("realm desired state is required"));
        }

        NormalizedRealm desiredRealm = normalize(desired);
        NormalizedRealm currentRealm = current == null ? NormalizedRealm.empty() : normalize(current);
        List<String> blockers = new ArrayList<>(desiredRealm.blockers);
        List<String> warnings = new ArrayList<>(desiredRealm.warnings);
        List<IdentityRealmDryRunReport.ChangeRecord> changes = new ArrayList<>();

        validateRequiredContract(desiredRealm, blockers, warnings);
        validateIdentitySafeguards(desired, blockers, warnings);
        Set<String> unsafeFeatureMappingKeys = unsafeFeatureMappingKeys(desired, desiredRealm, blockers);
        compareRealmBasics(currentRealm, desiredRealm, current != null, changes);
        compareCollection("roles", currentRealm.roles, desiredRealm.roles, KNOWN_ROLES, blockers, changes);
        compareCollection("groups", currentRealm.groups, desiredRealm.groups, KNOWN_GROUPS, blockers, changes);
        compareCollection("scopes", currentRealm.scopes, desiredRealm.scopes, KNOWN_SCOPES, blockers, changes);
        compareRootRedirects(currentRealm.redirectOrigins, desiredRealm.redirectOrigins, changes, warnings);
        compareClients(currentRealm.clients, desiredRealm.clients, current != null, desiredRealm.roles, desiredRealm.scopes, blockers, changes, warnings);
        compareClaimMappers(currentRealm.claimMappers, desiredRealm.claimMappers, current != null, changes);
        compareFeatureMappings(currentRealm.featureMappings, desiredRealm.featureMappings, current != null, unsafeFeatureMappingKeys, blockers, changes);

        changes.sort(Comparator.comparing(IdentityRealmDryRunReport.ChangeRecord::path)
                .thenComparing(IdentityRealmDryRunReport.ChangeRecord::action)
                .thenComparing(IdentityRealmDryRunReport.ChangeRecord::reasonCode));

        boolean hasRisky = changes.stream().anyMatch(change -> "risky".equals(change.classification()));
        boolean hasDestructive = changes.stream().anyMatch(change -> "destructive".equals(change.classification()));
        boolean hasUnknown = blockers.stream().anyMatch(value -> value.contains("deny by default") || value.contains("unknown"));
        if (hasDestructive) {
            blockers.add("destructive realm changes are blocked in this dry-run-only slice");
        }

        String readiness = readiness(distinct(blockers), hasRisky, hasDestructive, hasUnknown);
        List<IdentityRealmDryRunReport.ReadinessCheck> checks = readinessChecks(readiness, distinct(blockers), hasRisky, hasDestructive, hasUnknown);
        List<String> diff = deterministicDiff(desiredRealm, changes);
        String dryRunId = "realm-dry-run-" + Integer.toHexString(String.join("\n", diff).hashCode());

        return new IdentityRealmDryRunReport(
                providerKey(),
                desiredRealm.realmId,
                dryRunId,
                "dry-run",
                readiness,
                destructiveApplyAvailable(),
                true,
                false,
                changes,
                checks,
                diff,
                distinct(warnings),
                distinct(blockers),
                List.of(
                        "Review desired realm diff in the Admin Console boundary.",
                        "Map unknown roles, groups, scopes, and feature mappings before they can affect members.",
                        "Store provider credentials only as SecretRef values before any future guarded apply.",
                        "Keep provider mutation disabled until apply guards, rollback evidence, and audit are proven."),
                List.of());
    }

    private void validateRequiredContract(NormalizedRealm desired, List<String> blockers, List<String> warnings) {
        if (blank(desired.realmId) || "missing".equals(desired.realmId)) {
            blockers.add("realm id is required before import planning");
        }
        if (desired.clients.isEmpty()) {
            blockers.add("at least one OIDC client must be declared");
        }
        if (desired.roles.stream().noneMatch(role -> "owner".equals(role) || "admin".equals(role))) {
            warnings.add("owner/admin role baseline is not present in desired realm state");
        }
    }

    private void validateIdentitySafeguards(
            IdentityRealmDesiredState desired,
            List<String> blockers,
            List<String> warnings) {
        String primarySubjectClaim = safe(desired.primarySubjectClaim()).toLowerCase(Locale.ROOT);
        if ("email".equals(primarySubjectClaim) || "preferred_username".equals(primarySubjectClaim)) {
            blockers.add("primary identity key must be immutable subject claim, not email or username");
        }
        if (desired.lastAdminSubjectRefs().isEmpty()) {
            warnings.add("last-admin protection requires at least one immutable subject reference before guarded apply");
        }
        for (String subjectRef : normalizeValues(desired.lastAdminSubjectRefs())) {
            if (subjectRef.contains("@")) {
                blockers.add("last-admin protection must not use email as primary subject: " + subjectRef);
            }
        }
        boolean hasBreakGlass = false;
        for (IdentityRealmDesiredState.RecoveryIdentity identity : desired.breakGlassIdentities()) {
            if (identity == null) {
                continue;
            }
            String subjectRef = safe(identity.subjectRef());
            if (identity.breakGlass()) {
                hasBreakGlass = true;
            }
            if (subjectRef.contains("@")) {
                blockers.add("break-glass identity must use immutable subject reference, not email: " + subjectRef);
            }
            List<String> roles = normalizeValues(identity.roles());
            if (identity.breakGlass() && roles.stream().noneMatch(role -> role.equals("owner") || role.equals("admin"))) {
                blockers.add("break-glass identity must carry owner or admin recovery role: " + subjectRef);
            }
        }
        if (!hasBreakGlass) {
            warnings.add("break-glass identity is required before Keycloak apply can be enabled");
        }
        for (IdentityRealmDesiredState.ServiceAccount account : desired.serviceAccounts()) {
            if (account == null) {
                continue;
            }
            String subjectRef = safe(account.subjectRef());
            if (blank(subjectRef) || "missing".equals(subjectRef)) {
                blockers.add("service account subject reference is required");
            }
            for (String role : normalizeValues(account.roles())) {
                if (!KNOWN_ROLES.contains(role)) {
                    blockers.add("unknown service account role deny by default until mapped: " + subjectRef + "/" + role);
                }
            }
            for (String scope : normalizeValues(account.scopes())) {
                if (!KNOWN_SCOPES.contains(scope)) {
                    blockers.add("unknown service account scope deny by default until mapped: " + subjectRef + "/" + scope);
                }
            }
        }
        if (desired.serviceAccounts().isEmpty()) {
            warnings.add("no service accounts declared for backend/admin automation planning");
        }
    }

    private Set<String> unsafeFeatureMappingKeys(
            IdentityRealmDesiredState desired,
            NormalizedRealm desiredRealm,
            List<String> blockers) {
        Set<String> unsafe = new LinkedHashSet<>();
        for (IdentityRealmDesiredState.FeatureMapping mapping : desired.featureMappings()) {
            if (mapping == null) {
                continue;
            }
            String featureKey = safe(mapping.featureKey());
            for (String role : normalizeValues(mapping.requiredRoles())) {
                if (!KNOWN_ROLES.contains(role) || !desiredRealm.roles.contains(role)) {
                    blockers.add("unknown feature role reference deny by default until mapped: " + featureKey + "/" + role);
                    unsafe.add(featureKey);
                }
            }
            for (String group : normalizeValues(mapping.requiredGroups())) {
                if (!KNOWN_GROUPS.contains(group) || !desiredRealm.groups.contains(group)) {
                    blockers.add("unknown feature group reference deny by default until mapped: " + featureKey + "/" + group);
                    unsafe.add(featureKey);
                }
            }
            for (String scope : normalizeValues(mapping.requiredScopes())) {
                if (!KNOWN_SCOPES.contains(scope) || !desiredRealm.scopes.contains(scope)) {
                    blockers.add("unknown feature scope reference deny by default until mapped: " + featureKey + "/" + scope);
                    unsafe.add(featureKey);
                }
            }
        }
        return unsafe;
    }

    private void compareRealmBasics(
            NormalizedRealm current,
            NormalizedRealm desired,
            boolean hasCurrent,
            List<IdentityRealmDryRunReport.ChangeRecord> changes) {
        change(changes, "/realm/id", hasCurrent ? current.realmId : null, desired.realmId, "realm-identifier");
        change(changes, "/realm/displayName", hasCurrent ? current.displayName : null, desired.displayName, "realm-display-name");
        change(changes, "/realm/enabled", hasCurrent ? String.valueOf(current.enabled) : null, String.valueOf(desired.enabled), "realm-enabled-flag");
    }

    private void compareCollection(
            String kind,
            List<String> current,
            List<String> desired,
            Set<String> known,
            List<String> blockers,
            List<IdentityRealmDryRunReport.ChangeRecord> changes) {
        for (String value : desired) {
            boolean knownValue = known.contains(value);
            if (!knownValue) {
                blockers.add("unknown " + kind + " deny by default until mapped: " + value);
            }
            if (!current.contains(value)) {
                changes.add(new IdentityRealmDryRunReport.ChangeRecord(
                        "/" + kind + "/" + value,
                        "create",
                        knownValue ? "safe" : "risky",
                        knownValue ? "known-" + kind + "-mapping" : "unknown-" + kind + "-deny-by-default",
                        "absent",
                        value,
                        knownValue ? "stable product capability mapping" : "policy-blocked until admin maps this identity input",
                        !knownValue));
            } else {
                changes.add(new IdentityRealmDryRunReport.ChangeRecord(
                        "/" + kind + "/" + value,
                        "no-op",
                        knownValue ? "safe" : "risky",
                        knownValue ? "known-" + kind + "-mapping" : "unknown-" + kind + "-deny-by-default",
                        value,
                        value,
                        knownValue ? "already mapped" : "policy-blocked until admin maps this identity input",
                        !knownValue));
            }
        }
        for (String value : current) {
            if (!desired.contains(value)) {
                changes.add(new IdentityRealmDryRunReport.ChangeRecord(
                        "/" + kind + "/" + value,
                        "delete",
                        "destructive",
                        "existing-" + kind + "-removal-blocked",
                        value,
                        "absent",
                        "could remove member access or administrative recovery; blocked in dry-run slice",
                        true));
            }
        }
    }

    private void compareRootRedirects(
            List<String> current,
            List<String> desired,
            List<IdentityRealmDryRunReport.ChangeRecord> changes,
            List<String> warnings) {
        for (String redirect : desired) {
            boolean risky = riskyRedirect(redirect);
            if (risky) {
                warnings.add("redirect origin requires admin review: " + redirect);
            }
            changes.add(changeRecord(
                    "/redirectOrigins/" + redirect,
                    current.contains(redirect) ? "no-op" : "create",
                    risky ? "risky" : "safe",
                    risky ? "redirect-origin-needs-admin-review" : "redirect-origin-support-safe",
                    current.contains(redirect) ? redirect : "absent",
                    redirect,
                    "realm-level redirect allowlist remains backend-controlled",
                    false));
        }
        for (String redirect : current) {
            if (!desired.contains(redirect)) {
                changes.add(changeRecord(
                        "/redirectOrigins/" + redirect,
                        "delete",
                        "destructive",
                        "redirect-origin-removal-blocked",
                        redirect,
                        "absent",
                        "could break existing client redirects; blocked in dry-run slice",
                        true));
            }
        }
    }

    private void compareClients(
            Map<String, NormalizedClient> current,
            Map<String, NormalizedClient> desired,
            boolean hasCurrent,
            List<String> declaredRoles,
            List<String> declaredScopes,
            List<String> blockers,
            List<IdentityRealmDryRunReport.ChangeRecord> changes,
            List<String> warnings) {
        for (Map.Entry<String, NormalizedClient> entry : desired.entrySet()) {
            String id = entry.getKey();
            NormalizedClient desiredClient = entry.getValue();
            NormalizedClient currentClient = current.get(id);
            if (!hasCurrent || currentClient == null) {
                changes.add(changeRecord("/clients/" + id, "create", "safe", "oidc-client-managed-by-control-plane", "absent", id,
                        "members continue to use backend-owned sign-in; provider client details stay admin-only", false));
            } else {
                change(changes, "/clients/" + id + "/publicClient", String.valueOf(currentClient.publicClient), String.valueOf(desiredClient.publicClient), "client-public-mode");
            }
            compareRedirects(id, currentClient == null ? List.of() : currentClient.redirectOrigins, desiredClient.redirectOrigins, changes, warnings);
            compareNestedClientValues(id, "roles", currentClient == null ? List.of() : currentClient.roles, desiredClient.roles, declaredRoles, KNOWN_ROLES, blockers, changes);
            compareNestedClientValues(id, "scopes", currentClient == null ? List.of() : currentClient.scopes, desiredClient.scopes, declaredScopes, KNOWN_SCOPES, blockers, changes);
        }
        for (String id : current.keySet()) {
            if (!desired.containsKey(id)) {
                changes.add(changeRecord("/clients/" + id, "delete", "destructive", "existing-client-removal-blocked", id, "absent",
                        "could break sign-in or session refresh for members; blocked in dry-run slice", true));
            }
        }
    }

    private void compareRedirects(
            String clientId,
            List<String> current,
            List<String> desired,
            List<IdentityRealmDryRunReport.ChangeRecord> changes,
            List<String> warnings) {
        for (String redirect : desired) {
            boolean risky = riskyRedirect(redirect);
            if (risky) {
                warnings.add("redirect origin requires admin review: " + redirect);
            }
            String action = current.contains(redirect) ? "no-op" : "create";
            changes.add(changeRecord(
                    "/clients/" + clientId + "/redirectOrigins/" + redirect,
                    action,
                    risky ? "risky" : "safe",
                    risky ? "redirect-origin-needs-admin-review" : "redirect-origin-support-safe",
                    current.contains(redirect) ? redirect : "absent",
                    redirect,
                    "member sign-in remains backend-owned; admin must review redirect surface",
                    false));
        }
        for (String redirect : current) {
            if (!desired.contains(redirect)) {
                changes.add(changeRecord(
                        "/clients/" + clientId + "/redirectOrigins/" + redirect,
                        "delete",
                        "destructive",
                        "redirect-origin-removal-blocked",
                        redirect,
                        "absent",
                        "could break existing client redirects; blocked in dry-run slice",
                        true));
            }
        }
    }

    private void compareNestedClientValues(
            String clientId,
            String kind,
            List<String> current,
            List<String> desired,
            List<String> declaredValues,
            Set<String> knownValues,
            List<String> blockers,
            List<IdentityRealmDryRunReport.ChangeRecord> changes) {
        for (String value : desired) {
            boolean known = knownValues.contains(value) && declaredValues.contains(value);
            if (!known) {
                blockers.add("unknown client " + kind + " reference deny by default until mapped: " + clientId + "/" + value);
            }
            changes.add(changeRecord(
                    "/clients/" + clientId + "/" + kind + "/" + value,
                    current.contains(value) ? "no-op" : "create",
                    known ? "safe" : "risky",
                    known ? "client-" + kind + "-reference" : "unknown-client-" + kind + "-deny-by-default",
                    current.contains(value) ? value : "absent",
                    value,
                    known ? "client references known support-safe realm contract values" : "policy-blocked until admin maps this client identity input",
                    !known));
        }
        for (String value : current) {
            if (!desired.contains(value)) {
                changes.add(changeRecord(
                        "/clients/" + clientId + "/" + kind + "/" + value,
                        "delete",
                        "destructive",
                        "client-" + kind + "-removal-blocked",
                        value,
                        "absent",
                        "could remove client access mapping; blocked in dry-run slice",
                        true));
            }
        }
    }

    private void compareClaimMappers(
            Map<String, String> current,
            Map<String, String> desired,
            boolean hasCurrent,
            List<IdentityRealmDryRunReport.ChangeRecord> changes) {
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            change(changes, "/claimMappers/" + entry.getKey(), hasCurrent ? current.get(entry.getKey()) : null, entry.getValue(), "claim-mapper-contract");
        }
        for (String key : current.keySet()) {
            if (!desired.containsKey(key)) {
                changes.add(changeRecord("/claimMappers/" + key, "delete", "destructive", "claim-mapper-removal-blocked", current.get(key), "absent",
                        "could remove required organization or capability claims; blocked in dry-run slice", true));
            }
        }
    }

    private void compareFeatureMappings(
            Map<String, String> current,
            Map<String, String> desired,
            boolean hasCurrent,
            Set<String> unsafeFeatureMappingKeys,
            List<String> blockers,
            List<IdentityRealmDryRunReport.ChangeRecord> changes) {
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            boolean knownFeature = KNOWN_FEATURES.contains(entry.getKey());
            boolean referencesKnown = !unsafeFeatureMappingKeys.contains(entry.getKey());
            if (!knownFeature) {
                blockers.add("unknown feature mapping deny by default until mapped: " + entry.getKey());
            }
            String currentValue = hasCurrent ? current.get(entry.getKey()) : null;
            String action = currentValue == null ? "create" : Objects.equals(currentValue, entry.getValue()) ? "no-op" : "update";
            boolean safeMapping = knownFeature && referencesKnown;
            changes.add(changeRecord(
                    "/featureMappings/" + entry.getKey(),
                    action,
                    safeMapping ? "safe" : "risky",
                    safeMapping ? "feature-mapping-known" : "unknown-feature-mapping-deny-by-default",
                    currentValue == null ? "absent" : currentValue,
                    entry.getValue(),
                    safeMapping ? "feature mapping stays within backend-owned capability policy" : "policy-blocked until admin maps this feature and its identity inputs",
                    !safeMapping));
        }
        for (String key : current.keySet()) {
            if (!desired.containsKey(key)) {
                changes.add(changeRecord("/featureMappings/" + key, "delete", "destructive", "feature-mapping-removal-blocked", current.get(key), "absent",
                        "could remove access to workspace features; blocked in dry-run slice", true));
            }
        }
    }

    private void change(List<IdentityRealmDryRunReport.ChangeRecord> changes, String path, String before, String after, String reasonCode) {
        String safeBefore = safe(before);
        String safeAfter = safe(after);
        String action;
        if (before == null) {
            action = "create";
        } else if (Objects.equals(safeBefore, safeAfter)) {
            action = "no-op";
        } else {
            action = "update";
        }
        changes.add(changeRecord(path, action, "safe", reasonCode, before == null ? "absent" : safeBefore, safeAfter,
                "support-safe control-plane metadata only", false));
    }

    private IdentityRealmDryRunReport.ChangeRecord changeRecord(
            String path,
            String action,
            String classification,
            String reasonCode,
            String beforeValue,
            String afterValue,
            String memberImpact,
            boolean applyBlocked) {
        return new IdentityRealmDryRunReport.ChangeRecord(
                safePath(path),
                action,
                classification,
                reasonCode,
                safe(beforeValue),
                safe(afterValue),
                memberImpact,
                applyBlocked);
    }

    private List<IdentityRealmDryRunReport.ReadinessCheck> readinessChecks(
            String readiness,
            List<String> blockers,
            boolean hasRisky,
            boolean hasDestructive,
            boolean hasUnknown) {
        return List.of(
                new IdentityRealmDryRunReport.ReadinessCheck(
                        "realm-contract",
                        blockers.isEmpty() ? "ready" : "admin-action-required",
                        blockers.isEmpty() ? "realm-desired-state-complete" : "realm-desired-state-incomplete",
                        blockers.isEmpty() ? "Review deterministic diff before apply." : "Resolve listed blockers and rerun dry-run."),
                new IdentityRealmDryRunReport.ReadinessCheck(
                        "fail-closed-policy",
                        hasUnknown ? "admin-action-required" : "ready",
                        hasUnknown ? "unknown-roles-groups-scopes-or-features-deny-by-default" : "known-policy-inputs",
                        "Map unknown identity inputs explicitly before they can affect members."),
                new IdentityRealmDryRunReport.ReadinessCheck(
                        "apply-safety",
                        hasDestructive ? "policy-blocked" : readiness,
                        hasDestructive ? "destructive-apply-blocked" : hasRisky ? "risky-change-review-required" : "dry-run-only-no-mutation",
                        "This slice never mutates Keycloak or Terraform/OpenTofu state."));
    }

    private String readiness(List<String> blockers, boolean hasRisky, boolean hasDestructive, boolean hasUnknown) {
        if (hasDestructive) {
            return "policy-blocked";
        }
        if (!blockers.isEmpty() || hasUnknown) {
            return "admin-action-required";
        }
        return hasRisky ? "degraded" : "ready";
    }

    private List<String> deterministicDiff(NormalizedRealm desired, List<IdentityRealmDryRunReport.ChangeRecord> changes) {
        List<String> diff = new ArrayList<>();
        diff.add("plan realm=" + desired.realmId);
        diff.add("plan displayName=" + desired.displayName);
        diff.add("plan enabled=" + desired.enabled);
        diff.add("plan clients=" + desired.clients.size());
        diff.add("plan roles=" + desired.roles.size());
        diff.add("plan groups=" + desired.groups.size());
        diff.add("plan scopes=" + desired.scopes.size());
        diff.add("plan claimMappers=" + desired.claimMappers.size());
        diff.add("plan featureMappings=" + desired.featureMappings.size());
        diff.add("plan serviceAccounts=" + desired.serviceAccounts.size());
        diff.add("plan breakGlassIdentities=" + desired.breakGlassIdentities.size());
        diff.add("plan lastAdminSubjectRefs=" + desired.lastAdminSubjectRefs.size());
        diff.add("plan primarySubjectClaim=" + desired.primarySubjectClaim);
        changes.stream()
                .map(change -> change.action() + " " + change.path() + " classification=" + change.classification() + " blocked=" + change.applyBlocked())
                .forEach(diff::add);
        return diff;
    }

    private NormalizedRealm normalize(IdentityRealmDesiredState state) {
        return new NormalizedRealm(
                safe(state.realmId()),
                safe(state.displayName()),
                state.enabled() == null || state.enabled(),
                normalizeClients(state.clients()),
                normalizeValues(state.roles()),
                normalizeValues(state.groups()),
                normalizeValues(state.scopes()),
                normalizeClaimMappers(state.claimMappers()),
                normalizeValues(state.redirectOrigins()),
                normalizeFeatureMappings(state.featureMappings()),
                normalizeServiceAccounts(state.serviceAccounts()),
                normalizeRecoveryIdentities(state.breakGlassIdentities()),
                normalizeValues(state.lastAdminSubjectRefs()),
                safe(state.primarySubjectClaim()),
                safeList(state.providerWarnings()),
                safeList(state.blockers()));
    }

    private Map<String, NormalizedClient> normalizeClients(List<IdentityRealmDesiredState.RealmClient> clients) {
        Map<String, NormalizedClient> normalized = new LinkedHashMap<>();
        clients.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(client -> safe(client.clientId())))
                .forEach(client -> normalized.put(safe(client.clientId()), new NormalizedClient(
                        safe(client.clientId()),
                        client.publicClient(),
                        normalizeValues(client.redirectOrigins()),
                        normalizeValues(client.roles()),
                        normalizeValues(client.scopes()))));
        return normalized;
    }

    private Map<String, String> normalizeClaimMappers(List<IdentityRealmDesiredState.ClaimMapper> mappers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        mappers.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(mapper -> safe(mapper.name())))
                .forEach(mapper -> normalized.put(
                        safe(mapper.name()),
                        String.join("->", safe(mapper.sourceClaim()), safe(mapper.targetClaim()), String.valueOf(mapper.required()))));
        return normalized;
    }

    private Map<String, String> normalizeFeatureMappings(List<IdentityRealmDesiredState.FeatureMapping> mappings) {
        Map<String, String> normalized = new LinkedHashMap<>();
        mappings.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(mapping -> safe(mapping.featureKey())))
                .forEach(mapping -> normalized.put(
                        safe(mapping.featureKey()),
                        "roles=" + normalizeValues(mapping.requiredRoles())
                                + ";groups=" + normalizeValues(mapping.requiredGroups())
                                + ";scopes=" + normalizeValues(mapping.requiredScopes())));
        return normalized;
    }

    private Map<String, String> normalizeServiceAccounts(List<IdentityRealmDesiredState.ServiceAccount> accounts) {
        Map<String, String> normalized = new LinkedHashMap<>();
        accounts.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(account -> safe(account.subjectRef())))
                .forEach(account -> normalized.put(
                        safe(account.subjectRef()),
                        "roles=" + normalizeValues(account.roles()) + ";scopes=" + normalizeValues(account.scopes())));
        return normalized;
    }

    private Map<String, String> normalizeRecoveryIdentities(List<IdentityRealmDesiredState.RecoveryIdentity> identities) {
        Map<String, String> normalized = new LinkedHashMap<>();
        identities.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(identity -> safe(identity.subjectRef())))
                .forEach(identity -> normalized.put(
                        safe(identity.subjectRef()),
                        "purpose=" + safe(identity.purpose()) + ";breakGlass=" + identity.breakGlass() + ";roles=" + normalizeValues(identity.roles())));
        return normalized;
    }

    private List<String> normalizeValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::safe)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::safe)
                .distinct()
                .toList();
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private boolean riskyRedirect(String value) {
        if (value == null) {
            return false;
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        return lowered.contains("*") || lowered.startsWith("http://");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safePath(String path) {
        return safe(path).replace(" ", "-");
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "missing";
        }
        String trimmed = value.trim();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if (SECRET_LIKE.matcher(lowered).find()
                || lowered.contains("@github.com/")
                || lowered.contains("x-access-token:")
                || lowered.contains("-----begin")) {
            return "redacted-secret-like-value";
        }
        return trimmed;
    }

    private record NormalizedRealm(
            String realmId,
            String displayName,
            boolean enabled,
            Map<String, NormalizedClient> clients,
            List<String> roles,
            List<String> groups,
            List<String> scopes,
            Map<String, String> claimMappers,
            List<String> redirectOrigins,
            Map<String, String> featureMappings,
            Map<String, String> serviceAccounts,
            Map<String, String> breakGlassIdentities,
            List<String> lastAdminSubjectRefs,
            String primarySubjectClaim,
            List<String> warnings,
            List<String> blockers) {
        static NormalizedRealm empty() {
            return new NormalizedRealm("missing", "missing", true, Map.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), List.of(), "sub", List.of(), List.of());
        }
    }

    private record NormalizedClient(
            String clientId,
            boolean publicClient,
            List<String> redirectOrigins,
            List<String> roles,
            List<String> scopes) {
    }
}
