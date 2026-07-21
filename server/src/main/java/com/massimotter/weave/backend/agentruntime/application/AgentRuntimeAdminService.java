package com.massimotter.weave.backend.agentruntime.application;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProvisioningPlan;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonNotFoundException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.model.agentruntime.AgentRuntimeProjectionResponse;
import com.massimotter.weave.backend.model.agentruntime.StopAgentRuntimeRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Exact administrative lifecycle application service for the ARC OpenAPI. */
public final class AgentRuntimeAdminService {
    private static final Set<RuntimeCellState> STARTABLE = EnumSet.of(
            RuntimeCellState.PROVISIONING,
            RuntimeCellState.STOPPED,
            RuntimeCellState.STARTING,
            RuntimeCellState.MATERIALIZING,
            RuntimeCellState.READY,
            RuntimeCellState.BUSY,
            RuntimeCellState.SYNCING,
            RuntimeCellState.DEGRADED,
            RuntimeCellState.SUSPENDED);
    private static final Set<RuntimeCellState> STOPPABLE = EnumSet.of(
            RuntimeCellState.PROVISIONING,
            RuntimeCellState.STOPPED,
            RuntimeCellState.STARTING,
            RuntimeCellState.MATERIALIZING,
            RuntimeCellState.READY,
            RuntimeCellState.BUSY,
            RuntimeCellState.SYNCING,
            RuntimeCellState.DEGRADED,
            RuntimeCellState.SUSPENDED);
    private static final Set<RuntimeCellState> SUSPENDABLE = EnumSet.of(
            RuntimeCellState.PROVISIONING,
            RuntimeCellState.STOPPED,
            RuntimeCellState.STARTING,
            RuntimeCellState.MATERIALIZING,
            RuntimeCellState.READY,
            RuntimeCellState.BUSY,
            RuntimeCellState.SYNCING,
            RuntimeCellState.DEGRADED,
            RuntimeCellState.SUSPENDED);
    private static final Set<RuntimeCellState> DELETABLE = EnumSet.allOf(RuntimeCellState.class);

    private final RuntimePersonDirectory people;
    private final RuntimePolicyAuthority policy;
    private final AgentRuntimeControlService control;
    private final RuntimeProfileIssuanceService profileIssuance;
    private final RuntimeCellRepository cells;
    private final RuntimeCommandRepository commands;
    private final RuntimeProfileRepository profiles;
    private final RuntimeWorkloadIdentityAdmin workloadIdentities;
    private final RuntimeStateStoreAdmin runtimeState;
    private final Clock clock;

