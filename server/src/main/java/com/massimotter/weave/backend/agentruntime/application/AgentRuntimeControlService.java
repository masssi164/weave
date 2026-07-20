package com.massimotter.weave.backend.agentruntime.application;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

public final class AgentRuntimeControlService {
    private static final String PROVISION = "PROVISION";
    private static final String REVOKE = "REVOKE";

    private final RuntimeCellRepository cells;
    private final RuntimeCommandRepository commands;
    private final RuntimeWorkloadIdentityAdmin workloadIdentityAdmin;
    private final Clock clock;

    public AgentRuntimeControlService(
            RuntimeCellRepository cells,
            RuntimeCommandRepository commands,
            RuntimeWorkloadIdentityAdmin workloadIdentityAdmin,
            Clock clock) {
        this.cells = cells;
        this.commands = commands;
        this.workloadIdentityAdmin = workloadIdentityAdmin;
        this.clock = clock;
    }

    public RuntimeCell provision(ProvisionRuntimeCommand command) {
        Instant now = clock.instant();
        String cellKey = stableCellKey(command.organizationRef(), command.memberBinding());
        String cellRef = "cell:" + cellKey;
        RuntimeCommandReceipt receipt = commands.claim(
                command.organizationRef(), command.personRef(), command.idempotencyKey(), PROVISION,
                cellRef, command.auditRef(), now);
        RuntimeCell existing = cells.findByPerson(command.organizationRef(), command.personRef()).orElse(null);
        if (existing != null) {
            requireSameBinding(existing, receipt, command);
            commands.complete(receipt, existing.version(), now);
            return existing;
        }

        try {
            RuntimeWorkloadBinding workload = workloadIdentityAdmin.ensureBinding(
                    new RuntimeWorkloadIdentityAdmin.EnsureBindingCommand(
                            command.organizationRef(), command.personRef(), cellRef, "weaver-cell-" + cellKey,
                            command.authenticationMethod(), command.auditRef()));
            RuntimeCell proposed = RuntimeCell.provisioning(
                    command.organizationRef(), command.personRef(), command.memberBinding(), cellRef, workload,
                    command.entitlementRevision(), command.workspaceRevision(), command.workspaceManifestRef(),
                    command.runtimeStateStoreRef(), command.auditRef(), now);
            RuntimeCell persisted;
            try {
                persisted = cells.insert(proposed);
            } catch (RuntimeException concurrentInsert) {
                persisted = cells.findByPerson(command.organizationRef(), command.personRef())
                        .orElseThrow(() -> concurrentInsert);
                requireSameBinding(persisted, receipt, command);
            }
            commands.complete(receipt, persisted.version(), clock.instant());
            return persisted;
        } catch (RuntimeException failure) {
            commands.fail(receipt, "runtime-provisioning-failed", clock.instant());
            throw failure;
        }
    }

    public RuntimeCell revoke(RevokeRuntimeCommand command) {
        RuntimeCell existing = cells.findByPerson(command.organizationRef(), command.personRef())
                .orElseThrow(() -> new IllegalStateException("runtime cell does not exist"));
        Instant now = clock.instant();
        RuntimeCommandReceipt receipt = commands.claim(
                command.organizationRef(), command.personRef(), command.idempotencyKey(), REVOKE,
                existing.cellRef(), command.auditRef(), now);
        try {
            RuntimeCell revoked = cells.revoke(
                    command.organizationRef(), command.personRef(), command.entitlementRevision(),
                    command.auditRef(), now);
            workloadIdentityAdmin.disableBinding(new RuntimeWorkloadIdentityAdmin.DisableBindingCommand(
                    revoked.organizationRef(), revoked.personRef(), revoked.cellRef(),
                    revoked.workloadBinding().clientId(), command.auditRef()));
            commands.complete(receipt, revoked.version(), clock.instant());
            return revoked;
        } catch (RuntimeException failure) {
            commands.fail(receipt, "runtime-revocation-incomplete", clock.instant());
            throw failure;
        }
    }

    private static void requireSameBinding(
            RuntimeCell cell, RuntimeCommandReceipt receipt, ProvisionRuntimeCommand command) {
        if (!cell.cellRef().equals(receipt.cellRef())
                || !cell.organizationRef().equals(command.organizationRef())
                || !cell.personRef().equals(command.personRef())
                || !cell.memberBinding().equals(command.memberBinding())) {
            throw new RuntimeCommandConflictException("person is bound to a different runtime cell");
        }
    }

    private static String stableCellKey(String organizationRef, RuntimeMemberBinding memberBinding) {
        String input = organizationRef + "\u0000" + memberBinding.issuer() + "\u0000" + memberBinding.subject();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK SHA-256 support is unavailable", impossible);
        }
    }

    public record ProvisionRuntimeCommand(
            String organizationRef,
            String personRef,
            RuntimeMemberBinding memberBinding,
            String entitlementRevision,
            String workspaceRevision,
            String workspaceManifestRef,
            String runtimeStateStoreRef,
            RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod,
            String idempotencyKey,
        String auditRef) {

        public ProvisionRuntimeCommand {
            requireText(organizationRef, "organizationRef");
            requireText(personRef, "personRef");
            if (memberBinding == null || authenticationMethod == null) {
                throw new IllegalArgumentException("member binding and workload authentication method are required");
            }
            requireText(entitlementRevision, "entitlementRevision");
            requireText(workspaceRevision, "workspaceRevision");
            requireText(workspaceManifestRef, "workspaceManifestRef");
            requireText(runtimeStateStoreRef, "runtimeStateStoreRef");
            if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
                throw new IllegalArgumentException("idempotency key length must be between 16 and 128");
            }
            requireText(auditRef, "auditRef");
        }
    }

    public record RevokeRuntimeCommand(
            String organizationRef,
            String personRef,
            String entitlementRevision,
            String idempotencyKey,
            String auditRef) {
        public RevokeRuntimeCommand {
            requireText(organizationRef, "organizationRef");
            requireText(personRef, "personRef");
            requireText(entitlementRevision, "entitlementRevision");
            if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
                throw new IllegalArgumentException("idempotency key length must be between 16 and 128");
            }
            requireText(auditRef, "auditRef");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
