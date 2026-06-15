package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EffectivePolicySimulationService {

    private static final Set<String> SIMULATION_ROLES = Set.of("owner", "admin", "operator", "member", "guest");
    private static final Set<String> SIMULATION_GROUPS = Set.of(
            "weave-calendar-editors",
            "weave-board-editors",
            "weave-meeting-hosts",
            "weave-document-editors",
            "weave-decision-recorders",
            "weave-weaver-pilot");
    private static final Map<String, List<String>> SIMULATION_GROUP_CAPABILITIES = Map.of(
            "weave-calendar-editors", List.of("calendar.manage_events"),
            "weave-board-editors", List.of("boards.update_task"),
            "weave-meeting-hosts", List.of("meetings.host"),
            "weave-document-editors", List.of("documents.edit"),
            "weave-decision-recorders", List.of("decisions.record"),
            "weave-weaver-pilot", List.of("weaver.files_read", "weaver.exec_disabled"));
    private static final Set<String> SIMULATION_KNOWN_CAPABILITIES = Set.of(
            "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "calendar.manage_events",
            "boards.read", "boards.update_task", "meetings.join", "meetings.host", "documents.view", "documents.edit",
            "decisions.read", "decisions.record", "manuals.read", "manuals.admin", "release_evidence.read", "release_evidence.manage",
            "admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure", "operator.support_bundle.create",
            "weaver.enabled", "weaver.files_read", "weaver.exec_disabled");

    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    @Autowired
    public EffectivePolicySimulationService(AuditEventPublisher auditEventPublisher) {
        this(auditEventPublisher, Clock.systemUTC());
    }

    EffectivePolicySimulationService(AuditEventPublisher auditEventPublisher, Clock clock) {
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public EffectivePolicySimulationResponse simulate(
            EffectivePolicySimulationRequest request,
            String organizationId,
            String actorRef) {
        List<String> deniedInputs = Stream.of(
                        deniedInputCodes(request == null ? null : request.roles(), "role", SIMULATION_ROLES),
                        deniedInputCodes(request == null ? null : request.groups(), "group", SIMULATION_GROUPS),
                        deniedInputCodes(request == null ? null : request.requestedCapabilities(), "capability", SIMULATION_KNOWN_CAPABILITIES))
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .toList();
        List<String> roles = normalizedKnownValues(request == null ? null : request.roles(), SIMULATION_ROLES);
        List<String> groups = normalizedKnownValues(request == null ? null : request.groups(), SIMULATION_GROUPS);
        List<String> requestedCapabilities = normalizedKnownValues(request == null ? null : request.requestedCapabilities(), SIMULATION_KNOWN_CAPABILITIES);
        boolean failClosed = !deniedInputs.isEmpty();
        LinkedHashSet<String> grants = new LinkedHashSet<>();
        if (!failClosed) {
            if (roles.stream().anyMatch(role -> role.equals("owner") || role.equals("admin"))) {
                grants.addAll(List.of(
                        "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "calendar.manage_events",
                        "boards.read", "boards.update_task", "meetings.join", "meetings.host", "documents.view", "documents.edit",
                        "decisions.read", "decisions.record", "manuals.read", "manuals.admin", "release_evidence.read", "release_evidence.manage",
                        "admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure", "weaver.exec_disabled"));
            }
            if (roles.contains("operator")) {
                grants.addAll(List.of("admin_control_plane.readiness_read", "operator.support_bundle.create", "release_evidence.read", "manuals.admin", "manuals.read", "weaver.exec_disabled"));
            }
            if (roles.contains("member")) {
                grants.addAll(List.of("chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "boards.read", "meetings.join", "documents.view", "decisions.read", "manuals.read", "release_evidence.read", "weaver.exec_disabled"));
            }
            for (String group : groups) {
                grants.addAll(SIMULATION_GROUP_CAPABILITIES.getOrDefault(group, List.of()));
            }
            grants.remove("weaver.enabled");
        }
        List<EffectivePolicySimulationResponse.CapabilityState> capabilityStates = requestedCapabilities.stream()
                .map(capability -> simulationState(capability, grants, failClosed))
                .toList();
        String auditRef = "effective-policy-simulation-" + Instant.now(clock).toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                organizationId,
                "admin-control-plane",
                actorRef,
                "effective-policy-simulation",
                AuditAction.EFFECTIVE_POLICY_SIMULATED,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "subjectProvided", request != null && request.subject() != null && !request.subject().isBlank(),
                        "organizationProvided", request != null && request.organizationId() != null && !request.organizationId().isBlank(),
                        "roleCount", roles.size(),
                        "groupCount", groups.size(),
                        "requestedCapabilityCount", requestedCapabilities.size(),
                        "unknownInputCount", deniedInputs.size(),
                        "unknownInputsFailClosed", failClosed,
                        "supportSafe", true,
                        "reasonProvided", request != null && request.reason() != null && !request.reason().isBlank())));
        return new EffectivePolicySimulationResponse(
                safeSimulationIdentityRef(request == null ? null : request.subject()),
                request == null || request.organizationId() == null || request.organizationId().isBlank()
                        ? organizationId
                        : safeText(request.organizationId()),
                roles,
                groups,
                requestedCapabilities,
                grants.stream().filter(requestedCapabilities::contains).sorted().toList(),
                deniedInputs,
                failClosed,
                true,
                true,
                capabilityStates,
                failClosed
                        ? List.of("Map unknown roles, groups, or capabilities before provider activation.")
                        : List.of("Review member-visible states before applying provider or realm changes."),
                List.of(auditRef));
    }

    private List<String> normalizedKnownValues(List<String> values, Set<String> knownValues) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(this::safeSimulationInputToken)
                .filter(knownValues::contains)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> deniedInputCodes(List<String> values, String kind, Set<String> knownValues) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> {
                    if (value == null || value.isBlank()) {
                        return "invalid-" + kind;
                    }
                    String normalized = value.trim().toLowerCase(Locale.ROOT);
                    if (!safeSimulationInputToken(normalized)) {
                        return "invalid-" + kind;
                    }
                    if (!knownValues.contains(normalized)) {
                        return "unknown-" + kind;
                    }
                    return null;
                })
                .filter(value -> value != null)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean safeSimulationInputToken(String value) {
        return value != null && value.matches("[a-z][a-z0-9_.:-]*");
    }

    private EffectivePolicySimulationResponse.CapabilityState simulationState(
            String capability,
            Set<String> grants,
            boolean failClosed) {
        if (failClosed) {
            return new EffectivePolicySimulationResponse.CapabilityState(
                    capability,
                    "policy-blocked",
                    "unknown-identity-inputs-fail-closed",
                    "Admins must map unknown provider inputs before members receive this capability.");
        }
        if ("weaver.enabled".equals(capability)) {
            return new EffectivePolicySimulationResponse.CapabilityState(
                    capability,
                    "disabled",
                    "weaver-default-disabled",
                    "Weaver remains opt-in, governed, audited, and disabled by default.");
        }
        if (grants.contains(capability)) {
            return new EffectivePolicySimulationResponse.CapabilityState(
                    capability,
                    "ready",
                    "granted-by-effective-policy",
                    "Member-visible capability state may be ready if provider readiness also passes.");
        }
        return new EffectivePolicySimulationResponse.CapabilityState(
                capability,
                "policy-blocked",
                "deny-by-default-capability-policy",
                "This capability remains blocked unless a known org role or group grants it.");
    }

    private String safeSimulationIdentityRef(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        String trimmed = value.trim();
        if (trimmed.contains("@") || trimmed.matches("(?i).*(bearer\\s+|xox[baprs]-|secret(ref)?://|https?://|token|secret).*")) {
            return "identity-ref-redacted";
        }
        return safeText(trimmed);
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
}
