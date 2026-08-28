package com.massimotter.weave.backend.runner.application;

import java.time.Duration;

/**
 * Process-local wake-up hint for bounded task claims. Durable task authority remains exclusively in
 * {@link RunnerTaskStore}; callers must always re-check PostgreSQL after this signal changes or
 * times out.
 */
public interface RunnerTaskAvailabilitySignal {

    long revision();

    void awaitChange(long observedRevision, Duration maximumWait) throws InterruptedException;

    void signal();
}