    public AgentRuntimeAdminService(
            RuntimePersonDirectory people,
            RuntimePolicyAuthority policy,
            AgentRuntimeControlService control,
            RuntimeProfileIssuanceService profileIssuance,
            RuntimeCellRepository cells,
            RuntimeCommandRepository commands,
            RuntimeProfileRepository profiles,
            RuntimeWorkloadIdentityAdmin workloadIdentities,
            RuntimeStateStoreAdmin runtimeState,
            Clock clock) {
        this.people = Objects.requireNonNull(people, "people");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.control = Objects.requireNonNull(control, "control");
        this.profileIssuance = Objects.requireNonNull(profileIssuance, "profileIssuance");
        this.cells = Objects.requireNonNull(cells, "cells");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.workloadIdentities = Objects.requireNonNull(workloadIdentities, "workloadIdentities");
        this.runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AgentRuntimeProjectionResponse get(AdminContext context, String personRef) {
        return projection(requireCell(context, personRef));
    }

    public AgentRuntimeProjectionResponse provision(
            AdminContext context,
            String personRef,
            String idempotencyKey) {
        RuntimePersonDirectory.ResolvedRuntimePerson person = people.resolve(
                new RuntimePersonDirectory.ResolveRuntimePersonCommand(
                        context.organizationRef(), personRef, context.auditRef()));
        RuntimeProvisioningPlan plan = policy.provisioningPlan(person);
        RuntimeCell cell = control.provision(new AgentRuntimeControlService.ProvisionRuntimeCommand(
                person.organizationRef(),
                person.personRef(),
                person.memberBinding(),
                plan.workspaceRevision(),
                plan.workspaceManifestRef(),
                plan.runtimeStateStoreRef(),
                plan.authenticationMethod(),
                idempotencyKey,
                context.auditRef()));
        return projection(cell);
    }

    public AgentRuntimeProjectionResponse start(
            AdminContext context,
            String personRef,
            String idempotencyKey) {
        RuntimePersonDirectory.ResolvedRuntimePerson person = people.resolve(
                new RuntimePersonDirectory.ResolveRuntimePersonCommand(
                        context.organizationRef(), personRef, context.auditRef()));
        RuntimeCell cell = requireCell(context, personRef);
        if (!cell.memberBinding().equals(person.memberBinding())) {
            throw new RuntimeCommandConflictException(
                    "the runtime cell is bound to another immutable member identity");
        }
        RuntimeCommandReceipt receipt = claim(cell, idempotencyKey, "START", context.auditRef());
        if (receipt.status() == RuntimeCommandReceipt.Status.COMPLETED) {
            return projection(requireCell(context, personRef));
        }
        try {
            cell = control.reconcileEntitlement(cell, context.auditRef());
            requireEntitled(cell);
            RuntimeWorkloadBinding reconciled = workloadIdentities.reconcileBinding(
                    new RuntimeWorkloadIdentityAdmin.ReconcileBindingCommand(
                            cell.organizationRef(), cell.personRef(), cell.cellRef(),
                            cell.workloadBinding(), context.auditRef()));
            if (!reconciled.equals(cell.workloadBinding())) {
                throw new RuntimeCommandConflictException(
                        "the immutable workload identity changed during start");
            }
            profileIssuance.issue(cell, "start:" + idempotencyKey, receipt.createdAt());
            cell = requireCell(context, personRef);
            cell = cells.transitionDesiredState(
                    cell.organizationRef(), cell.personRef(), cell.version(), STARTABLE,
                    RuntimeCellState.STARTING, context.auditRef(), clock.instant());
            commands.complete(receipt, cell.version(), clock.instant());
            return projection(cell);
        } catch (RuntimeException failure) {
            commands.fail(receipt, "runtime-start-failed", clock.instant());
            throw failure;
        }
    }

    public AgentRuntimeProjectionResponse stop(
            AdminContext context,
            String personRef,
            String idempotencyKey,
            StopAgentRuntimeRequest request) {
        RuntimeCell cell = requireCell(context, personRef);
        String command = "STOP:" + request.mode()
                + (request.timeoutSeconds() == null ? "" : ":" + request.timeoutSeconds());
        RuntimeCommandReceipt receipt = claim(cell, idempotencyKey, command, context.auditRef());
        if (receipt.status() == RuntimeCommandReceipt.Status.COMPLETED) {
            return projection(requireCell(context, personRef));
        }
        try {
            if (cell.entitlementState() == RuntimeEntitlementState.ENTITLED) {
                cell = cells.transitionDesiredState(
                        cell.organizationRef(), cell.personRef(), cell.version(), STOPPABLE,
                        RuntimeCellState.STOPPED, context.auditRef(), clock.instant());
            }
            commands.complete(receipt, cell.version(), clock.instant());
            return projection(cell);
        } catch (RuntimeException failure) {
            commands.fail(receipt, "runtime-stop-failed", clock.instant());
            throw failure;
        }
    }

    public AgentRuntimeProjectionResponse suspend(
            AdminContext context,
            String personRef,
            String idempotencyKey,
            String reason) {
        RuntimeCell cell = requireCell(context, personRef);
        RuntimeCommandReceipt receipt = claim(
                cell, idempotencyKey, semanticCommand("SUSPEND", reason), context.auditRef());
        if (receipt.status() == RuntimeCommandReceipt.Status.COMPLETED) {
            return projection(requireCell(context, personRef));
        }
        try {
            profiles.revokeCurrent(cell.cellRef(), "admin-suspended", clock.instant());
            if (cell.entitlementState() == RuntimeEntitlementState.ENTITLED) {
                cell = cells.transitionDesiredState(
                        cell.organizationRef(), cell.personRef(), cell.version(), SUSPENDABLE,
                        RuntimeCellState.SUSPENDED, reasonAuditRef("suspend", reason, context.auditRef()),
                        clock.instant());
            }
            commands.complete(receipt, cell.version(), clock.instant());
            return projection(cell);
        } catch (RuntimeException failure) {
            commands.fail(receipt, "runtime-suspend-failed", clock.instant());
            throw failure;
        }
    }

    public AgentRuntimeProjectionResponse reconcile(
            AdminContext context,
            String personRef,
            String idempotencyKey) {
        RuntimeCell cell = requireCell(context, personRef);
        RuntimeCommandReceipt receipt = claim(cell, idempotencyKey, "RECONCILE", context.auditRef());
        if (receipt.status() == RuntimeCommandReceipt.Status.COMPLETED) {
            return projection(requireCell(context, personRef));
        }
        try {
            cell = control.reconcileEntitlement(cell, context.auditRef());
            if (cell.entitlementState() == RuntimeEntitlementState.ENTITLED) {
                RuntimeWorkloadBinding reconciled = workloadIdentities.reconcileBinding(
                        new RuntimeWorkloadIdentityAdmin.ReconcileBindingCommand(
                                cell.organizationRef(), cell.personRef(), cell.cellRef(),
                                cell.workloadBinding(), context.auditRef()));
                if (!reconciled.equals(cell.workloadBinding())) {
                    throw new RuntimeCommandConflictException(
                            "the immutable workload identity changed during reconciliation");
                }
                if (requiresProfile(cell.desiredState())) {
                    profileIssuance.issue(cell, "reconcile:" + idempotencyKey, receipt.createdAt());
                    cell = requireCell(context, personRef);
                }
            }
            commands.complete(receipt, cell.version(), clock.instant());
            return projection(cell);
        } catch (RuntimeException failure) {
            commands.fail(receipt, "runtime-reconcile-failed", clock.instant());
            throw failure;
        }
    }

    public AgentRuntimeProjectionResponse revoke(
            AdminContext context,
            String personRef,
            String idempotencyKey,
            String reason,
            String expectedEntitlementRevision) {
        RuntimeCell cell = control.revoke(new AgentRuntimeControlService.RevokeRuntimeCommand(
                context.organizationRef(),
                personRef,
                expectedEntitlementRevision,
                "admin-revoked",
                reasonRefHash(context.organizationRef(), personRef, reason),
                context.actorRef(),
                idempotencyKey,
                reasonAuditRef("revoke", reason, context.auditRef())));
        return projection(cell);
    }

    public AgentRuntimeProjectionResponse deleteRuntimeState(
            AdminContext context,
            String personRef,
            String idempotencyKey,
            String reason) {
        RuntimeCell cell = requireCell(context, personRef);
        RuntimeCommandReceipt receipt = claim(
                cell, idempotencyKey, semanticCommand("DELETE_STATE", reason), context.auditRef());
        if (receipt.status() == RuntimeCommandReceipt.Status.COMPLETED) {
            return projection(requireCell(context, personRef));
        }
        try {
            profiles.revokeCurrent(cell.cellRef(), "runtime-state-deletion", clock.instant());
            if (cell.desiredState() != RuntimeCellState.DELETED) {
                cell = cells.transitionDesiredState(
                        cell.organizationRef(), cell.personRef(), cell.version(), DELETABLE,
                        RuntimeCellState.DELETING,
                        reasonAuditRef("delete-state", reason, context.auditRef()),
                        clock.instant());
                workloadIdentities.deleteBinding(new RuntimeWorkloadIdentityAdmin.DeleteBindingCommand(
                        cell.organizationRef(), cell.personRef(), cell.cellRef(),
                        cell.workloadBinding(), context.auditRef()));
                runtimeState.deleteRuntimeState(new RuntimeStateStoreAdmin.DeleteRuntimeStateCommand(
                        cell.organizationRef(), cell.personRef(), cell.cellRef(), cell.runtimeStateStoreRef(),
                        idempotencyKey, context.auditRef()));
                cell = cells.transitionDesiredState(
                        cell.organizationRef(), cell.personRef(), cell.version(),
                        Set.of(RuntimeCellState.DELETING), RuntimeCellState.DELETED,
                        context.auditRef(), clock.instant());
            }
            commands.complete(receipt, cell.version(), clock.instant());
            return projection(cell);
        } catch (RuntimeException failure) {
            commands.fail(receipt, "runtime-state-deletion-failed", clock.instant());
            throw failure;
        }
    }

    private RuntimeCommandReceipt claim(
            RuntimeCell cell,
            String idempotencyKey,
            String command,
            String auditRef) {
        return commands.claim(
                cell.organizationRef(), cell.personRef(), idempotencyKey,
                command, cell.cellRef(), auditRef, clock.instant());
    }

    private RuntimeCell requireCell(AdminContext context, String personRef) {
        requireText(personRef, "personRef");
        return cells.findByPerson(context.organizationRef(), personRef)
                .orElseThrow(() -> new RuntimePersonNotFoundException(
                        "The requested Agent Runtime does not exist"));
    }

    private static void requireEntitled(RuntimeCell cell) {
        if (cell.entitlementState() != RuntimeEntitlementState.ENTITLED) {
            throw new RuntimeCommandConflictException("The Agent Runtime entitlement is not active");
        }
    }

    private static boolean requiresProfile(RuntimeCellState state) {
        return switch (state) {
            case STARTING, MATERIALIZING, READY, BUSY, SYNCING, DEGRADED -> true;
            default -> false;
        };
    }

    private static String semanticCommand(String operation, String reason) {
        requireReason(reason);
        return operation + ":" + RuntimeWorkloadOwnership.fingerprint(reason).substring(7, 39);
    }

    private static String reasonAuditRef(String operation, String reason, String requestAuditRef) {
        requireReason(reason);
        return "audit:arc-" + operation + ":" + RuntimeWorkloadOwnership.fingerprint(
                requestAuditRef + "\u0000" + reason).substring(7);
    }

    private static String reasonRefHash(String organizationRef, String personRef, String reason) {
        requireReason(reason);
        return RuntimeWorkloadOwnership.fingerprint(
                "weave.agent-runtime.admin-reason/v1\u0000" + organizationRef + "\u0000"
                        + personRef + "\u0000" + reason);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A reason of at most 500 characters is required");
        }
    }

