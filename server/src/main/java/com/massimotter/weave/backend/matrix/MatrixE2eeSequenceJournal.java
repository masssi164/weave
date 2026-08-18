package com.massimotter.weave.backend.matrix;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.ObjLongConsumer;

/**
 * Publishes Matrix E2EE state and the cursor that makes that state visible as
 * one atomic boundary.
 */
final class MatrixE2eeSequenceJournal {

    private long highWater;

    synchronized long publish(LongConsumer publication) {
        long next = highWater + 1;
        publication.accept(next);
        highWater = next;
        return highWater;
    }

    synchronized <T> boolean publishAllIf(
            BooleanSupplier guard,
            List<T> values,
            ObjLongConsumer<T> publication) {
        if (!guard.getAsBoolean()) {
            return false;
        }
        long next = highWater;
        for (T value : values) {
            next += 1;
            publication.accept(value, next);
        }
        highWater = next;
        return true;
    }

    synchronized <T> Snapshot<T> snapshot(LongFunction<T> reader) {
        long snapshotHighWater = highWater;
        return new Snapshot<>(snapshotHighWater, reader.apply(snapshotHighWater));
    }

    synchronized void restore(long persistedHighWater, Runnable publication) {
        if (persistedHighWater < 0) {
            throw new IllegalArgumentException("Matrix E2EE sequence must not be negative.");
        }
        publication.run();
        highWater = Math.max(highWater, persistedHighWater);
    }

    synchronized long current() {
        return highWater;
    }

    record Snapshot<T>(long highWater, T value) {
    }
}
