package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class MatrixE2eeSequenceJournalTest {

    @Test
    @Timeout(5)
    void snapshotCannotAcknowledgePublicationUntilItsStateIsVisible() throws Exception {
        MatrixE2eeSequenceJournal journal = new MatrixE2eeSequenceJournal();
        CountDownLatch publicationStarted = new CountDownLatch(1);
        CountDownLatch releasePublication = new CountDownLatch(1);
        AtomicLong visibleSequence = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> publisher = executor.submit(() -> journal.publish(sequence -> {
                publicationStarted.countDown();
                await(releasePublication);
                visibleSequence.set(sequence);
            }));
            assertThat(publicationStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<MatrixE2eeSequenceJournal.Snapshot<Long>> snapshot =
                    executor.submit(() -> journal.snapshot(ignored -> visibleSequence.get()));

            assertThatThrownBy(() -> snapshot.get(150, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releasePublication.countDown();
            publisher.get(1, TimeUnit.SECONDS);
            MatrixE2eeSequenceJournal.Snapshot<Long> completed =
                    snapshot.get(1, TimeUnit.SECONDS);

            assertThat(completed.highWater()).isEqualTo(1);
            assertThat(completed.value()).isEqualTo(1);
        } finally {
            releasePublication.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(1).toMillis(), TimeUnit.MILLISECONDS))
                    .isTrue();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("The Matrix E2EE publication was not released.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("The Matrix E2EE publication was interrupted.", exception);
        }
    }
}
