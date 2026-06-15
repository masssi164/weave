package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyProperties;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyReport;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyRequest;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDesiredState;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunEvidence;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunReport;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunRequest;
import com.massimotter.weave.backend.identity.realm.IdentityRealmEvidenceRepository;
import com.massimotter.weave.backend.identity.realm.IdentityRealmLiveApplyAdapter;
import com.massimotter.weave.backend.identity.realm.IdentityRealmProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import static com.massimotter.weave.backend.model.IdentityKeyFormat.MAX_PRIMARY_IDENTITY_KEY_LENGTH;
import static com.massimotter.weave.backend.model.IdentityKeyFormat.PRIMARY_IDENTITY_KEY_PATTERN;

@Service
public class IdentityRealmWorkflowService {

    private static final int MAX_BOOTSTRAP_ADMIN_KEY_LENGTH = MAX_PRIMARY_IDENTITY_KEY_LENGTH;
    private static final Pattern PRIMARY_IDENTITY_KEY_REGEX = Pattern.compile(PRIMARY_IDENTITY_KEY_PATTERN);

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final AuditEventPublisher auditEventPublisher;
    private final List<IdentityRealmProvider> identityRealmProviders;
    private final IdentityRealmEvidenceRepository identityRealmEvidenceRepository;
    private final List<IdentityRealmLiveApplyAdapter> identityRealmLiveApplyAdapters;
    private final IdentityRealmApplyProperties identityRealmApplyProperties;
    private final Clock clock;

    public IdentityRealmWorkflowService(
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher,
            List<IdentityRealmProvider> identityRealmProviders,
            IdentityRealmEvidenceRepository identityRealmEvidenceRepository,
            List<IdentityRealmLiveApplyAdapter> identityRealmLiveApplyAdapters,
            IdentityRealmApplyProperties identityRealmApplyProperties,
            Clock clock) {
        this.workspaceCapabilityService = Objects.requireNonNull(workspaceCapabilityService, "workspaceCapabilityService");
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher, "auditEventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identityRealmProviders = requireNonEmpty(identityRealmProviders, "identityRealmProviders");
        this.identityRealmEvidenceRepository = Objects.requireNonNull(identityRealmEvidenceRepository, "identityRealmEvidenceRepository");
        this.identityRealmApplyProperties = Objects.requireNonNull(identityRealmApplyProperties, "identityRealmApplyProperties");
        this.identityRealmLiveApplyAdapters = requireNonEmpty(identityRealmLiveApplyAdapters, "identityRealmLiveApplyAdapters");
    }

