package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Objects.requireNonNull;

/** Versioned command-receipt adapter with a durable idempotency boundary. */
@Repository
public class JpaRuntimeCommandRepository implements RuntimeCommandRepository {

    private final RuntimeCommandJpaRepository commands;
    private final TransactionTemplate transactions;

    public JpaRuntimeCommandRepository(
            RuntimeCommandJpaRepository commands,
            PlatformTransactionManager transactionManager) {
        this.commands = requireNonNull(commands, "commands");
        this.transactions = new TransactionTemplate(
                requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public RuntimeCommandReceipt claim(
            String organizationRef,
            String personRef,
            String idempotencyKey,
            String command,
            String proposedCellRef,
            String auditRef,
            Instant now) {
        RuntimeCommandId id = new RuntimeCommandId(
                organizationRef,
                personRef,
                idempotencyKey);
        RuntimeCommandJpaEntity existing = commands.findById(id).orElse(null);
        if (existing != null) {
            return requireSameCommand(existing, command, proposedCellRef);
        }
        try {
            return transactions.execute(status -> commands.saveAndFlush(
                            RuntimeCommandJpaEntity.started(
                                    id,
                                    command,
                                    proposedCellRef,
                                    auditRef,
                                    now))
                    .toDomain());
        } catch (DataIntegrityViolationException | PersistenceException duplicateOrFailure) {
            return transactions.execute(status -> requireSameCommand(
                    commands.findById(id).orElseThrow(() -> duplicateOrFailure),
                    command,
                    proposedCellRef));
        }
    }

    @Override
    public RuntimeCommandReceipt complete(
            RuntimeCommandReceipt receipt,
            long runtimeVersion,
            Instant now) {
        return transactions.execute(status -> {
            RuntimeCommandJpaEntity command = locked(receipt);
            if (!command.complete(receipt.command(), runtimeVersion, now)) {
                throw new RuntimeCommandConflictException(
                        "command completion conflicts with the stored receipt");
            }
            return commands.saveAndFlush(command).toDomain();
        });
    }

    @Override
    public RuntimeCommandReceipt fail(
            RuntimeCommandReceipt receipt,
            String failureCode,
            Instant now) {
        return transactions.execute(status -> {
            RuntimeCommandJpaEntity command = locked(receipt);
            command.fail(failureCode, now);
            return commands.saveAndFlush(command).toDomain();
        });
    }

    private RuntimeCommandJpaEntity locked(RuntimeCommandReceipt receipt) {
        return commands.lockById(new RuntimeCommandId(
                        receipt.organizationRef(),
                        receipt.personRef(),
                        receipt.idempotencyKey()))
                .orElseThrow(() -> new IllegalStateException(
                        "runtime command receipt is missing"));
    }

    private RuntimeCommandReceipt requireSameCommand(
            RuntimeCommandJpaEntity stored,
            String command,
            String proposedCellRef) {
        if (!stored.matches(command, proposedCellRef)) {
            throw new RuntimeCommandConflictException(
                    "idempotency key is already bound to another command");
        }
        return stored.toDomain();
    }
}
