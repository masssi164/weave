package com.massimotter.weave.backend.runner.application;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Low-latency wake-up hint for one Engine process. Cross-process notifications may replace this
 * adapter later; bounded polling against PostgreSQL remains the source of truth.
 */
public final class InMemoryRunnerTaskAvailabilitySignal implements RunnerTaskAvailabilitySignal {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private long revision;

    @Override
    public long revision() {
        lock.lock();
        try {
            return revision;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void awaitChange(long observedRevision, Duration maximumWait) throws InterruptedException {
        Duration wait = Objects.requireNonNull(maximumWait, "maximumWait");
        if (wait.isNegative()) {
            throw new IllegalArgumentException("maximumWait must not be negative");
        }
        long remainingNanos = wait.toNanos();
        if (remainingNanos == 0) {
            return;
        }

        lock.lockInterruptibly();
        try {
            while (revision == observedRevision && remainingNanos > 0) {
                remainingNanos = changed.awaitNanos(remainingNanos);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void signal() {
        lock.lock();
        try {
            revision++;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
