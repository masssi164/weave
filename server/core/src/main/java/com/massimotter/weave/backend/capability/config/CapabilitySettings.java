package com.massimotter.weave.backend.capability.config;

import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;

/** Typed capability policy input; each owner supplies its own defaults. */
public record CapabilitySettings(boolean enabled, String dependencyUrl, WorkspaceCapabilityReadiness readiness) {}