    private static AgentRuntimeProjectionResponse projection(RuntimeCell cell) {
        return new AgentRuntimeProjectionResponse(
                cell.personRef(),
                cell.cellRef(),
                "weaver-openclaw",
                cell.entitlementState().name().toLowerCase(java.util.Locale.ROOT),
                cell.entitlementRevision(),
                cell.desiredState().wireValue(),
                cell.observedState().wireValue(),
                cell.runtimeProfileHash(),
                cell.workspaceRevision(),
                null,
                null,
                0,
                capabilityState(cell),
                cell.auditRef());
    }

    private static String capabilityState(RuntimeCell cell) {
        if (cell.entitlementState() != RuntimeEntitlementState.ENTITLED) {
            return "not_entitled";
        }
        if (cell.desiredState() == RuntimeCellState.SUSPENDED) {
            return "disabled_by_policy";
        }
        if (cell.desiredState() == RuntimeCellState.DELETING
                || cell.desiredState() == RuntimeCellState.DELETED) {
            return "not_configured";
        }
        if (cell.observedState() == RuntimeCellState.DEGRADED) {
            return "degraded";
        }
        return cell.runtimeProfileHash() == null ? "not_configured" : "available";
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public record AdminContext(String organizationRef, String actorRef, String auditRef) {
        public AdminContext {
            requireText(organizationRef, "organizationRef");
            requireText(actorRef, "actorRef");
            requireText(auditRef, "auditRef");
        }
    }
}
