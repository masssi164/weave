package com.massimotter.weave.backend.readiness.domain;

/** Support-safe observation of a local capability; provider credentials and raw failures are excluded. */
public record LocalDependencyObservation(
        String id, String label, String status, String readiness, String summary, String remediation) {}