    public IdentityRealmDryRunReport dryRunIdentityRealm(IdentityRealmDryRunRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "identity-realm", "dry-run");
        IdentityRealmProvider provider = identityRealmProvider("keycloak-realm");
        IdentityRealmDryRunReport report = provider.dryRun(request);
        String auditRef = "identity-realm-dry-run-" + Instant.now(clock).toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
                "identity-realm-dry-run",
                AuditAction.PROVIDER_REPLACEMENT_DRY_RUN,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "providerKey", provider.providerKey(),
                        "realmId", report.realmId(),
                        "readiness", report.readiness(),
                        "changeCount", report.changes().size(),
                        "blockerCount", report.blockers().size(),
                        "supportSafe", report.supportSafe(),
                        "rawSecretExposed", report.rawSecretExposed(),
                        "destructiveApplyAvailable", report.destructiveApplyAvailable(),
                        "dryRunReasonPresent", request != null && request.reason() != null && !request.reason().isBlank())));
        IdentityRealmDryRunReport persistedReport = new IdentityRealmDryRunReport(
                report.providerKey(),
                report.realmId(),
                report.dryRunId(),
                report.operation(),
                report.readiness(),
                report.destructiveApplyAvailable(),
                report.supportSafe(),
                report.rawSecretExposed(),
                report.changes(),
                report.readinessChecks(),
                report.diff(),
                report.warnings(),
                report.blockers(),
                report.nextActions(),
                List.of(auditRef));
        identityRealmEvidenceRepository.save(new IdentityRealmDryRunEvidence(
                persistedReport.dryRunId(),
                auditRef,
                provider.providerKey(),
                persistedReport.realmId(),
                persistedReport,
                Instant.now(clock)));
        return persistedReport;
    }

    public IdentityRealmApplyReport applyIdentityRealm(IdentityRealmApplyRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.provider.configure", "identity-realm", "apply");
        IdentityRealmProvider provider = identityRealmProvider("keycloak-realm");
        IdentityRealmDryRunReport requestedDryRun = provider.dryRun(request == null ? null : request.dryRunRequest());
        Optional<IdentityRealmDryRunEvidence> persistedEvidence = identityRealmEvidenceRepository.findDryRun(request == null ? null : request.dryRunId());
        IdentityRealmDryRunReport dryRun = persistedEvidence.map(IdentityRealmDryRunEvidence::report).orElse(requestedDryRun);
        long safeChangeCount = dryRun.changes().stream().filter(change -> "safe".equals(change.classification())).count();
        long riskyChangeCount = dryRun.changes().stream().filter(change -> "risky".equals(change.classification())).count();
        long destructiveChangeCount = dryRun.changes().stream().filter(change -> "destructive".equals(change.classification())).count();
        boolean hasRisky = riskyChangeCount > 0;
        boolean hasDestructive = destructiveChangeCount > 0;
        boolean rollbackRequired = hasRisky || hasDestructive;
        boolean rollbackAccepted = !rollbackRequired || hasText(request == null ? null : request.rollbackEvidenceRef());
        boolean lastAdminGuardPassed = retainedAdminProofPresent(request);
        boolean confirmationProvided = request != null && "APPLY WEAVE IDENTITY REALM".equals(request.confirmationPhrase());
        boolean policySimulationPresent = request != null
                && hasText(request.policySimulationRef())
                && request.policySimulationRef().startsWith("effective-policy-simulation-");
        boolean persistedDryRunFresh = persistedEvidence
                .filter(evidence -> evidence.providerKey().equals(provider.providerKey()))
                .filter(evidence -> evidence.dryRunId().equals(requestedDryRun.dryRunId()))
                .filter(evidence -> !evidence.createdAt().plusSeconds(identityRealmApplyProperties.dryRunFreshnessSeconds()).isBefore(Instant.now(clock)))
                .isPresent();
        List<String> blocked = new ArrayList<>();
        if (!persistedDryRunFresh) {
            blocked.add("fresh persisted dry-run evidence is required before identity realm apply");
        }
        if (!policySimulationPresent) {
            blocked.add("effective policy simulation evidence ref is required before identity realm apply");
        }
        if (!confirmationProvided) {
            blocked.add("explicit confirmation phrase is required");
        }
        if (!lastAdminGuardPassed) {
            blocked.add("last-admin guard requires at least one retained immutable admin identity key");
        }
        if (hasRisky && (request == null || !request.approveRisky())) {
            blocked.add("risky changes require approveRisky=true");
        }
        if (hasDestructive && (request == null || !request.approveDestructive())) {
            blocked.add("destructive changes require approveDestructive=true");
        }
        if (hasDestructive && !provider.destructiveApplyAvailable()) {
            blocked.add("provider destructive apply is not available for this contract");
        }
        if (!rollbackAccepted) {
            blocked.add("rollback/restore evidence ref is required for risky or destructive apply");
        }
        blocked.addAll(dryRun.blockers());
        boolean guardsAccepted = blocked.isEmpty();
        IdentityRealmLiveApplyAdapter.IdentityRealmLiveApplyResult liveApply = guardsAccepted
                ? identityRealmLiveApplyAdapter(provider.providerKey()).apply(persistedEvidence.orElseThrow(), request)
                : new IdentityRealmLiveApplyAdapter.IdentityRealmLiveApplyResult(false, false, "guarded-provider-apply-blocked-before-adapter", List.of(), List.of());
        blocked.addAll(liveApply.blockedReasons());
        boolean accepted = blocked.isEmpty();
        boolean applied = accepted && liveApply.applied();
        boolean providerMutationPerformed = accepted && liveApply.providerMutationPerformed();
        String executionMode = accepted ? liveApply.executionMode() : "guarded-provider-apply-blocked-before-mutation";
        List<String> nextActions = new ArrayList<>(applyNextActions(accepted, blocked, rollbackRequired, hasRisky, hasDestructive));
        nextActions.addAll(liveApply.nextActions());
        nextActions = nextActions.stream().distinct().toList();
        String auditRef = "identity-realm-apply-" + Instant.now(clock).toEpochMilli();
        String actorRef = actorRef(jwt);
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef,
                "identity-realm-apply",
                AuditAction.IDENTITY_REALM_APPLY_GUARDED,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.ofEntries(
                        Map.entry("actorRef", actorRef),
                        Map.entry("candidateRef", "identity-realm:" + dryRun.realmId()),
                        Map.entry("planRef", dryRun.dryRunId()),
                        Map.entry("providerKey", provider.providerKey()),
                        Map.entry("realmId", dryRun.realmId()),
                        Map.entry("decision", accepted ? "accepted" : "blocked"),
                        Map.entry("result", accepted ? (applied ? "accepted-with-provider-mutation" : "accepted-without-provider-mutation") : "blocked-before-provider-mutation"),
                        Map.entry("executionMode", executionMode),
                        Map.entry("liveApplyEnabled", identityRealmApplyProperties.liveApplyEnabled()),
                        Map.entry("providerConfigured", identityRealmApplyProperties.providerConfigured()),
                        Map.entry("providerMutationPerformed", providerMutationPerformed),
                        Map.entry("safeChangeCount", safeChangeCount),
                        Map.entry("riskyChangeCount", riskyChangeCount),
                        Map.entry("destructiveChangeCount", destructiveChangeCount),
                        Map.entry("blockedReasonCount", blocked.size()),
                        Map.entry("nextActionCount", nextActions.size()),
                        Map.entry("persistedDryRunEvidencePresent", persistedEvidence.isPresent()),
                        Map.entry("persistedDryRunFresh", persistedDryRunFresh),
                        Map.entry("effectivePolicySimulationEvidencePresent", policySimulationPresent),
                        Map.entry("confirmationProvided", confirmationProvided),
                        Map.entry("retainedAdminIdentityKeyCount", request == null ? 0 : request.retainedAdminPrimaryIdentityKeys().stream().filter(this::safePrimaryIdentityKey).count()),
                        Map.entry("rollbackRestoreEvidencePresent", request != null && hasText(request.rollbackEvidenceRef())),
                        Map.entry("lastAdminGuardPassed", lastAdminGuardPassed),
                        Map.entry("rollbackEvidenceAccepted", rollbackAccepted),
                        Map.entry("supportSafe", true))));
        return new IdentityRealmApplyReport(
                provider.providerKey(),
                dryRun.realmId(),
                dryRun.dryRunId(),
                accepted ? "accepted" : "blocked",
                executionMode,
                applied,
                providerMutationPerformed,
                true,
                false,
                lastAdminGuardPassed,
                rollbackRequired,
                rollbackAccepted,
                blocked.stream().distinct().toList(),
                dryRun.changes(),
                nextActions,
                List.of(auditRef));
    }

    boolean retainedAdminProofPresent(IdentityRealmApplyRequest request) {
        if (request == null || request.retainedAdminPrimaryIdentityKeys().isEmpty()) {
            return false;
        }
        IdentityRealmDesiredState desiredState = request.dryRunRequest() == null ? null : request.dryRunRequest().desiredState();
        if (desiredState == null) {
            return false;
        }
        Set<String> retainedSafeKeys = request.retainedAdminPrimaryIdentityKeys().stream()
                .filter(this::safePrimaryIdentityKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (retainedSafeKeys.isEmpty()) {
            return false;
        }
        Set<String> desiredLastAdminRefs = desiredState.lastAdminSubjectRefs().stream()
                .filter(this::safePrimaryIdentityKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> recoveryAdminRefs = desiredState.breakGlassIdentities().stream()
                .filter(identity -> identity != null && identity.breakGlass())
                .filter(identity -> identity.roles().stream().map(role -> role.toLowerCase(Locale.ROOT)).anyMatch(role -> role.equals("owner") || role.equals("admin")))
                .map(IdentityRealmDesiredState.RecoveryIdentity::subjectRef)
                .filter(this::safePrimaryIdentityKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return retainedSafeKeys.stream().anyMatch(key -> desiredLastAdminRefs.contains(key) || recoveryAdminRefs.contains(key));
    }

    List<String> applyNextActions(
            boolean accepted,
            List<String> blocked,
            boolean rollbackRequired,
            boolean hasRisky,
            boolean hasDestructive) {
        if (accepted) {
            return List.of(
                    "Guarded apply decision accepted after persisted dry-run, policy simulation, retained-admin, rollback, audit, and confirmation checks.",
                    "Archive the dry-run, policy simulation, retained-admin, rollback/export evidence, and audit ref before any future provider adapter retry.");
        }
        List<String> nextActions = new ArrayList<>();
        if (blocked.stream().anyMatch(reason -> reason.contains("confirmation"))) {
            nextActions.add("Re-submit with confirmationPhrase=APPLY WEAVE IDENTITY REALM after reviewing the dry-run.");
        }
        if (blocked.stream().anyMatch(reason -> reason.contains("last-admin"))) {
            nextActions.add("Retain at least one immutable owner/admin primary identity key such as issuer+subject before retrying.");
        }
        if (hasRisky) {
            nextActions.add("Review risky change classifications, run effective policy simulation, and set approveRisky=true only with operator evidence.");
        }
        if (hasDestructive) {
            nextActions.add("Treat destructive changes as unavailable until provider destructive apply support and restore evidence are explicitly proven.");
        }
        if (rollbackRequired && blocked.stream().anyMatch(reason -> reason.contains("rollback/restore evidence"))) {
            nextActions.add("Attach a support-safe rollback/restore evidence reference before retrying risky or destructive apply.");
        }
        nextActions.add("Resolve blockedReasons and re-run /api/admin/identity/realm/dry-run before another apply attempt.");
        return nextActions.stream().distinct().toList();
    }

    IdentityRealmProvider identityRealmProvider(String providerKey) {
        return identityRealmProviders.stream()
                .filter(provider -> provider.providerKey().equals(providerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No identity realm provider configured for " + providerKey));
    }

    IdentityRealmLiveApplyAdapter identityRealmLiveApplyAdapter(String providerKey) {
        return identityRealmLiveApplyAdapters.stream()
                .filter(adapter -> adapter.providerKey().equals(providerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No identity realm live apply adapter configured for " + providerKey));
    }

    private static <T> List<T> requireNonEmpty(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.isEmpty()) {
            throw new IllegalStateException(name + " must not be empty");
        }
        return List.copyOf(values);
    }

    private boolean safePrimaryIdentityKey(String value) {
        return value != null
                && value.length() <= MAX_BOOTSTRAP_ADMIN_KEY_LENGTH
                && PRIMARY_IDENTITY_KEY_REGEX.matcher(value).matches();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String organizationId(Jwt jwt) {
        return claim(jwt, "weave_tenant")
                .or(() -> claim(jwt, "tenant"))
                .or(() -> claim(jwt, "tid"))
                .orElse("weave-dogfood");
    }

    private String actorRef(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return "actor:system";
        }
        return "user:" + jwt.getSubject();
    }

    private Optional<String> claim(Jwt jwt, String claimName) {
        if (jwt == null) {
            return Optional.empty();
        }
        Object raw = jwt.getClaims().get(claimName);
        if (raw instanceof String value && !value.isBlank()) {
            return Optional.of(value.trim());
        }
        return Optional.empty();
    }
}
